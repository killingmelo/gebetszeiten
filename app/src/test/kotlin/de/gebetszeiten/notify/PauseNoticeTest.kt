package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseNoticeTest {

    @Test fun `keine Zeiten und noch nicht gemeldet - melden`() {
        assertEquals(PauseNotice.SHOW, pauseNotice(hasTimes = false, alreadyShown = false))
    }

    @Test fun `keine Zeiten und schon gemeldet - schweigen`() {
        assertEquals(PauseNotice.NOTHING, pauseNotice(hasTimes = false, alreadyShown = true))
    }

    @Test fun `wieder Zeiten und war gemeldet - Meldung zuruecknehmen`() {
        assertEquals(PauseNotice.CLEAR, pauseNotice(hasTimes = true, alreadyShown = true))
    }

    @Test fun `wieder Zeiten und war nie gemeldet - nichts tun`() {
        assertEquals(PauseNotice.NOTHING, pauseNotice(hasTimes = true, alreadyShown = false))
    }
}
