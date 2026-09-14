package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.DiyanetPrayerTimesCalculator
import de.gebetszeiten.core.prayertimes.GeoLocation
import de.gebetszeiten.core.prayertimes.Prayer
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared prayer-time helpers for the watch. Prioritätskette wie am Phone:
 * amtliche Zeiten (WearOfficialCache) → gebündelte Diyanet-Tabellen
 * (nearest ≤ 25 km) → Berechnung. Im offline-Flavor bleibt es dabei: die
 * Uhr geht selbst nie ins Netz, der Sync vom Handy ist rein empfangend
 * (Play-Services Data Layer). Im ONLINE-Flavor traegt WearOfficialCache
 * seit Aufgabe 6 zusaetzlich, was [refreshWearOfficial] selbst abgerufen
 * hat — der Sync bleibt zwar weiterhin empfangend, ist aber nicht mehr der
 * einzige Weg, wie amtliche Zeiten in den Cache kommen.
 */
object WearPrayer {

    suspend fun today(context: Context, location: GeoLocation, zone: ZoneId): DailyPrayerTimes =
        daily(context, location, ZonedDateTime.now(zone).toLocalDate(), zone)

    /** Next prayer strictly after [now], rolling into tomorrow if needed.
     *  Sunrise is just the end of Fajr's window, not a prayer → skipped. */
    suspend fun next(
        context: Context,
        location: GeoLocation,
        zone: ZoneId,
        now: ZonedDateTime,
    ): Pair<Prayer, ZonedDateTime> = upcoming(context, location, zone, now, count = 1).first()

    /** The next [count] prayers after [now], across the day boundary, skipping
     *  sunrise. Used to build a self-switching tile timeline for the whole day. */
    suspend fun upcoming(
        context: Context,
        location: GeoLocation,
        zone: ZoneId,
        now: ZonedDateTime,
        count: Int,
    ): List<Pair<Prayer, ZonedDateTime>> {
        val today = daily(context, location, now.toLocalDate(), zone)
        val tomorrow = daily(context, location, now.toLocalDate().plusDays(1), zone)
        return (today.ordered() + tomorrow.ordered())
            .filter { it.first != Prayer.SUNRISE && it.second.isAfter(now) }
            .take(count)
    }

    /** Sync-Cache → amtliche Tabelle → Berechnung — dieselbe
     *  Prioritätslogik wie PrayerProvider.daily am Phone. */
    private suspend fun daily(
        context: Context,
        location: GeoLocation,
        date: LocalDate,
        zone: ZoneId,
    ): DailyPrayerTimes {
        if (!WearSettings.useCalculated(context)) {
            WearOfficialCache.get(context, date, location.latitude, location.longitude)
                ?.let { return it.toDaily(date, zone) }
            WearOfficialSource.get(context, location.latitude, location.longitude, date)
                ?.let { return it.toDaily(date, zone) }
        }
        return DiyanetPrayerTimesCalculator.calculate(location, date, zone)
    }
}
