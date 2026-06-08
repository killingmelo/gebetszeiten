package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Six official times for one day (local wall-clock, no date attached). */
data class SixTimes(
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val asr: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime,
) {
    fun toDaily(date: LocalDate, zone: ZoneId): DailyPrayerTimes {
        fun at(t: LocalTime): ZonedDateTime = ZonedDateTime.of(date, t, zone)
        return DailyPrayerTimes(
            date = date,
            zone = zone,
            fajr = at(fajr),
            sunrise = at(sunrise),
            dhuhr = at(dhuhr),
            asr = at(asr),
            maghrib = at(maghrib),
            isha = at(isha),
        )
    }
}

/**
 * Fetches exact official Diyanet times for a location. Only the `online`
 * product flavor provides a real implementation; the `offline` flavor supplies
 * none (see the flavor-specific [OfficialTimesProvider]).
 */
interface OfficialTimesFetcher {
    /** Returns date → times for as many upcoming days as the source offers. */
    suspend fun fetch(settings: AppSettings): Map<LocalDate, SixTimes>
}
