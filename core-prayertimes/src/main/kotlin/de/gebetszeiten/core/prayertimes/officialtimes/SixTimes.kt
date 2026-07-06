package de.gebetszeiten.core.prayertimes.officialtimes

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
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
