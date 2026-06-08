package de.gebetszeiten.core.prayertimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Calibration tests for the prayer-time engine against verified Diyanet
 * (Türkiye) reference values for Nürnberg.
 *
 * Summer reference is the OFFICIAL Diyanet site
 * (namazvakitleri.diyanet.gov.tr, district 11024) for 7 June 2026:
 *   İmsak 03:35 · Güneş 05:04 · Öğle 13:20 · İkindi 17:36 · Akşam 21:25 · Yatsı 22:45
 * Winter solar/Fajr reference is the Diyanet-method angle calculation for
 * 21 December 2026 (the 18°/17° angles are reachable in winter):
 *   Fajr 06:11 · Sunrise 08:02 · Dhuhr 12:19 · Asr 14:06 · Maghrib 16:26 · Isha (angle) 18:10
 */
class DiyanetPrayerTimesCalculatorTest {

    private val nuremberg = GeoLocation(latitude = 49.4521, longitude = 11.0767)
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun assertCloseTo(label: String, expected: LocalTime, actual: ZonedDateTime, toleranceMin: Long) {
        val actualMinutes = actual.hour * 60L + actual.minute
        val expectedMinutes = expected.hour * 60L + expected.minute
        val diff = abs(actualMinutes - expectedMinutes)
        assertTrue(
            "$label: expected ~$expected (±${toleranceMin}m) but was ${actual.toLocalTime()} (off by ${diff}m)",
            diff <= toleranceMin,
        )
    }

    // ---- Summer: solar prayers (method-independent), tight tolerance ----

    @Test
    fun `summer solar prayers match official Diyanet within 2 minutes`() {
        val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 6, 7), berlin)

        assertCloseTo("sunrise", LocalTime.of(5, 4), t.sunrise, 2)
        assertCloseTo("dhuhr", LocalTime.of(13, 20), t.dhuhr, 2)
        assertCloseTo("asr", LocalTime.of(17, 36), t.asr, 2)
        assertCloseTo("maghrib", LocalTime.of(21, 25), t.maghrib, 2)
    }

    // ---- Summer high-latitude takdir rule: Fajr = sunrise-90, Isha = maghrib+80 ----

    @Test
    fun `summer fajr and isha match official Diyanet takdir rule`() {
        val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 6, 7), berlin)

        assertCloseTo("fajr", LocalTime.of(3, 35), t.fajr, 2)
        assertCloseTo("isha", LocalTime.of(22, 45), t.isha, 2)
    }

    // ---- Fajr takdir is active across the whole summer half (from 1 June) ----
    // Official Diyanet (district 11024): 1 June İmsak 03:37, Güneş 05:07.
    // Regression guard: a too-narrow angle gate previously gave ~02:50 here.
    @Test
    fun `early june fajr already uses takdir`() {
        val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 6, 1), berlin)
        assertCloseTo("fajr", LocalTime.of(3, 37), t.fajr, 2)
    }

    // ---- Shoulder seasons (outside summer half): solar + Fajr use true angle ----
    // Reference: aladhan method=13 (Diyanet) angle calculation.
    @Test
    fun `spring solar and fajr match angle calculation`() {
        val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 3, 15), berlin)
        assertCloseTo("fajr", LocalTime.of(4, 42), t.fajr, 2)
        assertCloseTo("sunrise", LocalTime.of(6, 23), t.sunrise, 2)
        assertCloseTo("dhuhr", LocalTime.of(12, 30), t.dhuhr, 2)
        assertCloseTo("asr", LocalTime.of(15, 41), t.asr, 2)
        assertCloseTo("maghrib", LocalTime.of(18, 28), t.maghrib, 2)
    }

    @Test
    fun `autumn solar and fajr match angle calculation`() {
        val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 10, 15), berlin)
        assertCloseTo("fajr", LocalTime.of(5, 50), t.fajr, 2)
        assertCloseTo("sunrise", LocalTime.of(7, 30), t.sunrise, 2)
        assertCloseTo("dhuhr", LocalTime.of(13, 6), t.dhuhr, 2)
        assertCloseTo("asr", LocalTime.of(15, 57), t.asr, 2)
        assertCloseTo("maghrib", LocalTime.of(18, 32), t.maghrib, 2)
    }

    // ---- Winter: angle is reachable, so solar + Fajr use the true 18° angle ----

    @Test
    fun `winter solar and fajr match Diyanet angle calculation within 2 minutes`() {
        val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 12, 21), berlin)

        assertCloseTo("fajr", LocalTime.of(6, 11), t.fajr, 2)
        assertCloseTo("sunrise", LocalTime.of(8, 2), t.sunrise, 2)
        assertCloseTo("dhuhr", LocalTime.of(12, 19), t.dhuhr, 2)
        assertCloseTo("asr", LocalTime.of(14, 6), t.asr, 2)
        assertCloseTo("maghrib", LocalTime.of(16, 26), t.maghrib, 2)
    }

    // ---- Isha takdir applies year-round above 45° (per Din İşleri Yüksek Kurulu) ----
    // NOTE: winter Isha = Maghrib + 80 min is taken from the Kurul's "entire year"
    // ruling; an official winter Isha value could not be fetched to triple-check.
    @Test
    fun `isha above 45 degrees is maghrib plus 80 minutes year round`() {
        val winter = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 12, 21), berlin)
        assertEquals(winter.maghrib.plusMinutes(80), winter.isha)

        val summer = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 6, 7), berlin)
        assertEquals(summer.maghrib.plusMinutes(80), summer.isha)
    }

    // ---- Ordering invariant ----

    @Test
    fun `prayers are in chronological order`() {
        val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 6, 7), berlin)
        assertTrue("fajr < sunrise", t.fajr.isBefore(t.sunrise))
        assertTrue("sunrise < dhuhr", t.sunrise.isBefore(t.dhuhr))
        assertTrue("dhuhr < asr", t.dhuhr.isBefore(t.asr))
        assertTrue("asr < maghrib", t.asr.isBefore(t.maghrib))
        assertTrue("maghrib < isha", t.maghrib.isBefore(t.isha))
    }
}
