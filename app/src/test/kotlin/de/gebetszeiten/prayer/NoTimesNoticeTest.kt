package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

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

/**
 * Deckt den Ein-Tag-Entscheid des Widgets ab (Aufgabe 12, Fix-Runde 1):
 * WELCHER Zustand gezeigt wird und mit welchem `onlineEnabled`-Wahrheitswert.
 * Die drei von der Pruefung geforderten Mutationen sind hier je ein eigener
 * Testfall, kein Nebeneffekt eines anderen Tests.
 *
 * `widgetNotice` liefert das VOLLE [NoTimesNotice] (nicht nur `headline`):
 * `headline` allein aendert sich nie mit `onlineEnabled` (siehe
 * [noTimesNotice]) - ein Test auf `headline` alleine koennte die Mutation
 * "onlineEnabled ignorieren / durch true ersetzen" also NIE toeten, egal wie
 * er formuliert waere. Erst `showFetch` macht `onlineEnabled` beobachtbar.
 */
class WidgetNoticeTest {

    private val someNext = NextPrayer(Prayer.DHUHR, ZonedDateTime.now(ZoneId.of("UTC")))

    @Test fun `mit Zeit gibt es keinen Hinweis`() {
        assertNull(widgetNotice(someNext, "Nürnberg", onlineEnabled = true))
    }

    @Test fun `ohne Zeit gibt es den Hinweis mit Ueberschrift`() {
        val notice = widgetNotice(next = null, city = "Nürnberg", onlineEnabled = true)
        assertNotNull(notice)
        assertEquals("Keine amtlichen Zeiten für Nürnberg", notice!!.headline)
    }

    @Test fun `onlineEnabled wird tatsaechlich durchgereicht, nicht ignoriert`() {
        // Mutation "onlineEnabled ignorieren / durch true ersetzen" toetet
        // diesen Test: wuerde `widgetNotice` intern immer `true` an
        // `noTimesNotice` weitergeben, waere `showFetch` bei
        // `onlineEnabled = false` faelschlich `true`.
        assertTrue(widgetNotice(null, "Nürnberg", onlineEnabled = true)!!.showFetch)
        assertFalse(widgetNotice(null, "Nürnberg", onlineEnabled = false)!!.showFetch)
    }
}
