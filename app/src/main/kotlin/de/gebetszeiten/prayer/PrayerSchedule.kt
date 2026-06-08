package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.DiyanetPrayerTimesCalculator
import de.gebetszeiten.core.prayertimes.GeoLocation
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** A single upcoming prayer transition. */
data class NextPrayer(val prayer: Prayer, val time: ZonedDateTime)

/** Pure scheduling helpers on top of the offline engine. */
object PrayerSchedule {

    fun forDate(settings: AppSettings, date: LocalDate, zone: ZoneId): DailyPrayerTimes =
        DiyanetPrayerTimesCalculator.calculate(
            GeoLocation(settings.latitude, settings.longitude),
            date,
            zone,
        )

    /** The next prayer strictly after [now], searching today then tomorrow. */
    fun next(settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer {
        val today = forDate(settings, now.toLocalDate(), zone)
        today.ordered().firstOrNull { it.second.isAfter(now) }?.let {
            return NextPrayer(it.first, it.second)
        }
        val tomorrow = forDate(settings, now.toLocalDate().plusDays(1), zone)
        val first = tomorrow.ordered().first()
        return NextPrayer(first.first, first.second)
    }

    /** The prayer whose time is at or just before [now] (the current period). */
    fun currentlyActive(settings: AppSettings, zone: ZoneId, now: ZonedDateTime): NextPrayer? {
        val today = forDate(settings, now.toLocalDate(), zone)
        return today.ordered()
            .lastOrNull { !it.second.isAfter(now) }
            ?.let { NextPrayer(it.first, it.second) }
    }
}
