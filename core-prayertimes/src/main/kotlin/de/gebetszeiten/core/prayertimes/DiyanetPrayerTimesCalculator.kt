package de.gebetszeiten.core.prayertimes

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerAdjustments
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

/**
 * Offline prayer-time engine reproducing the official Diyanet (Türkiye)
 * convention used in its European calendars.
 *
 * Verified against the official site (namazvakitleri.diyanet.gov.tr) for
 * Nürnberg — see [DiyanetPrayerTimesCalculatorTest].
 *
 * ## How Diyanet differs from a plain angle calculation
 *
 * 1. **Twilight angles** — Fajr 18°, Isha 17° (same as Muslim World League).
 * 2. **Asr** — standard (Shafiʿi) shadow factor.
 * 3. **Method (ihtiyat) adjustments** — fixed minute offsets applied to the
 *    raw astronomical times: sunrise −7, dhuhr +5, asr +4, maghrib +7. These
 *    bring the four solar prayers onto the published values within ±1 min.
 * 4. **High-latitude "takdir" rule** (Din İşleri Yüksek Kurulu) for places
 *    above 45°: Isha is fixed at Maghrib + 80 min year round; in the summer
 *    "white nights" period (when the sun never reaches 18° below the horizon)
 *    Fajr is fixed at Sunrise − 90 min. Below 45° the true angle times are used.
 */
object DiyanetPrayerTimesCalculator {

    private const val HIGH_LATITUDE_THRESHOLD = 45.0
    private const val ISHA_TAKDIR_MINUTES = 80L
    private const val FAJR_TAKDIR_MINUTES = 90L

    // Diyanet method (ihtiyat) adjustments, in minutes.
    private const val ADJ_SUNRISE = -7
    private const val ADJ_DHUHR = 5
    private const val ADJ_ASR = 4
    private const val ADJ_MAGHRIB = 7

    fun calculate(location: GeoLocation, date: LocalDate, zone: ZoneId): DailyPrayerTimes {
        val base = adhanTimes(location.latitude, location.longitude, date)

        val sunrise = base.sunrise.toZdt(zone)
        val dhuhr = base.dhuhr.toZdt(zone)
        val asr = base.asr.toZdt(zone)
        val maghrib = base.maghrib.toZdt(zone)

        val highLatitude = kotlin.math.abs(location.latitude) >= HIGH_LATITUDE_THRESHOLD

        // Diyanet applies the Fajr (İmsak) takdir only in the local summer half
        // year (equinox to equinox) — the Kurul's "March–September" for the
        // northern hemisphere — when short nights make the 18° angle unreliable.
        val fajr = if (highLatitude && isSummerHalf(location.latitude, date)) {
            sunrise.minusMinutes(FAJR_TAKDIR_MINUTES)
        } else {
            base.fajr.toZdt(zone)
        }

        val isha = if (highLatitude) {
            maghrib.plusMinutes(ISHA_TAKDIR_MINUTES)
        } else {
            base.isha.toZdt(zone)
        }

        return DailyPrayerTimes(date, zone, fajr, sunrise, dhuhr, asr, maghrib, isha)
    }

    private fun adhanTimes(latitude: Double, longitude: Double, date: LocalDate): PrayerTimes {
        val params: CalculationParameters = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters.apply {
            madhab = Madhab.SHAFI
            highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE
            // PrayerAdjustments(fajr, sunrise, dhuhr, asr, maghrib, isha)
            methodAdjustments = PrayerAdjustments(0, ADJ_SUNRISE, ADJ_DHUHR, ADJ_ASR, ADJ_MAGHRIB, 0)
        }
        val coordinates = Coordinates(latitude, longitude)
        val components = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        return PrayerTimes(coordinates, components, params)
    }

    /** True in the location's summer half-year (sun on the same side as the
     *  observer's hemisphere → short nights), i.e. between the spring and autumn
     *  equinoxes. Hemisphere-general: latitude and declination share a sign. */
    private fun isSummerHalf(latitude: Double, date: LocalDate): Boolean {
        val declination = solarDeclinationDegrees(date)
        return latitude * declination > 0.0
    }

    /** Approximate solar declination in degrees for the given date. */
    private fun solarDeclinationDegrees(date: LocalDate): Double {
        val angle = Math.toRadians(360.0 / 365.0 * (date.dayOfYear + 10))
        return -23.44 * Math.cos(angle)
    }

    private fun Date.toZdt(zone: ZoneId): ZonedDateTime = toInstant().atZone(zone)
}
