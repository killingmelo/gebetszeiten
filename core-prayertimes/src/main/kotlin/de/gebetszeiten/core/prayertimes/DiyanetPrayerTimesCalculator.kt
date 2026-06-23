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
 *    above 45°. Calibrated against the official Diyanet 2026 year table for
 *    Nürnberg (see resources/diyanet_nurnberg_2026.csv + YearCompareTest):
 *    - Normally Fajr/Isha use the true 18°/17° angle (Isha trimmed −7 min, which
 *      matches the published Yatsı to ±1–2 min outside summer).
 *    - In the summer "white nights" window — when the sun's deepest nightly
 *      depression stays below ~24.75° (Fajr) / ~24.0° (Isha) — the angle is
 *      unreliable, so takdir applies: Fajr = Sunrise − 90, Isha = Maghrib + 80.
 *    Below 45° the true angle times are always used.
 *    Result vs official 2026 (Nürnberg): mean error Fajr ~2.4 / Isha ~2.8 min;
 *    residual is the late-April / early-August transition weeks (aqrab al-ayyâm).
 */
object DiyanetPrayerTimesCalculator {

    private const val HIGH_LATITUDE_THRESHOLD = 45.0
    private const val ISHA_TAKDIR_MINUTES = 80L
    private const val FAJR_TAKDIR_MINUTES = 90L

    // Sun must dip at least this far below the horizon at solar midnight for the
    // angle to be used; below it (white nights) takdir applies. Calibrated.
    private const val FAJR_TAKDIR_DEPRESSION = 24.75
    private const val ISHA_TAKDIR_DEPRESSION = 24.0

    // Diyanet method (ihtiyat) adjustments, in minutes.
    private const val ADJ_SUNRISE = -7
    private const val ADJ_DHUHR = 5
    private const val ADJ_ASR = 4
    private const val ADJ_MAGHRIB = 7
    private const val ADJ_ISHA = -7

    fun calculate(location: GeoLocation, date: LocalDate, zone: ZoneId): DailyPrayerTimes {
        val rawBase = adhanTimes(location.latitude, location.longitude, date)

        // Polar day / night (above the Arctic/Antarctic circle around the
        // solstices): the sun never crosses the horizon, so adhan returns NULL
        // sunrise/sunset/twilight times. Rather than crash (NPE), fall back to
        // the nearest usable latitude (≈45°, "aqrab al-bilad" — nearest
        // locality) for the horizon-dependent times. Solar noon (dhuhr) is
        // longitude-driven, so it stays effectively correct; fajr/asr/isha at
        // these latitudes are an approximation by necessity.
        val polar = rawBase.sunrise == null || rawBase.maghrib == null ||
            rawBase.dhuhr == null || rawBase.asr == null
        val effLat = if (polar) sign(location.latitude) * HIGH_LATITUDE_THRESHOLD else location.latitude
        val base = if (polar) adhanTimes(effLat, location.longitude, date) else rawBase

        val sunrise = base.sunrise.toZdt(zone)
        val dhuhr = base.dhuhr.toZdt(zone)
        val asr = base.asr.toZdt(zone)
        val maghrib = base.maghrib.toZdt(zone)

        val highLatitude = kotlin.math.abs(effLat) >= HIGH_LATITUDE_THRESHOLD
        val depression = maxDepressionDegrees(effLat, date)

        val fajr = if (highLatitude && depression < FAJR_TAKDIR_DEPRESSION) {
            sunrise.minusMinutes(FAJR_TAKDIR_MINUTES)
        } else {
            base.fajr.toZdt(zone)
        }

        val isha = if (highLatitude && depression < ISHA_TAKDIR_DEPRESSION) {
            maghrib.plusMinutes(ISHA_TAKDIR_MINUTES)
        } else {
            base.isha.toZdt(zone) // already trimmed −7 via ADJ_ISHA
        }

        return DailyPrayerTimes(date, zone, fajr, sunrise, dhuhr, asr, maghrib, isha)
    }

    private fun adhanTimes(latitude: Double, longitude: Double, date: LocalDate): PrayerTimes {
        val params: CalculationParameters = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters.apply {
            madhab = Madhab.SHAFI
            highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE
            // PrayerAdjustments(fajr, sunrise, dhuhr, asr, maghrib, isha)
            methodAdjustments = PrayerAdjustments(0, ADJ_SUNRISE, ADJ_DHUHR, ADJ_ASR, ADJ_MAGHRIB, ADJ_ISHA)
        }
        val coordinates = Coordinates(latitude, longitude)
        val components = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        return PrayerTimes(coordinates, components, params)
    }

    /** The sun's maximum depression below the horizon at solar midnight (deg):
     *  (90 − |lat|) − declination. Small in the local high summer (short nights). */
    private fun maxDepressionDegrees(latitude: Double, date: LocalDate): Double =
        (90.0 - kotlin.math.abs(latitude)) - solarDeclinationDegrees(date) * sign(latitude)

    private fun sign(x: Double): Double = if (x >= 0.0) 1.0 else -1.0

    /** Approximate solar declination in degrees for the given date. */
    private fun solarDeclinationDegrees(date: LocalDate): Double {
        val angle = Math.toRadians(360.0 / 365.0 * (date.dayOfYear + 10))
        return -23.44 * Math.cos(angle)
    }

    private fun Date.toZdt(zone: ZoneId): ZonedDateTime = toInstant().atZone(zone)
}
