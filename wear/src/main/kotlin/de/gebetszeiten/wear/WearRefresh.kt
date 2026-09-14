package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Eigener, langlebiger Scope fuer den Netzabruf — unabhaengig vom Scope des
 * jeweiligen Aufrufers (`MainActivity`s `MainScope`, oder der Aufrufer haelt
 * ueberhaupt keinen eigenen, wie `PrayerTileService`/`PrayerComplicationService`
 * seit Fix-Runde 2). `SupervisorJob`, damit ein Fehlschlag EINES Abrufs nicht
 * den Scope fuer alle folgenden Aufrufe mit umbringt.
 */
private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Schuetzt [inFlight] — reine Buchhaltung, keine Netz-I/O unter dem Lock. */
private val refreshLock = Mutex()

/**
 * Der gerade laufende Abruf, falls einer laeuft — geteilt zwischen ALLEN
 * Aufrufern. Die Uhr hat DREI unabhaengige Ausloeser (Activity, Kachel,
 * Komplikation), anders als das Telefon mit seinem einzelnen
 * Alarm-Receiver: treffen zwei davon gleichzeitig ein (z. B. Handgelenk-Heben
 * zeigt Kachel UND Komplikation neu, genau beim faelligen Gebetsuebergang),
 * wuerde ohne diese Buendelung JEDER einen eigenen Abruf starten — drei
 * parallele Abrufe desselben Ortes, je drei HTTP-Anfragen, neun statt drei,
 * ueber die Bluetooth-Strecke der Uhr. Ein zweiter Aufrufer bekommt
 * stattdessen dasselbe [Deferred] und wartet dessen Ergebnis ab, statt einen
 * eigenen Lauf zu starten.
 *
 * Bewusst EIN gemeinsamer Slot fuer die ganze Funktion, nicht einer je Ort:
 * die Uhr hat ohnehin nur den einen aktiven Ort ([WearSettings.location]),
 * ein zweiter Aufruf waehrend eines laufenden waere fuer denselben Ort
 * gedacht. Trifft ein `force`-Aufruf auf einen bereits laufenden
 * unforcierten, wird trotzdem nur der laufende abgewartet — [force] ist
 * (Stand Fix-Runde 2) an keinem Uhr-Knopf verdrahtet, dieser Fall also
 * praktisch nicht erreichbar; sollte er es werden, verdient er eine eigene
 * Entscheidung, kein stillschweigendes Downgrade hier.
 */
private var inFlight: Deferred<Boolean>? = null

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
 * aufgerufen (ANR-Schwelle 5 s) — sie duerfen diese Funktion deshalb nur
 * fire-and-forget in einem eigenen Hintergrund-Scope starten und bei Erfolg
 * (Rueckgabewert `true`) eine Neuzeichnung anfordern, NIE per `runBlocking`
 * abwarten. `MainActivity` macht das bereits richtig (`scope.launch {
 * withContext(Dispatchers.IO) { refreshWearOfficial(...) } }`, ausserhalb des
 * synchronen `onStart`-Pfads).
 *
 * Bündelt gleichzeitige Aufrufe ueber [inFlight] (Begruendung dort) — ruft
 * intern hoechstens EINEN Netzabruf gleichzeitig aus, egal wie viele der
 * drei Ausloeser (Activity/Kachel/Komplikation) gerade zusammentreffen.
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

    val deferred = refreshLock.withLock {
        inFlight ?: refreshScope.async { doRefresh(context, force) }.also { inFlight = it }
    }
    return try {
        deferred.await()
    } finally {
        refreshLock.withLock { if (inFlight === deferred) inFlight = null }
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
        // [refreshWearOfficial].
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
    }
}
