package de.gebetszeiten.core.prayertimes

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** A geographic position. No source is implied — the app obtains this from
 *  manual city selection or manual coordinate entry (never via GPS tracking). */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)

/** The six daily times the app tracks. (Sunrise marks the end of Fajr.) */
enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/** The computed times for a single calendar day at a single location/zone. */
data class DailyPrayerTimes(
    val date: LocalDate,
    val zone: ZoneId,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
) {
    fun time(prayer: Prayer): ZonedDateTime = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    /** Times in chronological order, paired with their [Prayer]. */
    fun ordered(): List<Pair<Prayer, ZonedDateTime>> = listOf(
        Prayer.FAJR to fajr,
        Prayer.SUNRISE to sunrise,
        Prayer.DHUHR to dhuhr,
        Prayer.ASR to asr,
        Prayer.MAGHRIB to maghrib,
        Prayer.ISHA to isha,
    )
}
