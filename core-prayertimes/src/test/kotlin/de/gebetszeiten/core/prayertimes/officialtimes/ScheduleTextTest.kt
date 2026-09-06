package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleTextTest {

    private val day = LocalDate.of(2026, 7, 30)
    private val times = SixTimes(
        fajr = LocalTime.of(3, 54), sunrise = LocalTime.of(5, 38),
        dhuhr = LocalTime.of(13, 27), asr = LocalTime.of(17, 34),
        maghrib = LocalTime.of(21, 7), isha = LocalTime.of(22, 36),
    )

    @Test
    fun `round-trip erhaelt alle Tage sortiert`() {
        val schedule = mapOf(day.plusDays(1) to times, day to times)
        val text = ScheduleText.serialize(schedule)
        assertEquals(schedule, ScheduleText.parse(text))
        assertEquals(listOf(day, day.plusDays(1)), text.lines().map { LocalDate.parse(it.substringBefore(" ")) })
    }

    @Test
    fun `serialisiert im bisherigen Cache-Zeilenformat`() {
        assertEquals(
            "2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36",
            ScheduleText.serialize(mapOf(day to times)),
        )
    }

    @Test
    fun `leere Map ergibt leeren Text und zurueck`() {
        assertEquals("", ScheduleText.serialize(emptyMap()))
        assertEquals(emptyMap<LocalDate, SixTimes>(), ScheduleText.parse(""))
    }

    @Test
    fun `kaputte Zeilen werden uebersprungen`() {
        val text = "kaputt\n2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36\n2026-07-31 xx"
        assertEquals(mapOf(day to times), ScheduleText.parse(text))
    }

    @Test
    fun `parseDay liefert fuer jedes Datum dasselbe wie parse(text)(date), und null wenn nicht enthalten`() {
        val start = LocalDate.of(2026, 1, 1)
        val schedule = (0 until 366).associate { start.plusDays(it.toLong()) to times.copy(fajr = times.fajr.plusMinutes(it.toLong() % 30)) }
        val text = ScheduleText.serialize(schedule)
        val parsed = ScheduleText.parse(text)

        for (date in schedule.keys) {
            assertEquals(parsed[date], ScheduleText.parseDay(text, date))
        }

        assertNull(ScheduleText.parseDay(text, start.minusDays(1)))
        assertNull(parsed[start.minusDays(1)])
    }

    @Test
    fun `parseDay liefert die letzte gueltige Zeile, wenn eine spaetere Zeile zum selben Datum kaputt ist`() {
        val kaputtTag = LocalDate.of(2026, 9, 6)
        val text = "2026-09-06 04:54 06:23 13:02 16:39 19:31 20:53\n2026-09-06 kaputt"
        val expected = SixTimes(
            fajr = LocalTime.of(4, 54), sunrise = LocalTime.of(6, 23),
            dhuhr = LocalTime.of(13, 2), asr = LocalTime.of(16, 39),
            maghrib = LocalTime.of(19, 31), isha = LocalTime.of(20, 53),
        )

        assertEquals(expected, ScheduleText.parse(text)[kaputtTag])
        assertEquals(expected, ScheduleText.parseDay(text, kaputtTag))
    }
}
