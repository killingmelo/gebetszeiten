package de.gebetszeiten.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Was die Qibla-Ansicht zur Genauigkeit sagt: nichts, „genau“ oder „ungenau“ samt Acht. */
class CompassAccuracyTest {

    @Test fun `ohne Sensor gibt es keine Genauigkeit`() {
        assertNull(compassAccuracy(null))
    }

    @Test fun `vor der ersten Messung wird nichts behauptet`() {
        // Sonst blitzte beim Öffnen „genau“ auf, bevor der Sensor etwas sagt.
        assertEquals(CompassAccuracy.WAITING, compassAccuracy(DeviceHeading(0f, reliable = true, measured = false)))
        assertEquals(CompassAccuracy.WAITING, compassAccuracy(DeviceHeading(0f, reliable = false, measured = false)))
    }

    @Test fun `verlaesslicher Sensor heisst genau`() {
        assertEquals(CompassAccuracy.GOOD, compassAccuracy(DeviceHeading(120f, reliable = true)))
    }

    @Test fun `unverlaesslicher Sensor heisst ungenau`() {
        assertEquals(CompassAccuracy.POOR, compassAccuracy(DeviceHeading(120f, reliable = false)))
    }
}
