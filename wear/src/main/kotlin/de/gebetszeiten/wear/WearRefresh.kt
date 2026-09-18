package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Letzte Rettungsleine des [refreshScope]. Sie sollte nie greifen — jeder
 * Pfad in [doRefresh] und [launchWearRefresh] faengt seine Ausnahmen selbst
 * ab. Aber ein unbehandelter Fehler in einer Coroutine geht auf Android an
 * den Default-Handler, und der beendet den PROZESS: eine `IOException` aus
 * dem DataStore duerfte die Uhr nicht abstuerzen lassen, nur weil niemand
 * hinsieht. `SupervisorJob` leistet das ausdruecklich NICHT — es haelt nur
 * Geschwister-Coroutines am Leben, faengt aber keine Ausnahme ab.
 */
private val refreshExceptionHandler = CoroutineExceptionHandler { _, e ->
    android.util.Log.e("WearRefresh", "unbehandelter Fehler im Auffrisch-Scope", e)
}

/**
 * Eigener, langlebiger Scope fuer den Netzabruf — unabhaengig vom Scope des
 * jeweiligen Aufrufers. `MainActivity` haelt selbst einen langlebigen
 * `MainScope`, aber `PrayerTileService`/`PrayerComplicationService` sind
 * GEBUNDENE Dienste: das System loest sie Sekunden nach der Antwort wieder,
 * ihr eigener Scope waere fuer einen bis zu 25 s laufenden Netzabruf zu
 * kurzlebig (Fix-Runde 3, Important: ohne diesen datei-eigenen Scope stirbt
 * die Neuzeichnung mit dem Service, bevor `await()` je zurueckkehrt — siehe
 * [launchWearRefresh]). `SupervisorJob`, damit ein Fehlschlag EINES Abrufs
 * nicht den Scope fuer alle folgenden Aufrufe mit umbringt;
 * [refreshExceptionHandler] als zusaetzliche Rettungsleine fuer den Fall,
 * dass doch einmal etwas an allen `catch`-Zweigen vorbeikommt.
 */
private val refreshScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO + refreshExceptionHandler)

/**
 * Buendelt gleichzeitige Aufrufe von [refreshWearOfficial] — die Uhr hat
 * DREI unabhaengige Ausloeser (Activity, Kachel, Komplikation), anders als
 * das Telefon mit seinem einzelnen Alarm-Receiver. Treffen zwei davon
 * gleichzeitig ein (z. B. Handgelenk-Heben zeigt Kachel UND Komplikation
 * neu, genau beim faelligen Gebetsuebergang), bekommt der zweite Aufrufer
 * dasselbe Ergebnis, statt einen eigenen Lauf zu starten — aus potenziell
 * drei gleichzeitigen Ablaeufen (neun HTTP-Anfragen ueber die
 * Bluetooth-Strecke der Uhr) wird hoechstens einer (drei Anfragen).
 *
 * Reine Buendelungs-Mechanik in [SingleFlight] (herausgezogen und dort
 * eigenstaendig getestet, Fix-Runde 3) — hier nur die Verdrahtung mit
 * [refreshScope].
 *
 * Bewusst EIN gemeinsamer Slot fuer die ganze Funktion, nicht einer je Ort:
 * die Uhr hat ohnehin nur den einen aktiven Ort ([WearSettings.location]),
 * ein zweiter Aufruf waehrend eines laufenden waere fuer denselben Ort
 * gedacht. Trifft ein `force`-Aufruf auf einen bereits laufenden
 * unforcierten, wird trotzdem nur der laufende abgewartet — `force` ist
 * (Stand dieser Aufgabe) an keinem Uhr-Knopf verdrahtet, dieser Fall also
 * praktisch nicht erreichbar; sollte er es werden, verdient er eine eigene
 * Entscheidung, kein stillschweigendes Downgrade hier.
 */
private val refreshSingleFlight = SingleFlight<Boolean>(refreshScope)

