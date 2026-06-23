package de.gebetszeiten.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test fun zeroScaleDisablesAnimations() { assertEquals(false, animationsEnabled(0f)) }
    @Test fun normalScaleEnablesAnimations() { assertEquals(true, animationsEnabled(1f)) }
    @Test fun halfScaleStillEnabled() { assertEquals(true, animationsEnabled(0.5f)) }
}
