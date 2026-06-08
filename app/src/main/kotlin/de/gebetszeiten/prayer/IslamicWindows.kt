package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import java.time.ZonedDateTime

/** The three Hanafi makruh (karaha) windows during which prayer is disliked. */
data class KarahaTimes(
    /** Sunrise until the sun has risen a spear's length (İşrak). */
    val sunriseStart: ZonedDateTime,
    val sunriseEnd: ZonedDateTime,
    /** Just before the zenith until Dhuhr (Zeval / İstiva). */
    val zevalStart: ZonedDateTime,
    val zevalEnd: ZonedDateTime,
    /** When the sun yellows until sunset (İsfirar-ı şems). */
    val isfirarStart: ZonedDateTime,
    val isfirarEnd: ZonedDateTime,
)

/** Optional voluntary (nafl) prayer windows. */
data class NaflTimes(
    val duhaStart: ZonedDateTime,
    val duhaEnd: ZonedDateTime,
    /** Awwabin: between Maghrib and Isha. */
    val awwabinStart: ZonedDateTime,
    val awwabinEnd: ZonedDateTime,
    val tahajjudStart: ZonedDateTime,
    val tahajjudEnd: ZonedDateTime,
)

/**
 * Derives the Hanafi makruh windows and the Duha/Tahajjud windows from a day's
 * prayer times, using the Turkish/Diyanet conventions:
 *  - sunrise makruh ≈ 45 min (sun a spear's length high) → also İşrak/Duha start
 *  - zeval makruh ≈ 20 min before Dhuhr
 *  - İsfirar ≈ 40 min before sunset
 */
object IslamicWindows {

    const val ISRAK_AFTER_SUNRISE_MIN = 45L
    const val ZEVAL_BEFORE_DHUHR_MIN = 20L
    const val ISFIRAR_BEFORE_MAGHRIB_MIN = 40L

    fun karaha(times: DailyPrayerTimes): KarahaTimes = KarahaTimes(
        sunriseStart = times.sunrise,
        sunriseEnd = times.sunrise.plusMinutes(ISRAK_AFTER_SUNRISE_MIN),
        zevalStart = times.dhuhr.minusMinutes(ZEVAL_BEFORE_DHUHR_MIN),
        zevalEnd = times.dhuhr,
        isfirarStart = times.maghrib.minusMinutes(ISFIRAR_BEFORE_MAGHRIB_MIN),
        isfirarEnd = times.maghrib,
    )

    /** [nextFajr] is the following day's Fajr, used for the last-third night. */
    fun nafl(times: DailyPrayerTimes, nextFajr: ZonedDateTime): NaflTimes {
        val duhaStart = times.sunrise.plusMinutes(ISRAK_AFTER_SUNRISE_MIN)
        val duhaEnd = times.dhuhr.minusMinutes(ZEVAL_BEFORE_DHUHR_MIN)
        // Last third of the night (sunset → next dawn).
        val nightSeconds = java.time.Duration.between(times.maghrib, nextFajr).seconds
        val tahajjudStart = times.maghrib.plusSeconds(nightSeconds * 2 / 3)
        return NaflTimes(
            duhaStart = duhaStart,
            duhaEnd = duhaEnd,
            awwabinStart = times.maghrib,
            awwabinEnd = times.isha,
            tahajjudStart = tahajjudStart,
            tahajjudEnd = nextFajr,
        )
    }
}