/**
 * Die Uhr ruft ihre amtlichen Zeiten selbst ab, statt nur auf den Sync vom
 * Handy zu warten (siehe [WearOfficialCache]). Muster:
 * `PrayerProvider.refreshOfficial` am Telefon — aber nur fuer den EINEN
 * gewaehlten Uhr-Ort ([WearSettings.location]), kein Favoritenreigen: die
 * Uhr fuehrt keine eigene Favoritenliste.
 *
 * Dieselbe Wiederholungs-Bremse wie am Telefon ([needsRefresh] ueber
 * [chooseTarget]) verhindert, dass Kachel und Komplikation bei jedem
 * Zeichnen erneut abrufen.
 *
 * **Wirft nie.** Jeder Fehlerpfad in [doRefresh] endet in `false` — auch
 * eine `IOException` aus dem DataStore oder aus dem Einlesen der
 * Ortstabellen. Das ist keine Kosmetik: alle drei Aufrufer starten diese
 * Funktion in einem `launch` ohne eigenen Aufrufer-`try`, und durch die
 * Buendelung ([refreshSingleFlight]) traefe EINE entkommene Ausnahme
 * gleich ALLE wartenden Aufrufer. Einzige Ausnahme ist
 * `CancellationException` — ein echter Abbruch ist kein Fehler und muss
 * weiterlaufen duerfen.
 *
 * **Muss NICHT-BLOCKIEREND aufgerufen werden.** `withTimeout(25_000)` unten
 * kann einen echten Netzabruf bis zu 25 s laufen lassen. `PrayerTileService`
 * und `PrayerComplicationService` werden vom System auf dem HAUPT-THREAD
 * aufgerufen (ANR-Schwelle 5 s) — sie rufen diese Funktion deshalb NIE
 * direkt auf, sondern ueber [launchWearRefresh] (fire-and-forget im
 * datei-eigenen [refreshScope], siehe dort — NICHT im eigenen Service-Scope,
 * der stirbt, bevor `await()` zurueckkehrt). `MainActivity` ruft
 * [refreshWearOfficial] direkt auf, aber ebenfalls nicht-blockierend
 * (`scope.launch { withContext(Dispatchers.IO) { refreshWearOfficial(...) }
 * }`, ausserhalb des synchronen `onStart`-Pfads) — ihr eigener `MainScope`
 * ist lang genug lebendig (Aktivitaets-Lebenszyklus), und ein Abbruch dort
 * bedeutet ohnehin nur "niemand schaut mehr hin", der Abruf selbst laeuft
 * unbeeinflusst im geteilten [refreshScope] weiter (siehe [SingleFlight]).
 *
 * Offline-Flavor: [WearFetchProvider.isOnline] ist `false` — sofortige
 * Rueckkehr, ohne dass hier auch nur eine DataStore geoeffnet wird.
 *
 * Laeuft dagegen AUCH, wenn der Notausgang ([WearSettings.calculationFillsGaps])
 * eingeschaltet ist — bis Task 16 (unter dem alten Namen `useCalculated`,
 * "immer rechnen") stand hier ein fruehes `return false`, weil ein Abruf
 * dessen Ergebnis nie angezeigt haette. Seit der Schalter nur noch LUECKEN
 * fuellt, will ein Nutzer, der ihn einschaltet, amtliche Zeiten WEITERHIN —
 * ein Abruf ist also GENAUSO sinnvoll wie ohne (dieselbe Begruendung wie
 * `canFetchOfficial()`/`PrayerProvider.refreshOfficial` am Telefon).
 *
 * [force] (der "Jetzt aktualisieren"-Knopf, falls die Uhr einen bekommt)
 * durchbricht die Bremse fuer den aktiven Ort — dieselbe Semantik wie am
 * Telefon.
 *
 * @return `true` nur, wenn tatsaechlich neue Zeiten abgelegt wurden (also
 *   ein Neuzeichnen lohnt) — `false` bei jedem fruehen Ausstieg, wenn kein
 *   Ort faellig war, oder wenn der Abruf fehlschlug/leer blieb/dem Timeout
 *   unterlag.
 */
suspend fun refreshWearOfficial(context: Context, force: Boolean = false): Boolean {
    // Reine Konstante des Flavors, kein DataStore, kein Asset — das einzige,
    // was hier VOR der Buendelungs-Huelle stehen darf, ohne einen eigenen
    // `try` zu brauchen.
    if (!WearFetchProvider.isOnline) return false

    return refreshSingleFlight.run { doRefresh(context, force) }
}

