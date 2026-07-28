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

    // ---- Isha: true 17° angle (−7 ihtiyat) outside summer; takdir only in the
    // white-nights window. Calibrated against the official Diyanet 2026 year
    // table for Nürnberg (district 11024) — see YearCompareTest. ----
    @Test
    fun `winter isha uses the angle, summer isha uses takdir`() {
        // Winter: the 17° angle is reachable → official Yatsı 21 Dec 2026 = 18:03,
        // which is NOT Maghrib+80 (= 17:46). The old engine got this wrong.
        val winter = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 12, 21), berlin)
        assertCloseTo("winter isha", LocalTime.of(18, 3), winter.isha, 3)
        assertTrue(
            "winter isha must be the angle (later than Maghrib+80 takdir)",
            winter.isha.isAfter(winter.maghrib.plusMinutes(80)),
        )

        // High summer: white nights → takdir Maghrib + 80; official 7 Jun ≈ 22:45.
        val summer = DiyanetPrayerTimesCalculator.calculate(nuremberg, LocalDate.of(2026, 6, 7), berlin)
        assertEquals(summer.maghrib.plusMinutes(80), summer.isha)
    }

    // ---- Türkei: Yatsı ist Winkel +2 (Temkin), nicht −7 wie im Europa-Kalender ----
    // Referenz: amtliche Diyanet-Werte (namazvakitleri.diyanet.gov.tr) via Proxy,
    // Standort İSTANBUL (id 9541) bzw. SİNOP (id 9847), abgerufen 28.07.2026.
    // Vor dem Fix war Yatsı Istanbul konstant 8–9 min zu früh (22:03 statt 22:12).

    private val istanbul = GeoLocation(latitude = 41.0082, longitude = 28.9784)
    private val sinop = GeoLocation(latitude = 42.0231, longitude = 35.1531)
    private val istanbulZone: ZoneId = ZoneId.of("Europe/Istanbul")

    @Test
    fun `istanbul matches official Diyanet within 2 minutes`() {
        val t = DiyanetPrayerTimesCalculator.calculate(istanbul, LocalDate.of(2026, 7, 28), istanbulZone)
        assertCloseTo("fajr", LocalTime.of(4, 2), t.fajr, 2)
        assertCloseTo("sunrise", LocalTime.of(5, 49), t.sunrise, 2)
        assertCloseTo("dhuhr", LocalTime.of(13, 16), t.dhuhr, 2)
        assertCloseTo("asr", LocalTime.of(17, 11), t.asr, 2)
        assertCloseTo("maghrib", LocalTime.of(20, 32), t.maghrib, 2)
        assertCloseTo("isha", LocalTime.of(22, 12), t.isha, 2)
    }

    @Test
    fun `istanbul isha stays correct across the verified window`() {
        // 15.08.2026 amtlich: Yatsı 21:41 (id 9541).
        val t = DiyanetPrayerTimesCalculator.calculate(istanbul, LocalDate.of(2026, 8, 15), istanbulZone)
        assertCloseTo("isha", LocalTime.of(21, 41), t.isha, 2)
    }

    @Test
    fun `sinop isha matches official Diyanet within 2 minutes`() {
        // Nördlichste Großstadt der Türkei (42°) — amtlich 28.07.2026: Yatsı 21:53.
        val t = DiyanetPrayerTimesCalculator.calculate(sinop, LocalDate.of(2026, 7, 28), istanbulZone)
        assertCloseTo("isha", LocalTime.of(21, 53), t.isha, 2)
    }

    // ---- Regionale Isha-Gruppen: amtliche Yatsı-Werte 28.07.2026 (via Proxy) ----
    // +2-Gruppe (Winkel + 2 Temkin): GR, BG, AL, MK, ES, PT — wie die Türkei.
    // −7-Gruppe (Europa-Kalender): IT, BA, RO (wie DE/Nürnberg oben).

    private fun ishaOn280726(lat: Double, lng: Double, zone: String): ZonedDateTime =
        DiyanetPrayerTimesCalculator
            .calculate(GeoLocation(lat, lng), LocalDate.of(2026, 7, 28), ZoneId.of(zone))
            .isha

    @Test
    fun `plus-two group matches official isha within 2 minutes`() {
        assertCloseTo("Athen", LocalTime.of(22, 17), ishaOn280726(37.9838, 23.7275, "Europe/Athens"), 2)
        assertCloseTo("Lissabon", LocalTime.of(22, 32), ishaOn280726(38.7223, -9.1393, "Europe/Lisbon"), 2)
        assertCloseTo("Madrid", LocalTime.of(23, 19), ishaOn280726(40.4168, -3.7038, "Europe/Madrid"), 2)
        assertCloseTo("Sofia", LocalTime.of(22, 44), ishaOn280726(42.6977, 23.3219, "Europe/Sofia"), 2)
        assertCloseTo("Tirana", LocalTime.of(21, 50), ishaOn280726(41.3275, 19.8187, "Europe/Tirane"), 2)
        assertCloseTo("Skopje", LocalTime.of(21, 47), ishaOn280726(41.9973, 21.4280, "Europe/Skopje"), 2)
    }

    @Test
    fun `minus-seven group matches official isha within 2 minutes`() {
        assertCloseTo("Rom", LocalTime.of(22, 15), ishaOn280726(41.9028, 12.4964, "Europe/Rome"), 2)
        assertCloseTo("Sarajevo", LocalTime.of(22, 2), ishaOn280726(43.8563, 18.4131, "Europe/Sarajevo"), 2)
        assertCloseTo("Bukarest", LocalTime.of(22, 35), ishaOn280726(44.4268, 26.1025, "Europe/Bucharest"), 2)
        assertCloseTo("Mailand", LocalTime.of(22, 50), ishaOn280726(45.4642, 9.19, "Europe/Rome"), 2)
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
