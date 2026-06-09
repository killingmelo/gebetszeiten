package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.DiyanetPrayerTimesCalculator
import de.gebetszeiten.core.prayertimes.GeoLocation
import de.gebetszeiten.core.prayertimes.Prayer
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared prayer-time helpers for the watch. The watch computes locally with the
 * same offline engine as the phone — fully standalone, works without the phone.
 *
 * The location comes from [WearSettings] (a sensible default). Phone→watch sync
 * was dropped to keep the app fully FOSS (no proprietary Play Services) and
 * F-Droid-eligible; a watch-side location picker can be added later.
 */
object WearPrayer {

    fun today(location: GeoLocation, zone: ZoneId): DailyPrayerTimes =
        DiyanetPrayerTimesCalculator.calculate(location, ZonedDateTime.now(zone).toLocalDate(), zone)

    /** Next prayer strictly after [now], rolling into tomorrow if needed.
     *  Sunrise is just the end of Fajr's window, not a prayer → skipped. */
    fun next(location: GeoLocation, zone: ZoneId, now: ZonedDateTime): Pair<Prayer, ZonedDateTime> =
        upcoming(location, zone, now, count = 1).first()

    /** The next [count] prayers after [now], across the day boundary, skipping
     *  sunrise. Used to build a self-switching tile timeline for the whole day. */
    fun upcoming(
        location: GeoLocation,
        zone: ZoneId,
        now: ZonedDateTime,
        count: Int,
    ): List<Pair<Prayer, ZonedDateTime>> {
        val today = today(location, zone)
        val tomorrow = DiyanetPrayerTimesCalculator.calculate(location, now.toLocalDate().plusDays(1), zone)
        return (today.ordered() + tomorrow.ordered())
            .filter { it.first != Prayer.SUNRISE && it.second.isAfter(now) }
            .take(count)
    }
}
