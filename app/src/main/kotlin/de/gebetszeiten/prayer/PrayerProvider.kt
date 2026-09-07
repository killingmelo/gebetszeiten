package de.gebetszeiten.prayer

import android.content.Context
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.DueLocation
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.official.BundledOfficialSource
import de.gebetszeiten.official.OfficialTimesCache
import de.gebetszeiten.official.OfficialTimesProvider
import de.gebetszeiten.official.needsRefresh
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

    suspend fun daily(context: Context, settings: AppSettings, date: LocalDate, zone: ZoneId): DailyPrayerTimes {
        // 0) Nutzer hat explizit die eigene Berechnung gewählt.
        if (settings.useCalculated) return PrayerSchedule.forDate(settings, date, zone)
        // 1) Online-Cache (frischste Quelle, nur wenn aktiviert).
        if (settings.useOnline) {
            OfficialTimesCache(context).get(date, settings.latitude, settings.longitude)
                ?.let { return it.toDaily(date, zone) }
        }
        // 2) Gebündelte amtliche Tabelle (offline, nearest Diyanet-Standort ≤ 25 km).
        BundledOfficialSource.get(context, settings.latitude, settings.longitude, date)
            ?.let { return it.toDaily(date, zone) }
        // 3) Fallback: Berechnung.
        return PrayerSchedule.forDate(settings, date, zone)
    }

    suspend fun next(context: Context, settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer {
        val today = daily(context, settings, now.toLocalDate(), zone)
        today.ordered().firstOrNull { it.second.isAfter(now) }?.let {
            return NextPrayer(it.first, it.second)
        }
        val tomorrow = daily(context, settings, now.toLocalDate().plusDays(1), zone)
        val first = tomorrow.ordered().first()
        return NextPrayer(first.first, first.second)
    }

    /** Next actual prayer — sunrise (not a prayer) is skipped. */
    suspend fun nextPrayer(context: Context, settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer {
        var candidate = next(context, settings, zone, now)
        if (candidate.prayer == de.gebetszeiten.core.prayertimes.Prayer.SUNRISE) {
            candidate = next(context, settings, zone, candidate.time)
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
            daily(context, settings, now.toLocalDate(), zone).sunrise
        } else {
            null
        }

    suspend fun currentlyActive(context: Context, settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer? {
        val today = daily(context, settings, now.toLocalDate(), zone)
        return today.ordered()
            .lastOrNull { !it.second.isAfter(now) }
            ?.let { NextPrayer(it.first, it.second) }
    }

    /** Online flavor + user opted in: den amtlichen Zeiten-Cache auffrischen —
     *  und zwar den FAELLIGSTEN Ort, nicht nur den aktiven.
     *
     *  Das ist der Kern von „einmal Nuernberg einspeichern und sich nie wieder
     *  kuemmern": [CacheStore.dueOrder] ordnet Favoriten und aktiven Ort nach
     *  Dringlichkeit, und hier wird der erste genommen, der die
     *  Wiederholungs-Bremse ([needsRefresh]) passiert. Bei ~6 Ausloesern am Tag
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

        val target: Pair<Double, Double>? = if (force) {
            active
        } else {
            cache.dueOrder(pinned, active, today)
                .firstOrNull { it.isDue(today, now) }
                ?.let { it.latitude to it.longitude }
        }

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
                    val result = fetcher.fetch(targetSettings(settings, targetLat, targetLng))
                    if (result.schedule.isEmpty()) {
                        cache.recordAttempt(
                            "Keine amtlichen Zeiten erhalten (Standort oder Netz)",
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
                    cache.putAll(result.schedule, targetLat, targetLng, pinned, result.locationId)
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
        OfficialTimesProvider.syncToWear(context, activeSchedule, settings)
    }

    /** Passt die Bremse auf einen Kandidaten aus [CacheStore.dueOrder] an.
     *  Alles kommt aus SEINEM Eintrag, nicht aus dem des aktiven Orts —
     *  `stampOk` heisst „hat einen Zeitplan", genau wie in
     *  `OfficialTimesCache.status`. */
    private fun DueLocation.isDue(today: LocalDate, nowEpochMs: Long): Boolean {
        val header = entry?.header
        return needsRefresh(
            coveredUntil = header?.lastDate,
            today = today,
            stampOk = header?.lastDate != null,
            lastAttemptEpochMs = header?.lastAttemptEpochMs,
            lastAttemptFailed = header?.lastError != null,
            nowEpochMs = nowEpochMs,
        )
    }

    /** [settings] auf den gewaehlten Ort umgestellt.
     *
     *  Der Ortsname ist dabei nicht kosmetisch: er ist das letzte Glied der
     *  ID-Aufloesung (`resolveLocationIdChain` → Namenssuche). `settings.city`
     *  gehoert zum aktiven Ort — fuer einen Favoriten waere dieser Name
     *  schlicht falsch und schlimmer als keiner. Er kommt deshalb aus dem
     *  Favoriten selbst. Die ersten drei Glieder der Kette (Bundle,
     *  Koordinatenindex, Cache) arbeiten ohnehin nur mit Koordinaten; die
     *  Namenssuche ist seit dem weltweiten Index nur noch Lueckenfueller. */
    private fun targetSettings(settings: AppSettings, lat: Double, lng: Double): AppSettings {
        if (stampMatches(settings.latitude, settings.longitude, lat, lng)) {
            return settings.copy(latitude = lat, longitude = lng)
        }
        val name = settings.favorites
            .firstOrNull { stampMatches(it.city.latitude, it.city.longitude, lat, lng) }
            ?.city?.name
            // Unerreichbar: die Kandidaten sind Favoriten oder der aktive Ort.
            // Leer statt settings.city, weil ein falscher Name in der
            // Namenssuche schlechter ist als gar keiner.
            ?: ""
        return settings.copy(latitude = lat, longitude = lng, city = name)
    }
}
