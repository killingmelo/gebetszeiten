package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoTimesNoticeTest {

    @Test fun `mit Online-Abruf wird das Abrufen angeboten`() {
        val n = noTimesNotice(city = "Nürnberg", onlineEnabled = true)
        assertEquals("Keine amtlichen Zeiten für Nürnberg", n.headline)
        assertTrue(n.showFetch)
    }

    @Test fun `ohne Online-Abruf wird kein Abrufen angeboten`() {
        val n = noTimesNotice(city = "Nürnberg", onlineEnabled = false)
        assertFalse("Ein Knopf, der nichts tun kann, ist eine Luege", n.showFetch)
    }

    @Test fun `der Satz sagt, was zu tun ist - nicht nur, was fehlt`() {
        listOf(true, false).forEach { online ->
            val d = noTimesNotice("Nürnberg", online).detail
            assertTrue("Kein Ausweg genannt: $d", d.contains("Berechnung"))
        }
    }

    @Test fun `mehrteilige Ortsnamen bleiben vollstaendig`() {
        assertTrue(noTimesNotice("Bad Mergentheim", true).headline.contains("Bad Mergentheim"))
    }
}
