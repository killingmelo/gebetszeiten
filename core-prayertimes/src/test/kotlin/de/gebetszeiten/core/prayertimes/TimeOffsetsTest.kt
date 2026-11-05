package de.gebetszeiten.core.prayertimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeOffsetsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 5, h, m, 0, 0, zone)
    private val times = DailyPrayerTimes(
        date = LocalDate.of(2026, 11, 5), zone = zone,
        fajr = t(5, 21), sunrise = t(7, 3), dhuhr = t(12, 4), asr = t(14, 27), maghrib = t(16, 56), isha = t(18, 25),
    )

    @Test fun `ohne Verschiebung bleiben die amtlichen Zeiten`() {
        assertTrue(TimeOffsets.NONE.isZero)
        assertSame(times, TimeOffsets.NONE.applyTo(times))
    }

    @Test fun `jede Zeit wird um ihre Minuten verschoben`() {
        val o = TimeOffsets(fajr = -5, sunrise = 2, dhuhr = 10, asr = 0, maghrib = 3, isha = -60)
        val s = o.applyTo(times)
        assertEquals(t(5, 16), s.fajr)
        assertEquals(t(7, 5), s.sunrise)
        assertEquals(t(12, 14), s.dhuhr)
        assertEquals(t(14, 27), s.asr)
        assertEquals(t(16, 59), s.maghrib)
        assertEquals(t(17, 25), s.isha)
        assertFalse(o.isZero)
    }

    @Test fun `Speicherform hin und zurueck`() {
        val o = TimeOffsets(1, -2, 3, -4, 5, -60)
        assertEquals("1,-2,3,-4,5,-60", o.encode())
        assertEquals(o, TimeOffsets.decode(o.encode()))
    }

    @Test fun `Unlesbares ergibt keine Verschiebung, zu Grosses wird begrenzt`() {
        assertEquals(TimeOffsets.NONE, TimeOffsets.decode(null))
        assertEquals(TimeOffsets.NONE, TimeOffsets.decode("1,2,3"))
        assertEquals(TimeOffsets.NONE, TimeOffsets.decode("a,b,c,d,e,f"))
        assertEquals(TimeOffsets(fajr = 60, isha = -60), TimeOffsets.decode("90,0,0,0,0,-200"))
    }

    @Test fun `with begrenzt auf die Spanne`() {
        assertEquals(60, TimeOffsets.NONE.with(Prayer.ASR, 75).asr)
        assertEquals(-60, TimeOffsets.NONE.with(Prayer.SUNRISE, -61).sunrise)
        assertEquals(7, TimeOffsets.NONE.with(Prayer.MAGHRIB, 7).of(Prayer.MAGHRIB))
    }
}
