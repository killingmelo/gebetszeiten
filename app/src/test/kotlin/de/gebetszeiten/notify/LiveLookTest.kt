package de.gebetszeiten.notify

import de.gebetszeiten.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveLookTest {

    private val name = "ʿAṣr"

    @Test fun `Vorgabe ist Mond und nur die Zahl`() {
        assertEquals(Pill(promote = true, text = "40", icon = PillIcon.MOON), pillOf(LiveLook(), "40", name, canPromote = true))
    }

    @Test fun `Gebet und Zahl setzt den Namen davor`() {
        val look = LiveLook(text = AppSettings.PILL_TEXT_PRAYER)
        assertEquals("ʿAṣr 40", pillOf(look, "40", name, canPromote = true).text)
    }

    @Test fun `Symbolwahl wird übernommen`() {
        assertEquals(PillIcon.PRAYER, pillOf(LiveLook(icon = AppSettings.PILL_ICON_PRAYER), "40", name, true).icon)
        assertEquals(PillIcon.NUMBER, pillOf(LiveLook(icon = AppSettings.PILL_ICON_NUMBER), "40", name, true).icon)
    }

    @Test fun `Statusleiste aus heisst kein Live-Update und ruhiges Symbol`() {
        val pill = pillOf(LiveLook(statusBar = false), "40", name, canPromote = true)
        assertEquals(Pill(promote = false, text = null, icon = PillIcon.MOON), pill)
    }

    @Test fun `ohne Restzeit kein Live-Update`() {
        assertEquals(false, pillOf(LiveLook(), null, name, canPromote = true).promote)
    }

    @Test fun `nicht erlaubt bleibt es beim Zahlen-Symbol`() {
        // OnePlus ab Werk: das Zahlen-Symbol in der Statusleiste, wie vor 0.5.1,
        // auch wenn der Nutzer Mond oder Gebet als Pillensymbol gewählt hat.
        val pill = pillOf(LiveLook(icon = AppSettings.PILL_ICON_PRAYER), "40", name, canPromote = false)
        assertEquals(PillIcon.NUMBER, pill.icon)
    }
}
