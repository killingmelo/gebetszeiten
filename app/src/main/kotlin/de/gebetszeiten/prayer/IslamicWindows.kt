package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Nafile
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
    // Null when the next day's Fajr is unavailable (e.g. last day of bundled
    // coverage): the last-third-of-the-night window can't be derived without
    // inventing a time, so it simply doesn't exist rather than being guessed.
    val tahajjudStart: ZonedDateTime?,
    val tahajjudEnd: ZonedDateTime?,
)

/**
 * Derives the Hanafi makruh windows and the Duha/Tahajjud windows from a day's
 * prayer times, using the Turkish/Diyanet conventions:
 *  - sunrise makruh ≈ 45 min (sun a spear's length high) → also İşrak/Duha start
 *  - zeval makruh ≈ 20 min before Dhuhr
 *  - İsfirar ≈ 40 min before sunset
 */
object IslamicWindows {

    // Single source of truth lives in core (shared with the watch).
    const val ISRAK_AFTER_SUNRISE_MIN = de.gebetszeiten.core.prayertimes.Karaha.ISRAK_AFTER_SUNRISE_MIN
    const val ZEVAL_BEFORE_DHUHR_MIN = de.gebetszeiten.core.prayertimes.Karaha.ZEVAL_BEFORE_DHUHR_MIN
    const val ISFIRAR_BEFORE_MAGHRIB_MIN = de.gebetszeiten.core.prayertimes.Karaha.ISFIRAR_BEFORE_MAGHRIB_MIN

    fun karaha(times: DailyPrayerTimes): KarahaTimes = KarahaTimes(
        sunriseStart = times.sunrise,
        sunriseEnd = times.sunrise.plusMinutes(ISRAK_AFTER_SUNRISE_MIN),
        zevalStart = times.dhuhr.minusMinutes(ZEVAL_BEFORE_DHUHR_MIN),
        zevalEnd = times.dhuhr,
        isfirarStart = times.maghrib.minusMinutes(ISFIRAR_BEFORE_MAGHRIB_MIN),
        isfirarEnd = times.maghrib,
    )

    /** [nextFajr] is the following day's Fajr, used for the last-third night.
     *  Pass null when it's unavailable (e.g. the last day of bundled coverage) -
     *  the Tahajjud window is then left out rather than guessed. */
    // Rechnung im Kern (geteilt mit dem Desktop), hier nur umverpackt.
    fun nafl(times: DailyPrayerTimes, nextFajr: ZonedDateTime?): NaflTimes {
        val duha = Nafile.duha(times)
        val awwabin = Nafile.awwabin(times)
        val tahajjud = Nafile.tahajjud(times, nextFajr)
        return NaflTimes(
            duhaStart = duha.start,
            duhaEnd = duha.end,
            awwabinStart = awwabin.start,
            awwabinEnd = awwabin.end,
            tahajjudStart = tahajjud?.start,
            tahajjudEnd = tahajjud?.end,
        )
    }
}
