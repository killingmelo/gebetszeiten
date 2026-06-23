package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class KarahaCountdownTest {
    private val z = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2026, 6, 9, h, m, 0, 0, z)
    private val start = t(20, 47)
    private val end = t(21, 27)

    @Test fun activeShowsRemaining() {
        val s = KarahaCountdown.state(t(21, 15), start, end)!!
        assertEquals(true, s.active)
        assertEquals("noch 12 Min", s.text)
    }

    @Test fun imminentWithinLeadShowsCountdown() {
        val s = KarahaCountdown.state(t(20, 29), start, end)!!
        assertEquals(false, s.active)
        assertEquals("in 18 Min", s.text)
    }

    @Test fun beforeLeadIsNull() {
        assertNull(KarahaCountdown.state(t(19, 0), start, end))
    }

    @Test fun afterEndIsNull() {
        assertNull(KarahaCountdown.state(t(21, 27), start, end))
    }

    @Test fun atStartIsActive() {
        val s = KarahaCountdown.state(start, start, end)!!
        assertEquals(true, s.active)
        assertEquals("noch 40 Min", s.text)
    }
}
