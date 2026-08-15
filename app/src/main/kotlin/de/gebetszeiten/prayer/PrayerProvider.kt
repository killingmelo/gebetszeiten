package de.gebetszeiten.prayer

import android.content.Context
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
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

    /** Online flavor + user opted in: refresh the official-times cache —
     *  aber nur bei Standortwechsel oder wenn weniger als 7 Tage Zukunft
     *  abgedeckt sind (Diyanet-Jahresseite direkt liefert ein ganzes Jahr,
     *  Fallback-Proxy nur 31 Tage rollierend). */
    suspend fun refreshOfficial(context: Context, settings: AppSettings) {
        if (!settings.useOnline || settings.useCalculated) return
        val cache = OfficialTimesCache(context)
        val (stampOk, coveredUntil) = cache.freshness(settings.latitude, settings.longitude)
        if (!needsRefresh(coveredUntil, LocalDate.now(), stampOk)) {
            // Frischer Cache = kein Netz-Refresh. Trotzdem den GECACHTEN Stand
            // zur Uhr replizieren: sonst bekaeme ein Bestandsnutzer mit
            // Jahres-Cache monatelang nichts gesynct. Gleicher Inhalt = das
            // DataItem bleibt unveraendert, der Data-Layer dedupliziert und die
            // Uhr wird nicht geweckt. Offline-Flavor: syncToWear ist ein No-op.
            OfficialTimesProvider.syncToWear(context, cache.snapshot(settings.latitude, settings.longitude), settings)
            return
        }
        val fetcher = OfficialTimesProvider.fetcher(context) ?: return
        val now = System.currentTimeMillis()
        // Broadcast-Budget (~10-30 s im Alarm-Receiver): der Refresh darf den
        // Empfaenger nicht unbegrenzt blockieren — naechster Anlauf beim
        // folgenden Gebet. Budget: ~25 s plus max. 10 s gebundenes Tasks.await
        // im Wear-Push, Worst Case also ~35 s; praktisch greifen die
        // HTTP-Timeouts frueher. Nur der Timeout wird geschluckt; echte
        // Cancellation propagiert (Composite reicht sie durch).
        try {
            withTimeout(25_000) {
                val result = fetcher.fetch(settings)
                if (result.schedule.isEmpty()) {
                    cache.recordAttempt("Keine amtlichen Zeiten erhalten (Standort oder Netz)", now)
                    return@withTimeout
                }
                cache.putAll(result.schedule, settings.latitude, settings.longitude, result.locationId)
                cache.recordAttempt(null, now)
                OfficialTimesProvider.syncToWear(context, result.schedule, settings)
            }
        } catch (e: TimeoutCancellationException) {
            android.util.Log.w("PrayerProvider", "refreshOfficial abgebrochen (Timeout)", e)
            cache.recordAttempt("Zeitüberschreitung beim Abruf", now)
        }
    }
}
