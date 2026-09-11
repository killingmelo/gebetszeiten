package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Die Warnzeile, wenn die gebuendelte Reserve zur Neige geht (Task 19).
 *
 * `today` ist hier ueberall fest — die Funktion darf keine Systemuhr lesen,
 * sonst waere dieser Test an genau dem Tag rot, an dem er gebraucht wird.
 */
class CoverageWarningTest {

    private val heute = LocalDate.of(2026, 9, 11)

    @Test fun `ohne Abdeckungsende gibt es nichts zu warnen`() {
        // Kein gebuendelter Tisch fuer diesen Ort (Istanbul): eine Warnung
        // ueber eine Reserve, die es nie gab, waere reiner Laerm.
        assertNull(coverageWarning(null, heute))
    }

    @Test fun `mit reichlich Rest bleibt die Zeile weg`() {
        // Der heutige Bestand: 31.12.2026, also 111 Tage. Noch still.
        assertNull(coverageWarning(LocalDate.of(2026, 12, 31), heute))
    }

    @Test fun `genau auf der Schwelle wird gewarnt`() {
        // Die Schwelle faellt IN die Warnung: `null` gibt es nur, wenn MEHR
        // als warnWithinDays Tage abgedeckt sind.
        assertNotNull(coverageWarning(heute.plusDays(45), heute))
    }

    @Test fun `einen Tag ueber der Schwelle bleibt es still`() {
        assertNull(coverageWarning(heute.plusDays(46), heute))
    }

    @Test fun `knapp unter der Schwelle wird gewarnt`() {
        assertNotNull(coverageWarning(heute.plusDays(44), heute))
    }

    @Test fun `am letzten abgedeckten Tag wird gewarnt, aber nicht von abgelaufen geredet`() {
        // coverageEnd == heute: der heutige Tag IST noch abgedeckt.
        val text = coverageWarning(heute, heute)
        assertNotNull(text)
        assertTrue(text!!, text.contains("nur noch bis 11.09.2026"))
        assertTrue(text, !text.contains("endeten"))
    }

    @Test fun `ein Tag danach ist abgelaufen und sagt das auch`() {
        val text = coverageWarning(heute.minusDays(1), heute)
        assertNotNull(text)
        assertTrue(text!!, text.contains("endeten am 10.09.2026"))
        assertTrue(text, !text.contains("nur noch bis"))
    }

    @Test fun `der Satz nennt den Online-Cache VOR der eigenen Berechnung`() {
        // Der Befund aus der Pruefung: „ohne Netz gibt es danach nur die
        // eigene Berechnung" uebergeht den persistierten Online-Cache, den
        // `PrayerProvider.daily` VOR dem Bundle und OHNE Netz liest. Fuer
        // einen Nutzer mit `useOnline` (ab Werk an) ist die Reserve die
        // DRITTE Stufe, nicht die letzte — der Satz darf ihm nicht mehr
        // Verlust ankuendigen, als eintritt.
        listOf(coverageWarning(heute.plusDays(10), heute)!!, coverageWarning(heute.minusDays(10), heute)!!)
            .forEach { text ->
                val cache = text.indexOf("zuvor geladenen amtlichen Zeiten")
                val berechnung = text.indexOf("eigene Berechnung")
                assertTrue(text, cache >= 0)
                assertTrue("die Reihenfolge stimmt nicht: $text", cache < berechnung)
                // Die alte, falsche Zuspitzung darf nicht zurueckkommen.
                assertTrue(text, !text.contains("nur die eigene Berechnung"))
            }
    }

    @Test fun `beide Faelle nennen die Folge fuer den Nutzer, nicht das Asset`() {
        val bald = coverageWarning(heute.plusDays(10), heute)!!
        val abgelaufen = coverageWarning(heute.minusDays(10), heute)!!
        listOf(bald, abgelaufen).forEach {
            assertTrue(it, it.contains("ohne Netz"))
            assertTrue(it, it.contains("eigene Berechnung"))
            assertTrue(it, !it.contains("Asset"))
            assertTrue(it, !it.contains("coverage"))
        }
        assertTrue("beide Faelle lauten gleich", bald != abgelaufen)
    }

    /**
     * Der Kern der Sache: keine Zahl darf etwas anderes behaupten, als sie
     * ist. Die EINZIGEN Ziffern im Satz sind die des Abdeckungsendes — keine
     * Restlaufzeit, keine Schwelle, kein Jahrgang, die man als Abdeckung
     * missverstehen koennte. (Dieselbe Fehlerklasse wie Task 8 und der
     * „geprüfte 31-Tage-Stand" bei 51 ausgelieferten Tagen.)
     */
    @Test fun `die einzige Zahl im Satz ist das Abdeckungsende`() {
        listOf(heute.plusDays(7), heute.minusDays(3), LocalDate.of(2027, 2, 1)).forEach { ende ->
            val text = coverageWarning(ende, heute, warnWithinDays = 200)!!
            val erwartet = ende.dayOfMonth.toString().padStart(2, '0') +
                ende.monthValue.toString().padStart(2, '0') +
                ende.year.toString()
            assertEquals(text, erwartet, text.filter { it.isDigit() })
        }
    }

    @Test fun `die Frist ist ein Parameter, kein eingebauter Wert`() {
        val ende = heute.plusDays(100)
        assertNull(coverageWarning(ende, heute, warnWithinDays = 99))
        assertNotNull(coverageWarning(ende, heute, warnWithinDays = 100))
    }
}
