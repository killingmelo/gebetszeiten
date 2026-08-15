package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Amtliche Zeiten mit Quellen-Kette: Diyanet-Jahresseite direkt (ganzes
 * Jahr), bei Fehler/leer der Community-Proxy (31 Tage). Die Standort-ID
 * kommt aus dem DE-Bundle (id-genau), sonst aus dem Cache (letzter
 * erfolgreicher Abruf am selben Ort), sonst per Proxy-Namenssuche.
 * Wirft nie — leeres Ergebnis heißt: Aufrufer bleibt bei Bundle/Berechnung.
 */
class CompositeDiyanetFetcher(
    private val resolveId: suspend (AppSettings) -> Int?,
    private val direct: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val proxy: suspend (Int) -> Map<LocalDate, SixTimes>,
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
            return FetchResult(emptyMap(), null)
        }
        val schedule = attempt("Direktabruf", id, direct)
            .ifEmpty { attempt("Proxy-Abruf", id, proxy) }
        return FetchResult(schedule, id.takeIf { schedule.isNotEmpty() })
    }

    private suspend fun attempt(
        label: String,
        id: Int,
        source: suspend (Int) -> Map<LocalDate, SixTimes>,
    ): Map<LocalDate, SixTimes> = try {
        source(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("$label fehlgeschlagen (id=$id)", e)
        emptyMap()
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
                            withContext(Dispatchers.IO) { proxyFetcher.resolveLocationId(settings.city) }
                        },
                    )
                },
                direct = DiyanetDirectFetcher()::fetchYear,
                proxy = proxyFetcher::fetchById,
            )
        }
    }
}

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
