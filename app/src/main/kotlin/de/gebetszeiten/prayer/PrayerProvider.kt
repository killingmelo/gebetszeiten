package de.gebetszeiten.prayer

import android.content.Context
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.DaySource
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import de.gebetszeiten.core.prayertimes.officialtimes.daySourceOrder
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.official.BundledOfficialSource
import de.gebetszeiten.official.OfficialTimesCache
import de.gebetszeiten.official.OfficialTimesProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Single source of prayer times for the whole app. Prefers cached official
 * Diyanet times when present (online flavor), otherwise falls back to the
 * offline engine. In the offline flavor the cache is always empty, so this is
 * always the offline calculation.
 */
object PrayerProvider {

    /** Amtliche Zeiten oder gar keine: `null` heisst immer dasselbe — unter
     *  den aktuellen Einstellungen liegen keine Zeiten vor. [daySourceOrder]
     *  legt fest, welche Quellen in welcher Reihenfolge befragt werden; die
     *  Berechnung steht darin nur, wenn der Nutzer sie als Notausgang
     *  eingeschaltet hat (`settings.useCalculated` — Umbenennung zu
     *  `calculationFillsGaps` folgt in Aufgabe 15). */
    suspend fun daily(context: Context, settings: AppSettings, date: LocalDate, zone: ZoneId): DailyPrayerTimes? {
        for (source in daySourceOrder(settings.useOnline, settings.useCalculated)) {
            when (source) {
                DaySource.ONLINE_CACHE ->
                    OfficialTimesCache(context).get(date, settings.latitude, settings.longitude)
                        ?.let { return it.toDaily(date, zone) }
                DaySource.BUNDLED_TABLE ->
                    BundledOfficialSource.get(context, settings.latitude, settings.longitude, date)
                        ?.let { return it.toDaily(date, zone) }
                DaySource.CALCULATION ->
                    return PrayerSchedule.forDate(settings, date, zone)
            }
        }
        return null
    }

