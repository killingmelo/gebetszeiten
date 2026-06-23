package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTextTest {
    @Test fun hoursAndMinutes() { assertEquals("3 Std 26 Min", durationLabel(206)) }
    @Test fun fullHours() { assertEquals("2 Std", durationLabel(120)) }
    @Test fun minutesOnly() { assertEquals("55 Min", durationLabel(55)) }
    @Test fun zero() { assertEquals("0 Min", durationLabel(0)) }
}
