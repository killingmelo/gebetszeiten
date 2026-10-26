package de.gebetszeiten.desktop.display

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.display.BOUNDARY_SETTLE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DisplayStateTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 6, 10)

    private fun at(d: LocalDate, h: Int, m: Int, s: Int = 0): ZonedDateTime =
        ZonedDateTime.of(d, LocalTime.of(h, m, s), zone)

    private fun times(
        d: LocalDate = day,
        fajr: ZonedDateTime = at(d, 5, 0),
        sunrise: ZonedDateTime = at(d, 6, 30),
        dhuhr: ZonedDateTime = at(d, 13, 0),
        asr: ZonedDateTime = at(d, 15, 30),
        maghrib: ZonedDateTime = at(d, 18, 0),
        isha: ZonedDateTime = at(d, 19, 30),
    ) = DailyPrayerTimes(d, zone, fajr, sunrise, dhuhr, asr, maghrib, isha)

    @Test
    fun asrIn27Minutes() {
        val s = displayState(at(day, 15, 3), times(), null, showKaraha = true)
        assertEquals("Asr", s.line1)
        assertEquals("noch 20+ Min", s.line2)
        assertEquals(Tone.NORMAL, s.tone)
        assertEquals("Noch 20+ Min bis Asr", s.tooltip)
    }

    @Test
    fun exactShowsMinutes() {
        val s = displayState(at(day, 15, 3), times(), null, showKaraha = true, exact = true)
        assertEquals("Asr", s.line1)
        assertEquals("noch 27 Min", s.line2)
        assertEquals("Noch 27 Min bis Asr", s.tooltip)
    }

    @Test
    fun exactShowsHoursAndMinutes() {
        val s = displayState(at(day, 10, 46), times(), null, showKaraha = false, exact = true)
        assertEquals("noch 2 Std 14 Min", s.line2)
    }

    @Test
    fun exactFullHourHasNoMinutes() {
        val s = displayState(at(day, 11, 0), times(), null, showKaraha = false, exact = true)
        assertEquals("noch 2 Std", s.line2)
    }

    @Test
    fun exactRoundsUpAndChangesAtNextMinute() {
        // 26 Min 40 s Rest -> „27 Min" bis 15:04:00, dann „26 Min".
        val s = displayState(at(day, 15, 3, 20), times(), null, showKaraha = true, exact = true)
        assertEquals("noch 27 Min", s.line2)
        assertEquals(at(day, 15, 4).plusNanos(BOUNDARY_SETTLE_MS * 1_000_000), s.nextChangeAt)
    }

    @Test
    fun exactLastSecondsSayOneMinute() {
        val s = displayState(at(day, 15, 29, 30), times(), null, showKaraha = true, exact = true)
        assertEquals("noch 1 Min", s.line2)
        assertEquals("Noch 1 Min bis Asr", s.tooltip)
        assertEquals(at(day, 15, 30), s.nextChangeAt)
    }

    @Test
    fun exactKarahaStillReplacesLine2() {
        val t = times(dhuhr = at(day, 13, 21))
        val s = displayState(at(day, 12, 50), t, null, showKaraha = true, exact = true)
        assertEquals("⚠️ Karaha ab 13:01", s.line2)
        assertEquals("Noch 31 Min bis Dhuhr · ⚠️ Karaha ab 13:01", s.tooltip)
    }

    @Test
    fun urgentLastTenMinutes() {
        val s = displayState(at(day, 15, 23), times(), null, showKaraha = true)
        assertEquals("noch 7 Min", s.line2)
        assertEquals(Tone.URGENT, s.tone)
        assertEquals("Noch 7 Min bis Asr", s.tooltip)
    }

    @Test
    fun lastMinuteSaysJetzt() {
        val s = displayState(at(day, 15, 29, 30), times(), null, showKaraha = true)
        assertEquals("jetzt", s.line2)
        assertEquals(Tone.URGENT, s.tone)
        assertEquals("Asr jetzt", s.tooltip)
    }

    @Test
    fun zevalSoonReplacesLine2() {
        // Dhuhr 13:21 -> Zeval 13:01-13:21, Warnung ab 12:46.
        val t = times(dhuhr = at(day, 13, 21))
        val s = displayState(at(day, 12, 50), t, null, showKaraha = true)
        assertEquals("Dhuhr", s.line1)
        assertEquals("⚠️ Karaha ab 13:01", s.line2)
        assertEquals(Tone.KARAHA_SOON, s.tone)
        assertEquals("Noch 30+ Min bis Dhuhr · ⚠️ Karaha ab 13:01", s.tooltip)
    }

    @Test
    fun isfirarActive() {
        val s = displayState(at(day, 17, 30), times(), null, showKaraha = true)
        assertEquals("Maghrib", s.line1)
        assertEquals("⚠️ Karaha bis 18:00", s.line2)
        assertEquals(Tone.KARAHA_ACTIVE, s.tone)
    }

    @Test
    fun karahaHiddenWhenSwitchedOff() {
        val s = displayState(at(day, 17, 30), times(), null, showKaraha = false)
        assertEquals("noch 30+ Min", s.line2)
        assertEquals(Tone.NORMAL, s.tone)
        assertEquals("Noch 30+ Min bis Maghrib", s.tooltip)
    }

    @Test
    fun afterIshaShowsTomorrowsFajr() {
        val tomorrow = day.plusDays(1)
        val s = displayState(at(day, 22, 0), times(), times(d = tomorrow), showKaraha = true)
        assertEquals("Fajr", s.line1)
        assertEquals("noch 7+ Std", s.line2)
        assertEquals(Tone.NORMAL, s.tone)
    }

    @Test
    fun afterIshaWithoutTomorrowIsNoTimes() {
        val s = displayState(at(day, 22, 0), times(), null, showKaraha = true)
        assertEquals(Tone.NO_TIMES, s.tone)
        assertEquals("Keine Zeiten", s.line1)
    }

    @Test
    fun noTimes() {
        val s = displayState(at(day, 12, 0), null, null, showKaraha = true)
        assertEquals("Keine Zeiten", s.line1)
        assertEquals("", s.line2)
        assertEquals(Tone.NO_TIMES, s.tone)
        assertEquals("Keine amtlichen Zeiten für diesen Ort", s.tooltip)
        assertNull(s.nextChangeAt)
    }

    @Test
    fun nextChangeIsNextStepBoundary() {
        val s = displayState(at(day, 15, 3), times(), null, showKaraha = true)
        val expected = at(day, 15, 10).plusNanos(BOUNDARY_SETTLE_MS * 1_000_000L)
        assertEquals(expected, s.nextChangeAt)
    }

    @Test
    fun nextChangeIsKarahaBoundaryWhenEarlier() {
        // Isfirar-Warnung beginnt 17:20 - 15 min = 17:05; Asr liegt spaet.
        val t = times(asr = at(day, 20, 0), isha = at(day, 21, 0))
        val s = displayState(at(day, 17, 4, 50), t, null, showKaraha = true)
        assertEquals(at(day, 17, 5), s.nextChangeAt)
    }

    @Test
    fun dstDayRemainingIsWallClockCorrect() {
        // 25.10.2026: um 03:00 wird auf 02:00 zurueckgestellt. 01:30 -> 06:00 sind real 5 h 30 min.
        val d = LocalDate.of(2026, 10, 25)
        val s = displayState(at(d, 1, 30), times(d = d, fajr = at(d, 6, 0)), null, showKaraha = true)
        assertEquals("Fajr", s.line1)
        assertEquals("noch 5+ Std", s.line2)
    }
}
