package de.gebetszeiten.prayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix-Runde 1 zu Aufgabe 11: `noTimesNotice` wurde in Aufgabe 10 fuer den
 * EIN-TAG-Fall der Heute-Ansicht formuliert - "Keine amtlichen Zeiten fuer
 * <Ort>" ist dort immer wahr. Fuer die Monatstabelle stimmt derselbe Satz
 * nur, wenn ALLE Tage betroffen sind; fehlt nur ein einzelner Tag (z. B.
 * der Tag nach dem Ende der gebuendelten Abdeckung), gibt es fuer den Ort
 * sehr wohl amtliche Zeiten - der Satz darf das nicht verschweigen.
 */
class MonthNoTimesNoticeTest {

    @Test fun `ganzer Monat leer verwendet dieselbe Kopfaussage wie der Ein-Tag-Fall`() {
        assertTrue(
            monthNoTimesNotice(city = "Nürnberg", onlineEnabled = true, emptyDays = 30, totalDays = 30)
                .startsWith(noTimesNotice(city = "Nürnberg", onlineEnabled = true).headline),
        )
        assertTrue(
            monthNoTimesNotice(city = "Nürnberg", onlineEnabled = false, emptyDays = 28, totalDays = 28)
                .startsWith(noTimesNotice(city = "Nürnberg", onlineEnabled = false).headline),
        )
    }

    /** Der eigentliche Fehler aus der Pruefung: ein einzelner leerer Tag
     *  darf NICHT dieselbe Aussage bekommen wie ein komplett leerer Monat -
     *  fuer den Ort gibt es amtliche Zeiten, nur nicht fuer diesen Tag. */
    @Test fun `ein einzelner leerer Tag behauptet nicht, es gaebe gar keine amtlichen Zeiten`() {
        val satz = monthNoTimesNotice(city = "Nürnberg", onlineEnabled = true, emptyDays = 1, totalDays = 30)
        assertFalse(
            "Ein Tag ist nicht der ganze Monat - der Satz darf die Ein-Tag-Aussage nicht enthalten",
            satz.contains("Keine amtlichen Zeiten für"),
        )
    }

    @Test fun `einzelner Tag wird sprachlich als Einzahl behandelt`() {
        val satz = monthNoTimesNotice(city = "Nürnberg", onlineEnabled = true, emptyDays = 1, totalDays = 30)
        assertTrue(satz.contains("einen Tag"))
    }

    @Test fun `mehrere Tage werden gezaehlt`() {
        val satz = monthNoTimesNotice(city = "Nürnberg", onlineEnabled = true, emptyDays = 3, totalDays = 30)
        assertTrue(satz.contains("3 Tage"))
    }

    @Test fun `der Satz sagt in beiden Faellen, was zu tun ist`() {
        listOf(1 to 30, 30 to 30).forEach { (empty, total) ->
            listOf(true, false).forEach { online ->
                val satz = monthNoTimesNotice("Nürnberg", online, empty, total)
                assertTrue("Kein Ausweg genannt: $satz", satz.contains("Berechnung"))
            }
        }
    }

    @Test fun `Ortsname mehrteilig bleibt vollstaendig im Teilmonat-Fall`() {
        assertTrue(
            monthNoTimesNotice("Bad Mergentheim", true, emptyDays = 30, totalDays = 30).contains("Bad Mergentheim"),
        )
    }

    @Test fun `ungueltige Tageszahlen werfen`() {
        assertThrows(IllegalArgumentException::class.java) {
            monthNoTimesNotice("Nürnberg", true, emptyDays = 0, totalDays = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            monthNoTimesNotice("Nürnberg", true, emptyDays = 31, totalDays = 30)
        }
    }
}
