package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
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
}
