package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.SourceResult
import de.gebetszeiten.core.prayertimes.officialtimes.resolveQuorum
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.fetchErrorSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import javax.net.ssl.SSLException

/**
 * Amtliche Zeiten aus DREI Quellen, die IMMER UND NEBENLAEUFIG laufen:
 * Diyanet-Jahresseite direkt (~400 Tage), der Community-Proxy (31 Tage) und
 * ezanvakti (~31 Tage). `resolveQuorum` entscheidet daraus, was gilt. Die
 * Standort-ID kommt aus dem DE-Bundle (id-genau), sonst aus dem Index, sonst
 * aus dem Cache, sonst per Proxy-Namenssuche. Wirft nie — leeres Ergebnis
 * heißt: Aufrufer bleibt bei Bundle/Berechnung.
 *
 * **Warum immer alle drei, und nicht die zweite erst bei Fehlschlag der
 * ersten?** Eine Kette ist ein Fallback, und ein Fallback verifiziert
 * nichts: er springt ein, wenn die erste Quelle SCHWEIGT, nicht wenn sie
 * LUEGT. Genau das war der Fehler, den der Nutzer mit „ein anderer
 * zusätzlicher 1:1-Scrape, dessen Zeiten gegengeprüft werden" benannt hat.
 * Ein Gegencheck braucht die Gegenstimme auch dann, wenn die erste Quelle
 * antwortet.
 *
 * **Der Preis, ausdrücklich gewollt:** drei Netzaufrufe je Auffrischung
 * statt einem. Der Jahresabruf ist ~378 KB, die beiden JSON-Quellen je
 * ~10 KB. Bei einem Abruf je Ort und Jahr ist das belanglos; bei
 * wiederholten Fehlversuchen vervielfacht es sich entsprechend. Wer das
 * später „optimiert", indem er die Kontrollquellen nur noch bei Fehlschlag
 * abruft, entfernt den Zweck dieser Klasse.
 *
 * **Zeitbudget:** nebenläufig ist die Wanduhr `max(direkt, proxy,
 * ezanvakti)` statt der Summe. `refreshOfficial` gibt weiterhin 25 s — drei
 * Quellen nacheinander hätten das gesprengt. Nebeneinander sind die 25 s
 * aber nur tragfähiger, nicht tragfähig: der Worst Case ist `max(30 s, 20 s,
 * 20 s)`, weil `DiyanetDirectFetcher` den Lesetimeout auf 20 s anhebt und
 * die 10 s `connectTimeout` aus `httpGet` davorliegen. 30 s über 25 s
 * heißt: der Abbruch kommt vom Budget, nicht vom Socket — und ein
 * blockierender Socket-Read reagiert nicht auf Cancellation. Keine
 * Regression (vorher war die Kette bei bis zu ~50 s), aber auch keine
 * Zusicherung, die man behaupten dürfte.
 *
 * [now] wird hineingereicht statt aus der Systemuhr geholt: der Wert landet
 * als `checkedEpochMs` in der `Verification` und muss testbar bleiben.
 */
