package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Die Uhr ruft ihre amtlichen Zeiten selbst ab, statt nur auf den Sync vom
 * Handy zu warten (siehe [WearOfficialCache], bis hier ein reiner
 * Empfangspfad). Muster: `PrayerProvider.refreshOfficial` am Telefon — aber
 * nur fuer den EINEN gewaehlten Uhr-Ort ([WearSettings.location]), kein
 * Favoritenreigen: die Uhr fuehrt keine eigene Favoritenliste.
 *
 * Dieselbe Wiederholungs-Bremse wie am Telefon ([needsRefresh] ueber
 * [chooseTarget]) verhindert, dass Kachel und Komplikation bei jedem
 * Zeichnen erneut abrufen — beide rufen diese Funktion bei jeder
 * Aktualisierung auf, [MainActivity] zusaetzlich bei jedem `onStart`.
 *
 * Offline-Flavor: [WearFetchProvider.isOnline] ist `false` und
 * [WearFetchProvider.fetcher] liefert `null` — beides fuehrt sofort zur
 * Rueckkehr, ohne dass hier auch nur die Cache-DataStore geoeffnet wird.
 *
 * [force] (der "Jetzt aktualisieren"-Knopf, falls die Uhr einen bekommt)
 * durchbricht die Bremse fuer den aktiven Ort — dieselbe Semantik wie am
 * Telefon.
 */
suspend fun refreshWearOfficial(context: Context, force: Boolean = false) {
    if (!WearFetchProvider.isOnline) return
    val fetcher = WearFetchProvider.fetcher(context) ?: return

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
    ) ?: return
    val (targetLat, targetLng) = target

    try {
        // Dasselbe Budget wie am Telefon (~25 s): der Aufruf haengt an jeder
        // Tile-/Complication-Aktualisierung, nicht an einem eigenen
        // Broadcast-Fenster — trotzdem darf er den Zeichenpfad nicht
        // unbegrenzt blockieren.
        withTimeout(25_000) {
            val city = WearSettings.city(context)
            val preferredId = WearOfficialCache.cachedLocationId(context, targetLat, targetLng)
            val result = fetcher.fetch(targetLat, targetLng, city, preferredId)
            if (result.schedule.isEmpty()) {
                WearOfficialCache.recordAttempt(context, "Kein Ergebnis von den amtlichen Quellen", now, targetLat, targetLng)
                return@withTimeout
            }
            WearOfficialCache.put(context, result.schedule, targetLat, targetLng, result.locationId)
            // error = null: ein gelungener Abruf ist kein Fehler, auch wenn
            // nicht ALLE drei Quellen antworteten — dieselbe Begruendung wie
            // in `PrayerProvider.refreshOfficial`.
            WearOfficialCache.recordAttempt(context, null, now, targetLat, targetLng)
        }
    } catch (e: TimeoutCancellationException) {
        android.util.Log.w("WearRefresh", "refreshWearOfficial abgebrochen (Timeout)", e)
        WearOfficialCache.recordAttempt(context, "Zeitüberschreitung beim Abruf", now, targetLat, targetLng)
    }
}