/**
 * Startet [refreshWearOfficial] fire-and-forget im datei-eigenen,
 * langlebigen [refreshScope] und ruft bei Erfolg [onUpdated] auf — fuer
 * `PrayerTileService`/`PrayerComplicationService`, die selbst KEINEN fuer
 * einen Netzabruf hinreichend langlebigen Scope halten (Begruendung an
 * [refreshScope]).
 *
 * Nicht `context.lifecycleScope` oder ein per-Service-Feld: genau DAS war
 * der Fix-Runde-3-Befund — ein `scope.launch {...}` im Service-eigenen Scope
 * wird abgebrochen, sobald das System den Dienst kurz nach der Antwort
 * wieder loest, und `onUpdated` (der `requestUpdate`-Aufruf) feuert dann nur
 * noch, wenn der Abruf zufaellig schneller war als der Dienst lebte — also
 * fast nie im Zielfall (langsames Netz), fast immer nur dann, wenn die
 * Bremse ohnehin `false` geliefert haette.
 *
 * [onUpdated] laeuft im `try` mit: es ist fremder Code (bei beiden Diensten
 * ein Aufruf ins Tiles-/Komplikations-Framework, der eine
 * `RuntimeException` werfen kann, wenn das Ziel gerade nicht erreichbar
 * ist). Ein Fehlschlag DORT darf die Uhr nicht abstuerzen lassen — die
 * Zeiten sind zu dem Zeitpunkt bereits abgelegt, nur das Neuzeichnen
 * unterbleibt.
 *
 * [onUpdated] wird auf [Dispatchers.IO] aufgerufen, nicht auf dem
 * Haupt-Thread. Beide heutigen Aufrufer sind damit einverstanden
 * (`TileService.getUpdater(...).requestUpdate` und
 * `ComplicationDataSourceUpdateRequester.requestUpdateAll` sind
 * Thread-unabhaengige Anfragen ans System, keine UI-Aufrufe).
 *
 * Ruft bei Erfolg AUCH [WearVibration.reschedule] auf, vor [onUpdated]
 * (Fix-Runde 1, Important 2): ein erfolgreicher eigener Abruf kann eine
 * naechste Zeit erst verfuegbar machen (Notausgang aus, Uhr vorher ohne
 * amtliche Zeiten -> Kette abbestellt) oder eine bestehende naechste Zeit
 * verschieben. Ohne diesen Aufruf blieb die Kette nach einem Abbestellen tot,
 * bis Neustart, Ortswechsel oder das Umschalten des Vibrations-Schalters
 * selbst — [WearSyncApplier.apply] deckte nur den SYNC-Pfad ab, nicht den
 * eigenen Abruf der Uhr.
 */
