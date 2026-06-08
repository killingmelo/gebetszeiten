package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.DiyanetPrayerTimesCalculator
import de.gebetszeiten.core.prayertimes.GeoLocation
import de.gebetszeiten.core.prayertimes.Prayer
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared prayer-time helpers for the watch. The watch computes locally with the
 * same offline engine as the phone — no per-day sync needed, works without the
 * phone nearby.
 *
 * The location is currently a sensible default; syncing it from the paired
 * phone via the Wearable Data Layer is the next step (Phase 2b).
 */
object WearPrayer {

    fun today(location: GeoLocation, zone: ZoneId): DailyPrayerTimes =
        DiyanetPrayerTimesCalculator.calculate(location, ZonedDateTime.now(zone).toLocalDate(), zone)

    /** Next prayer strictly after [now], rolling into tomorrow if needed. */
    fun next(location: GeoLocation, zone: ZoneId, now: ZonedDateTime): Pair<Prayer, ZonedDateTime> {
        today(location, zone).ordered().firstOrNull { it.second.isAfter(now) }?.let { return it }
        val tomorrow = DiyanetPrayerTimesCalculator.calculate(
            location, now.toLocalDate().plusDays(1), zone,
        )
        return tomorrow.ordered().first()
    }
}
