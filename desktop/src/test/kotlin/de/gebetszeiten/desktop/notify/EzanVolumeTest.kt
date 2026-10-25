package de.gebetszeiten.desktop.notify

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EzanVolumeTest {

    @Test
    fun factorEndpointsAndClamp() {
        assertEquals(0.0, volumeFactor(0), 0.0)
        assertEquals(1.0, volumeFactor(100), 0.0)
        assertEquals(0.04, volumeFactor(20), 1e-9)
        assertEquals(1.0, volumeFactor(250), 0.0)
        assertEquals(0.0, volumeFactor(-5), 0.0)
    }

    @Test
    fun zeroPercentIsSilent() {
        val s = shortArrayOf(1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)
        scaleSamples(s, 0, s.size, volumeFactor(0))
        assertArrayEquals(shortArrayOf(0, 0, 0, 0), s)
    }

    @Test
    fun scalesOnlyRequestedRange() {
        val s = shortArrayOf(100, 200, 300, 400)
        scaleSamples(s, 1, 2, 0.5)
        assertArrayEquals(shortArrayOf(100, 100, 150, 400), s)
    }

    @Test
    fun clampsToShortRange() {
        val s = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE)
        scaleSamples(s, 0, 2, 2.0)
        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), s)
    }
}
