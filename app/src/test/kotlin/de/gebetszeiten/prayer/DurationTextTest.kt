package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTextTest {
    @Test fun hoursAndMinutes() { assertEquals("3 Std 26 Min", durationLabel(206)) }
    @Test fun fullHours() { assertEquals("2 Std", durationLabel(120)) }
    @Test fun minutesOnly() { assertEquals("55 Min", durationLabel(55)) }
    // Unter einer Minute nie „0 Min“: vor einer Makrūh-Zeit stand sonst „in 0 Min“.
    @Test fun underOneMinute() { assertEquals("weniger als 1 Min", durationLabel(0)) }
    @Test fun negativeIsUnderOneMinute() { assertEquals("weniger als 1 Min", durationLabel(-3)) }
    @Test fun oneMinute() { assertEquals("1 Min", durationLabel(1)) }
}
