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
}
