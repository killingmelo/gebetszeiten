package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Eigener, langlebiger Scope fuer den Netzabruf — unabhaengig vom Scope des
 * jeweiligen Aufrufers. `MainActivity` haelt selbst einen langlebigen
 * `MainScope`, aber `PrayerTileService`/`PrayerComplicationService` sind
 * GEBUNDENE Dienste: das System loest sie Sekunden nach der Antwort wieder,
 * ihr eigener Scope waere fuer einen bis zu 25 s laufenden Netzabruf zu
 * kurzlebig (Fix-Runde 3, Important: ohne diesen datei-eigenen Scope stirbt
 * die Neuzeichnung mit dem Service, bevor `await()` je zurueckkehrt — siehe
 * [launchWearRefresh]). `SupervisorJob`, damit ein Fehlschlag EINES Abrufs
 * nicht den Scope fuer alle folgenden Aufrufe mit umbringt — schuetzt aber
 * NICHT davor, dass eine unbehandelte Ausnahme in `doRefresh` den Aufrufer
 * (bzw. hier: [launchWearRefresh]s `launch`-Coroutine) abstuerzen laesst;
 * das leistet allein das `catch` dort.
 */
private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
 * unforcierten, wird trotzdem nur der laufende abgewartet — [force] ist
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
 * Rueckkehr, ohne dass hier auch nur die Cache-DataStore geoeffnet wird.
 * Genauso, wenn der Nutzer auf der Uhr die eigene Berechnung gewaehlt hat
 * ([WearSettings.useCalculated]): `WearPrayer.daily` liest den amtlichen
 * Cache dann ohnehin nicht — ein Abruf waere reiner Akkuverbrauch ohne
 * Wirkung (dieselbe erste Zeile wie in `PrayerProvider.refreshOfficial`).
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
    if (!WearFetchProvider.isOnline) return false
    if (WearSettings.useCalculated(context)) return false

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
 */
fun launchWearRefresh(context: Context, onUpdated: () -> Unit) {
    refreshScope.launch {
        if (refreshWearOfficial(context)) {
            onUpdated()
        }
    }
}

private suspend fun doRefresh(context: Context, force: Boolean): Boolean {
    val fetcher = WearFetchProvider.fetcher(context) ?: return false

    val location = WearSettings.location(context)
    val active = location.latitude to location.longitude
    val today = LocalDate.now()
    val now = System.currentTimeMillis()

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
    val (targetLat, targetLng) = target

    return try {
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
        WearOfficialCache.recordAttempt(context, "Zeitüberschreitung beim Abruf", now, targetLat, targetLng)
        false
    } catch (e: CancellationException) {
        // Ein ECHTER Abbruch (z. B. der Aufrufer selbst wird abgebrochen)
        // muss weiterlaufen, nicht als Fehler protokolliert werden — sonst
        // saehe ein normaler App-Wechsel wie ein gescheiterter Abruf aus.
        // `TimeoutCancellationException` (oben) ist der einzige Fall, den
        // WIR selbst ausloesen und deshalb auch selbst deuten duerfen.
        throw e
    } catch (e: Exception) {
        // Alles andere (z. B. eine IOException aus dem DataStore in
        // `put`/`recordAttempt`) darf NICHT unbehandelt bis zum Aufrufer
        // durchschlagen: `launchWearRefresh` startet ohne eigenen
        // `CoroutineExceptionHandler`, eine hier entkommene Ausnahme risse
        // die Coroutine (und, weil mehrere Aufrufer ueber `SingleFlight`
        // dasselbe Ergebnis teilen, potenziell mehr als nur den einen
        // urspruenglichen Aufrufer) mit sich. `SupervisorJob` an
        // `refreshScope` schuetzt davor NICHT — das haelt nur Geschwister-
        // Coroutines am Leben, faengt aber keine Ausnahme ab.
        android.util.Log.w("WearRefresh", "refreshWearOfficial abgebrochen (Fehler)", e)
        WearOfficialCache.recordAttempt(context, "Fehler beim Abruf: ${e.message}", now, targetLat, targetLng)
        false
    }
}
