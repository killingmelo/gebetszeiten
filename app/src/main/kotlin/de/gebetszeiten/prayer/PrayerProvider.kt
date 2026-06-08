package de.gebetszeiten.prayer

import android.content.Context
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.official.OfficialTimesCache
import de.gebetszeiten.official.OfficialTimesProvider
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
        OfficialTimesCache(context).get(date)?.let { return it.toDaily(date, zone) }
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

    suspend fun currentlyActive(context: Context, settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer? {
        val today = daily(context, settings, now.toLocalDate(), zone)
        return today.ordered()
            .lastOrNull { !it.second.isAfter(now) }
            ?.let { NextPrayer(it.first, it.second) }
    }

    /** Online flavor only: refresh the official-times cache for the location. */
    suspend fun refreshOfficial(context: Context, settings: AppSettings) {
        val fetcher = OfficialTimesProvider.fetcher(context) ?: return
        OfficialTimesCache(context).putAll(fetcher.fetch(settings))
    }
}
