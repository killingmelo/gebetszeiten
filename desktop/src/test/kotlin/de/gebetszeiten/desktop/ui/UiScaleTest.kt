package de.gebetszeiten.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {

    @Test
    fun fontSizeScalesAndRounds() {
        assertEquals(12, scaledFontSize(12, 1.0))
        assertEquals(15, scaledFontSize(12, 1.25))
        assertEquals(18, scaledFontSize(12, 1.5))
        assertEquals(24, scaledFontSize(12, 2.0))
    }

    @Test
    fun neverBelowOne() {
        assertEquals(1, scaledFontSize(1, 0.1))
    }

    @Test
    fun pixelsScaleLikeFonts() {
        assertEquals(10, scaledPx(8, 1.25))
        assertEquals(0, scaledPx(0, 1.5))
    }

    @Test
    fun trayIconPicksLargerVariantFromOnePointFive() {
        assertEquals(16, trayIconSize(1.0))
        assertEquals(16, trayIconSize(1.25))
        assertEquals(32, trayIconSize(1.5))
        assertEquals(32, trayIconSize(2.0))
    }
}