fun launchWearRefresh(context: Context, onUpdated: () -> Unit) {
    refreshScope.launch {
        try {
            if (refreshWearOfficial(context)) {
                WearVibration.reschedule(context)
                onUpdated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("WearRefresh", "Auffrischen/Neuzeichnen fehlgeschlagen", e)
        }
    }
}

private suspend fun doRefresh(context: Context, force: Boolean): Boolean {
    val now = System.currentTimeMillis()
    // Der Ort, unter dem ein Fehlschlag verbucht wird. Wird erst gesetzt,
    // sobald er ueberhaupt bekannt ist: faellt die Ausnahme davor (beim
    // Lesen der Einstellungen oder beim Oeffnen des Fetchers), gibt es
    // keinen Ort, unter dem `recordAttempt` etwas festhalten koennte — dann
    // bleibt es beim Protokolleintrag.
    var versuchsOrt: Pair<Double, Double>? = null
    return try {
        val fetcher = WearFetchProvider.fetcher(context) ?: return false

        val location = WearSettings.location(context)
        val active = location.latitude to location.longitude
        versuchsOrt = active
        val today = LocalDate.now()

        // Bei `force` wird `dueOrder` nicht einmal gelesen — dieselbe Abkuerzung
        // wie in `PrayerProvider.refreshOfficial`: ein DataStore-Read weniger im
        // Klick-Pfad, `chooseTarget` liefert bei `force` ohnehin immer
        // [activeCoords].
        val target = chooseTarget(
            due = if (force) emptyList() else WearOfficialCache.dueOrder(context, active, today),
            activeCoords = active,
            force = force,
            today = today,
            nowEpochMs = now,
        ) ?: return false
        versuchsOrt = target
        val (targetLat, targetLng) = target

        // Dasselbe Budget wie am Telefon (~25 s). Laeuft seit Fix-Runde 2
        // NIE mehr auf dem Haupt-Thread eines Zeichenpfads — siehe KDoc an
        // [refreshWearOfficial]/[launchWearRefresh].
        withTimeout(25_000) {
            val city = WearSettings.city(context)
            val preferredId = WearOfficialCache.cachedLocationId(context, targetLat, targetLng)
            val result = fetcher.fetch(targetLat, targetLng, city, preferredId)
            if (result.schedule.isEmpty()) {
                WearOfficialCache.recordAttempt(context, "Kein Ergebnis von den amtlichen Quellen", now, targetLat, targetLng)
                return@withTimeout false
            }
            WearOfficialCache.put(context, result.schedule, targetLat, targetLng, result.locationId)
            // error = null: ein gelungener Abruf ist kein Fehler, auch wenn
            // nicht ALLE drei Quellen antworteten — dieselbe Begruendung wie
            // in `PrayerProvider.refreshOfficial`.
            WearOfficialCache.recordAttempt(context, null, now, targetLat, targetLng)
            true
        }
    } catch (e: TimeoutCancellationException) {
        android.util.Log.w("WearRefresh", "refreshWearOfficial abgebrochen (Timeout)", e)
        verbucheFehlschlag(context, "Zeitüberschreitung beim Abruf", now, versuchsOrt)
        false
    } catch (e: CancellationException) {
        // Ein ECHTER Abbruch (z. B. der Aufrufer selbst wird abgebrochen)
        // muss weiterlaufen, nicht als Fehler protokolliert werden — sonst
        // saehe ein normaler App-Wechsel wie ein gescheiterter Abruf aus.
        // `TimeoutCancellationException` (oben) ist der einzige Fall, den
        // WIR selbst ausloesen und deshalb auch selbst deuten duerfen. Sie
        // MUSS deshalb vor diesem Zweig stehen: sie ist eine Unterklasse von
        // `CancellationException`, ein `catch` faengt immer den ersten
        // passenden Zweig, und die Reihenfolge ist hier der einzige
        // Unterschied zwischen "Timeout korrekt verbucht" und "Timeout still
        // als Abbruch durchgereicht". (Kotlin meldet eine ungluecklich
        // sortierte `catch`-Kette NICHT — anders als Java gibt es hier keinen
        // "unreachable catch"-Fehler, der uns vor dem Vertauschen schuetzen
        // wuerde.)
        throw e
    } catch (e: Exception) {
        // Alles andere (z. B. eine IOException aus dem DataStore oder beim
        // Einlesen der Ortstabellen) darf NICHT unbehandelt bis zum Aufrufer
        // durchschlagen: die drei Aufrufer starten diese Funktion in einem
        // `launch`, und weil mehrere Aufrufer ueber `SingleFlight` dasselbe
        // Ergebnis teilen, traefe eine entkommene Ausnahme sie ALLE — auf
        // Android heisst unbehandelt im Zweifel Prozessabsturz.
        // `SupervisorJob` an `refreshScope` schuetzt davor NICHT — das haelt
        // nur Geschwister-Coroutines am Leben, faengt aber keine Ausnahme ab.
        android.util.Log.w("WearRefresh", "refreshWearOfficial abgebrochen (Fehler)", e)
        verbucheFehlschlag(context, "Fehler beim Abruf: ${e.message}", now, versuchsOrt)
        false
    }
}

/**
 * `recordAttempt` aus einem `catch`-Zweig heraus — selbst ein
 * DataStore-Schreibvorgang, der aus demselben Grund scheitern kann wie das,
 * was uns ueberhaupt erst hierher gebracht hat (volle Platte, defekte
 * Datei). Ein Fehler beim Verbuchen des Fehlers darf den `catch`-Zweig nicht
 * sprengen; er wuerde sonst genau die Ausnahme ersetzen, die wir gerade
 * abgefangen haben, und waere damit wieder unbehandelt.
 *
 * [ort] `null` heisst: die Ausnahme fiel, bevor ueberhaupt feststand, um
 * welchen Ort es geht — dann gibt es nichts zu verbuchen (die Bremse
 * greift beim naechsten Versuch eben nicht, was hier das kleinere Uebel
 * ist: ohne Ort waere jeder Eintrag geraten).
 */
private suspend fun verbucheFehlschlag(
    context: Context,
    fehler: String,
    nowEpochMs: Long,
    ort: Pair<Double, Double>?,
) {
    if (ort == null) return
    try {
        WearOfficialCache.recordAttempt(context, fehler, nowEpochMs, ort.first, ort.second)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("WearRefresh", "Fehlschlag liess sich nicht verbuchen", e)
    }
}