    suspend fun next(context: Context, settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer? {
        val today = daily(context, settings, now.toLocalDate(), zone) ?: return null
        today.ordered().firstOrNull { it.second.isAfter(now) }?.let {
            return NextPrayer(it.first, it.second)
        }
        val tomorrow = daily(context, settings, now.toLocalDate().plusDays(1), zone) ?: return null
        val first = tomorrow.ordered().first()
        return NextPrayer(first.first, first.second)
    }

    /** Next actual prayer — sunrise (not a prayer) is skipped. */
    suspend fun nextPrayer(context: Context, settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer? {
        var candidate = next(context, settings, zone, now) ?: return null
        if (candidate.prayer == de.gebetszeiten.core.prayertimes.Prayer.SUNRISE) {
            candidate = next(context, settings, zone, candidate.time) ?: return null
        }
        return candidate
    }

    /** End of the running prayer's window when it is NOT the next prayer's
     *  start — only Fajr, whose window ends (invalidates) at sunrise. */
    suspend fun activeUntil(
        context: Context,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
        active: NextPrayer?,
    ): ZonedDateTime? =
        if (active?.prayer == de.gebetszeiten.core.prayertimes.Prayer.FAJR) {
            daily(context, settings, now.toLocalDate(), zone)?.sunrise
        } else {
            null
        }

    suspend fun currentlyActive(context: Context, settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer? {
        val today = daily(context, settings, now.toLocalDate(), zone) ?: return null
        return today.ordered()
            .lastOrNull { !it.second.isAfter(now) }
            ?.let { NextPrayer(it.first, it.second) }
    }

    /** Online flavor + user opted in: den amtlichen Zeiten-Cache auffrischen —
     *  und zwar den FAELLIGSTEN Ort, nicht nur den aktiven.
     *
     *  Das ist der Kern von „einmal Nuernberg einspeichern und sich nie wieder
     *  kuemmern": [CacheStore.dueOrder] ordnet Favoriten und aktiven Ort nach
     *  Dringlichkeit, und [chooseTarget] nimmt daraus den ersten, der die
     *  Wiederholungs-Bremse passiert. Bei ~6 Ausloesern am Tag
     *  (App-Start, Einstellungsaenderung, Knopf, Gebets-Alarme) sind zehn
     *  Favoriten binnen zwei Tagen versorgt.
     *
     *  **Genau EIN Abruf je Ausloeser.** Nicht mehrere, auch nicht
     *  nebenlaeufig: das Broadcast-Budget von ~25 s gilt weiter, und der
     *  naechste Ort ist beim naechsten Gebet dran. Passiert kein Kandidat die
     *  Bremse, wird nichts abgerufen.
     *
     *  [force] (der „Jetzt aktualisieren"-Knopf) gilt nur fuer den AKTIVEN
     *  Ort. Der Nutzer meint damit, was er vor sich sieht — nicht einen
     *  Favoriten am anderen Ende der Liste. */
    suspend fun refreshOfficial(context: Context, settings: AppSettings, force: Boolean = false) {
        if (!settings.useOnline || settings.useCalculated) return
        val cache = OfficialTimesCache(context)
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val active = settings.latitude to settings.longitude
        // Angeheftete Orte fuer die Verdraengung: ein Favorit darf nie aus
        // dem Cache fallen. Hier gelesen und uebergeben, damit der Cache
        // selbst die Einstellungen nicht kennen muss.
        val pinned = settings.favorites.map { it.city.latitude to it.city.longitude }

        // Bei `force` wird `dueOrder` nicht einmal gelesen — der Knopf gilt
        // ohnehin nur fuer den aktiven Ort, und ein DataStore-Read weniger
        // im Klick-Pfad.
        val target = chooseTarget(
            due = if (force) emptyList() else cache.dueOrder(pinned, active, today),
            activeCoords = active,
            force = force,
            today = today,
            nowEpochMs = now,
        )

        var fetchedActive: Map<LocalDate, SixTimes>? = null
        val fetcher = if (target == null) null else OfficialTimesProvider.fetcher(context)
        if (target != null && fetcher != null) {
            val (targetLat, targetLng) = target
            // Broadcast-Budget (~10-30 s im Alarm-Receiver): der Refresh darf
            // den Empfaenger nicht unbegrenzt blockieren — naechster Anlauf
            // beim folgenden Gebet. Budget: ~25 s plus max. 10 s gebundenes
            // Tasks.await im Wear-Push, Worst Case also ~35 s; praktisch
            // greifen die HTTP-Timeouts frueher. Nur der Timeout wird
            // geschluckt; echte Cancellation propagiert (Composite reicht sie
            // durch).
            try {
                withTimeout(25_000) {
                    val targetCityName = targetCity(settings, targetLat, targetLng)
                    // Die zuletzt bekannte Diyanet-ID fuer diesen Ort: die
                    // Netzschicht darf `OfficialTimesCache` (DataStore, ein
                    // app-Typ) nicht mehr selbst befragen, deshalb kommt der
                    // Wert von hier — gleiches Verhalten, anderer Ort.
                    val preferredId = cache.cachedLocationId(targetLat, targetLng)
                    val result = fetcher.fetch(targetLat, targetLng, targetCityName, preferredId)
                    if (result.schedule.isEmpty()) {
                        cache.recordAttempt(
                            // Wortlaut und Rueckfall in `emptyResultError`,
                            // einer reinen Funktion: hier drinnen — Context,
                            // Netz, DataStore — waeren sie ohne Robolectric
                            // nicht pruefbar, dort sind sie es. Den
                            // deutschen Fehlertext baut jetzt der Aufrufer
                            // aus `result.candidates` (nicht mehr der
                            // Fetcher — der kennt `fetchErrorSummary` nicht
                            // mehr, ein app-Typ).
                            emptyResultError(fetchErrorSummary(result.candidates)),
                            now,
                            targetLat,
                            targetLng,
                            pinned,
                        )
                        return@withTimeout
                    }
                    // Koordinaten des GEWAEHLTEN Orts, nie die aktiven: sonst
                    // landete Istanbuls Zeitplan unter Nuernbergs Stempel —
                    // falsche Gebetszeiten, die richtig aussehen.
                    cache.putAll(
                        result.schedule,
                        targetLat,
                        targetLng,
                        pinned,
                        result.locationId,
                        result.verification,
                    )
                    // `error = null`, auch wenn EINE von drei Quellen
                    // gescheitert ist (`fetchErrorSummary(result.candidates)`
                    // waere dann gesetzt): ein gelungener Abruf ist kein
                    // Fehler. Ein
                    // `lastError` neben vorhandenen Zeiten wuerde die
                    // Statuszeile "Fehler:" schreiben lassen und — an einem
                    // Ort ohne Zeitplan — `DueLocation.hopeless` ausloesen,
                    // also die Auswahl bremsen. Dass nur eine Quelle
                    // erreichbar war, sagt ohnehin die Pruefnotiz
                    // ("nicht möglich — nur eine Quelle erreichbar").
                    cache.recordAttempt(null, now, targetLat, targetLng, pinned)
                    if (stampMatches(active.first, active.second, targetLat, targetLng)) {
                        fetchedActive = result.schedule
                    }
                }
            } catch (e: TimeoutCancellationException) {
                android.util.Log.w("PrayerProvider", "refreshOfficial abgebrochen (Timeout)", e)
                cache.recordAttempt("Zeitüberschreitung beim Abruf", now, targetLat, targetLng, pinned)
            }
        }

        // Zur Uhr geht IMMER der Stand des AKTIVEN Orts — niemals der des
        // gerade aufgefrischten. Frischt die App im Hintergrund Istanbul auf,
        // waehrend der Nutzer in Nuernberg steht, wuerde die Uhr sonst
        // Istanbuler Zeiten anzeigen.
        //
        // Das laeuft auch dann, wenn nichts abgerufen wurde: sonst bekaeme ein
        // Nutzer mit vollem Jahres-Cache monatelang nichts gesynct. Gleicher
        // Inhalt = das DataItem bleibt unveraendert, der Data-Layer
        // dedupliziert und die Uhr wird nicht geweckt. Offline-Flavor:
        // syncToWear ist ein No-op.
        val activeSchedule = fetchedActive ?: cache.snapshot(active.first, active.second)
        // Zeitpunkt DIESES Standes (nicht "jetzt") — die Uhr vergleicht ihn
        // gegen einen evtl. frischeren eigenen Fund (siehe
        // `SyncDecision.syncWins` im wear-Modul). Nach einem frischen Abruf
        // ist das derselbe `now` wie oben (putAll schrieb ihn eben); kam
        // `activeSchedule` aus dem Snapshot, ist es der Zeitpunkt DES
        // Abrufs, der diesen Snapshot brachte — genauso ehrlich.
        val activeUpdatedEpochMs = cache.updatedEpochMs(active.first, active.second)
        OfficialTimesProvider.syncToWear(context, activeSchedule, settings, activeUpdatedEpochMs)
    }

    /** Der Ortsname fuer [lat]/[lng] — das letzte Glied der ID-Aufloesung
     *  (`resolveLocationIdChain` → Namenssuche). `settings.city` gehoert zum
     *  aktiven Ort — fuer einen Favoriten waere dieser Name schlicht falsch
     *  und schlimmer als keiner. Er kommt deshalb aus dem Favoriten selbst.
     *  Die ersten drei Glieder der Kette (Bundle, Koordinatenindex, Cache)
     *  arbeiten ohnehin nur mit Koordinaten; die Namenssuche ist seit dem
     *  weltweiten Index nur noch Lueckenfueller. */
    private fun targetCity(settings: AppSettings, lat: Double, lng: Double): String {
        if (stampMatches(settings.latitude, settings.longitude, lat, lng)) {
            return settings.city
        }
        // Unerreichbar: die Kandidaten sind Favoriten oder der aktive Ort.
        // Leer statt settings.city, weil ein falscher Name in der
        // Namenssuche schlechter ist als gar keiner.
        return settings.favorites
            .firstOrNull { stampMatches(it.city.latitude, it.city.longitude, lat, lng) }
            ?.city?.name
            ?: ""
    }
}
