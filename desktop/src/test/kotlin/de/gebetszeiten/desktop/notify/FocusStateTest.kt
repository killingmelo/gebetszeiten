package de.gebetszeiten.desktop.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusStateTest {

    @Test
    fun quietStates() {
        // QUNS_BUSY, D3D_FULL_SCREEN, PRESENTATION_MODE, QUIET_TIME
        for (s in listOf(2, 3, 4, 6)) assertTrue("state $s", quietFromState(s))
        // NOT_PRESENT, ACCEPTS_NOTIFICATIONS, APP (7)
        for (s in listOf(1, 5, 7)) assertFalse("state $s", quietFromState(s))
    }

    @Test
    fun wnfNichtLesbarHeisstNurSHQuery() {
        assertFalse(quietDecision(5, listOf(null, null)))
        assertTrue(quietDecision(2, listOf(null, null)))
    }

    @Test
    fun wnfUngleichNullIstStill() {
        // Nicht stoeren an, SHQuery meldet trotzdem ACCEPTS_NOTIFICATIONS
        assertTrue(quietDecision(5, listOf(1, null)))
        assertTrue(quietDecision(5, listOf(0, 2)))
        assertTrue(quietDecision(null, listOf(2, 0)))
    }

    @Test
    fun wnfNullIstNichtStill() {
        assertFalse(quietDecision(5, listOf(0, 0)))
    }

    @Test
    fun allesUnbekanntKlingt() {
        assertFalse(quietDecision(null, listOf(null, null)))
        assertFalse(quietDecision(null, emptyList()))
    }
}
