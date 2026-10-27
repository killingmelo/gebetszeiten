package de.gebetszeiten.core.prayertimes

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Freiwillige Gebetsfenster aus den sechs Zeiten, geteilt von Handy und
 * Desktop (Diyanet-Konventionen wie [Karaha]):
 *  - Kuşluk (Duha): Ende der İşrak-Karaha bis Beginn der Zeval-Karaha
 *  - Evvâbîn: Maghrib bis Isha
 *  - Teheccüd: letztes Drittel der Nacht (Maghrib bis Fajr des Folgetags)
 */
object Nafile {

    data class Window(val start: ZonedDateTime, val end: ZonedDateTime)

    fun duha(times: DailyPrayerTimes): Window = Window(
        times.sunrise.plusMinutes(Karaha.ISRAK_AFTER_SUNRISE_MIN),
        times.dhuhr.minusMinutes(Karaha.ZEVAL_BEFORE_DHUHR_MIN),
    )

    fun awwabin(times: DailyPrayerTimes): Window = Window(times.maghrib, times.isha)

    /** Null ohne Fajr des Folgetags: das Fenster wird dann nicht geraten. */
    fun tahajjud(times: DailyPrayerTimes, nextFajr: ZonedDateTime?): Window? = nextFajr?.let { fajr ->
        val nightSeconds = Duration.between(times.maghrib, fajr).seconds
        Window(times.maghrib.plusSeconds(nightSeconds * 2 / 3), fajr)
    }
}