class CompositeDiyanetFetcher(
    private val resolveId: suspend (AppSettings) -> Int?,
    private val direct: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val proxy: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val ezanvakti: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String, Exception) -> Unit = { msg, e ->
        android.util.Log.w("DiyanetFetch", msg, e)
    },
) : OfficialTimesFetcher {

    override suspend fun fetch(settings: AppSettings): FetchResult {
        val id = try {
            resolveId(settings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Standort-Aufloesung fehlgeschlagen", e)
            null
        }
        if (id == null) {
            // Vorher ein stilles `?: return` — genau deshalb war der
            // Serdivan-Fall unsichtbar: kein Log, keine UI-Meldung, nur
            // klammheimlich die eigene Berechnung.
            log(
                "Kein Diyanet-Standort fuer '${settings.city}' aufloesbar",
                IllegalStateException("keine ID"),
            )
            // Ohne Standort gibt es nichts abzurufen und damit auch nichts
            // zu pruefen: `verification` und `errorSummary` bleiben null.
            return FetchResult(emptyMap(), null)
        }
        val candidates = coroutineScope {
            listOf(
                async { attempt(SourceId.DIRECT, id, direct) },
                async { attempt(SourceId.PROXY_ABDUS, id, proxy) },
                async { attempt(SourceId.EZANVAKTI, id, ezanvakti) },
            ).awaitAll()
        }
        val outcome = resolveQuorum(candidates, id, now())
        // `verification` und `errorSummary` werden hier GEFUELLT, aber noch
        // von niemandem GELESEN: `refreshOfficial` speichert sie erst in
        // Task 12 im Cache-Kopf und zeigt sie in der Statuszeile. Das ist
        // keine vergessene Verdrahtung.
        return FetchResult(
            schedule = outcome.schedule,
            // Kommt aus dem Quorum, nicht aus `id`: bei
            // `VerificationNote.NONE` gibt es keine Zeiten, zu denen eine
            // Standort-ID gehoeren koennte.
            locationId = outcome.locationId,
            verification = outcome.verification,
            errorSummary = fetchErrorSummary(candidates),
        )
    }

    /**
     * Ein Quellenabruf, dessen Ausnahme INNEN bleibt.
     *
     * Das ist keine Stilfrage: `coroutineScope` bricht bei einer Ausnahme
     * aus einem `async` ALLE Geschwister mit ab. Traete die Ausnahme
     * heraus, risse eine kaputte Kontrollquelle den erfolgreichen
     * Jahresabruf mit ins Nichts — der Gegencheck wuerde die App also
     * schlechter machen als vorher.
     *
     * [CancellationException] geht weiter durch: ein echter Abbruch (der
     * 25-s-Timeout in `refreshOfficial`) soll alle drei beenden.
     */
    private suspend fun attempt(
        source: SourceId,
        id: Int,
        fetch: suspend (Int) -> Map<LocalDate, SixTimes>,
    ): SourceResult = try {
        SourceResult(source, fetch(id))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("${logLabel(source)} fehlgeschlagen (id=$id)", e)
        SourceResult(source, emptyMap(), fetchErrorText(e))
    }

    /** Nur fuers Log. Die Namen, die der NUTZER sieht, stehen in
     *  `VerificationText.sourceLabel` — getrennt, weil ein Logtag sich
     *  aendern darf, ohne dass eine Oberflaechenzeile mitwandert. */
    private fun logLabel(source: SourceId): String = when (source) {
        SourceId.DIRECT -> "Direktabruf"
        SourceId.PROXY_ABDUS -> "Proxy-Abruf"
        SourceId.EZANVAKTI -> "ezanvakti-Abruf"
    }

    companion object {
        fun create(context: Context): CompositeDiyanetFetcher {
            val proxyFetcher = DiyanetProxyFetcher()
            return CompositeDiyanetFetcher(
                resolveId = { settings ->
                    resolveLocationIdChain(
                        bundledId = BundledOfficialSource
                            .nearestLocation(context, settings.latitude, settings.longitude)
                            ?.diyanetId,
                        indexPlace = DiyanetPlaceIndex
                            .nearest(context, settings.latitude, settings.longitude),
                        cachedId = OfficialTimesCache(context)
                            .cachedLocationId(settings.latitude, settings.longitude),
                        searchByName = {
                            // Ohne Ortsnamen gar nicht erst suchen: das waere
                            // ein `GET /search?q=` — ein Netzaufruf, der
                            // nichts finden kann. Der Name ist leer, wenn ein
                            // Favorit als Ziel gewaehlt wurde, dessen Name
                            // nicht auffindbar ist (`targetSettings`): ein
                            // FALSCHER Name waere dort schlimmer als keiner.
                            if (settings.city.isBlank()) {
                                null
                            } else {
                                withContext(Dispatchers.IO) { proxyFetcher.resolveLocationId(settings.city) }
                            }
                        },
                    )
                },
                direct = DiyanetDirectFetcher()::fetchYear,
                proxy = proxyFetcher::fetchById,
                ezanvakti = EzanVaktiFetcher()::fetchById,
            )
        }
    }
}

/**
 * Aus einer Ausnahme ein kurzer Satz, den ein Mensch lesen kann — rein und
 * testbar, ohne Netz und ohne Android.
 *
 * Der Text landet ueber `fetchErrorSummary` in der Statuszeile
 * („Direktabruf: HTTP 503 · Proxy: Zeitüberschreitung"), deshalb: keine
 * Stacktraces, keine Klassennamen mit Paket, und nichts, was die Zeile
 * sprengt.
 *
 * Die Sonderfaelle haben einen Grund: `httpGet` wirft bereits
 * `error("HTTP $code")`, also eine `IllegalStateException` mit brauchbarem
 * `message`. Die haeufigen ECHTEN Netzfehler dagegen tragen Meldungen, die
 * niemandem helfen:
 * - [SocketTimeoutException]: „Read timed out" oder gar `null`;
 * - [UnknownHostException]: nur der Hostname;
 * - [SocketException] — davon erbt `ConnectException`, der haeufigste Fall
 *   ueberhaupt: „failed to connect to ...gov.tr/93.184.x.x (port 443) from
 *   /10.0.2.15 (port 45678) after 10000ms: isConnected failed: ECONNREFUSED
 *   (Connection refused)". IP, Port und `ECONNREFUSED` in der Statuszeile;
 * - [SSLException]: deren `message` verkettet die Ursache MIT
 *   vollqualifiziertem Klassennamen („...java.security.cert.
 *   CertPathValidatorException: Trust anchor ... not found"), was das
 *   Versprechen „keine Klassennamen mit Paket" bereits bricht.
 *
 * Alle vier saehen in der Statuszeile aus wie ein Fehler der QUELLE, obwohl
 * es das Geraet oder die Leitung ist.
 *
 * **Zur Reihenfolge im `when`:** [SocketTimeoutException] steht vor
 * [SocketException]. Das ist heute wirkungslos — sie erbt von
 * `InterruptedIOException`, nicht von [SocketException] —, kostet aber nichts
 * und macht den Zweig unabhaengig davon, wo die Hierarchie ihn einhaengt.
 *
 * **Kein Test haelt diese Reihenfolge fest**, und das laesst sich mit den
 * echten Typen auch nicht: ein Vertauschen aendert bei keiner existierenden
 * Ausnahme etwas. Wer hier umsortiert, bekommt also kein rotes Signal —
 * dieser Absatz ist das einzige Signal.
 */
internal fun fetchErrorText(e: Exception): String = when (e) {
    is SocketTimeoutException -> "Zeitüberschreitung"
    is UnknownHostException -> "Kein Netz"
    is SSLException -> "Verschlüsselung fehlgeschlagen"
    is SocketException -> "Keine Verbindung"
    // `isNotBlank`, nicht `isNotEmpty`: ein Leerzeichen als Fehlergrund
    // waere eine leere Behauptung in der Statuszeile.
    else -> shortenForStatusLine(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
}

/** Wie viele Zeichen eine FREMDE Meldung in der Statuszeile hoechstens
 *  belegen darf. Die eigenen Texte oben sind kurz; gekappt wird nur, was von
 *  aussen kommt. */
private const val MAX_ERROR_CHARS = 80

/** Kappt auf [MAX_ERROR_CHARS] EINSCHLIESSLICH des Auslassungszeichens: die
 *  Zeile bleibt damit hoechstens so breit wie versprochen, statt um ein
 *  Zeichen darueber. */
private fun shortenForStatusLine(text: String): String =
    if (text.length <= MAX_ERROR_CHARS) text else text.take(MAX_ERROR_CHARS - 1) + "…"

/**
 * ID-Aufloesung als reine Funktion: Bundle -> Index -> Cache -> Namenssuche.
 *
 * Die Namenssuche war bis 2026-08 der Primaerweg. Sie scheiterte an jedem Ort,
 * den Diyanet nicht selbst als Standort fuehrt — etwa Serdivan, das vom
 * Eintrag SAKARYA (2,1 km entfernt) abgedeckt wird. Der Koordinatenindex steht
 * deshalb VOR ihr; sie bleibt nur noch fuer Luecken im Index.
 *
 * [bundledId] und [indexPlace] werden eifrig ausgewertet — beide sind rein
 * lokal und in Millisekunden fertig. [searchByName] bleibt ein Lambda: der
 * einzige Netzaufruf der Kette darf nur laufen, wenn er gebraucht wird.
 */
internal suspend fun resolveLocationIdChain(
    bundledId: Int?,
    indexPlace: de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace?,
    cachedId: Int?,
    searchByName: suspend () -> Int?,
): Int? = bundledId ?: indexPlace?.diyanetId ?: cachedId ?: searchByName()
