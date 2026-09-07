package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Bestandsabsicherung fuer RemainingLabel.kt — die Datei haengt an vier
 * Oberflaechen (Benachrichtigung, Widget, und weiter) und hatte bisher keinen
 * Test. Ab Task 13 haengt auch die Symbolauswahl daran.
 */
class RemainingLabelTest {

    // --- remainingStepShort: dieselben Grenzen wie beim Symbol ---

    @Test fun vierzehnStunden() { assertEquals("14+ Std", remainingStepShort(Duration.ofHours(14))) }

    @Test fun genauZehnStunden() { assertEquals("10+ Std", remainingStepShort(Duration.ofHours(10))) }

    @Test fun knappUnterZehnStunden() {
        assertEquals("9+ Std", remainingStepShort(Duration.ofHours(10).minusMillis(1)))
    }

    @Test fun genauEineStunde() { assertEquals("1+ Std", remainingStepShort(Duration.ofHours(1))) }

    @Test fun knappUnterEinerStunde() {
        assertEquals("50+ Min", remainingStepShort(Duration.ofHours(1).minusMillis(1)))
    }

    @Test fun neunundfuenfzigMinuten() { assertEquals("50+ Min", remainingStepShort(Duration.ofMinutes(59))) }

    @Test fun siebenundzwanzigMinutenWerdenAbgerundet() {
        // Abgerundet, nicht gerundet — 27 Min verspricht nur 20+.
        assertEquals("20+ Min", remainingStepShort(Duration.ofMinutes(27)))
    }

    @Test fun genauZehnMinuten() { assertEquals("10+ Min", remainingStepShort(Duration.ofMinutes(10))) }

    @Test fun knappUnterZehnMinuten() {
        assertEquals("9 Min", remainingStepShort(Duration.ofMinutes(10).minusMillis(1)))
    }

    @Test fun genauEineMinute() { assertEquals("1 Min", remainingStepShort(Duration.ofMinutes(1))) }

    @Test fun neunundfuenfzigSekundenSindLeer() { assertEquals("", remainingStepShort(Duration.ofSeconds(59))) }

    @Test fun nullIstLeer() { assertEquals("", remainingStepShort(Duration.ZERO)) }

    @Test fun negativIstLeer() { assertEquals("", remainingStepShort(Duration.ofSeconds(-30))) }

    @Test fun deutlichNegativIstLeer() {
        // Der Negativ-Guard klemmt auf ZERO, es entsteht also kein "-5 Min".
        assertEquals("", remainingStepShort(Duration.ofMinutes(-5)))
    }

    // --- remainingStepLabel: "jetzt" genau dann, wenn short leer ist ---

    @Test fun labelStelltNochDavor() {
        assertEquals("noch 2+ Std", remainingStepLabel(Duration.ofHours(2)))
        assertEquals("noch 20+ Min", remainingStepLabel(Duration.ofMinutes(27)))
        assertEquals("noch 3 Min", remainingStepLabel(Duration.ofMinutes(3)))
    }

    @Test fun labelIstJetztGenauDannWennShortLeerIst() {
        val proben = listOf(
            Duration.ofHours(14),
            Duration.ofHours(1),
            Duration.ofMinutes(10),
            Duration.ofMinutes(1),
            Duration.ofSeconds(59),
            Duration.ZERO,
            Duration.ofSeconds(-30),
            Duration.ofMinutes(-5),
        )
        proben.forEach { d ->
            val leer = remainingStepShort(d).isEmpty()
            assertEquals("bei $d", leer, remainingStepLabel(d) == "jetzt")
        }
    }

    // --- isUrgent ---

    @Test fun urgentIstBeiGenauZehnMinutenFalse() {
        assertFalse(isUrgent(Duration.ofMinutes(10)))
    }

    @Test fun urgentIstKnappUnterZehnMinutenTrue() {
        assertTrue(isUrgent(Duration.ofMinutes(10).minusMillis(1)))
    }

    @Test fun urgentIstWeitDrausseFalse() {
        assertFalse(isUrgent(Duration.ofHours(1)))
        assertFalse(isUrgent(Duration.ofMinutes(59)))
    }

    @Test fun urgentGiltBisNullHinunter() {
        assertTrue(isUrgent(Duration.ofMinutes(1)))
        assertTrue(isUrgent(Duration.ofSeconds(59)))
        assertTrue(isUrgent(Duration.ZERO))
    }

    @Test fun urgentUndShortFallenBeiNegativerDauerAuseinander() {
        // Bestandsverhalten, absichtlich dokumentiert statt geaendert:
        // remainingStepShort klemmt Negatives auf ZERO und liefert deshalb
        // leer ("jetzt"), waehrend isUrgent wegen des !isNegative-Guards
        // false liefert — die Oberflaeche zeigt "jetzt" dann OHNE Dringlich-
        // keitsfarbe. Aendern waere eine eigene Entscheidung (vier Aufrufer).
        val negativ = Duration.ofSeconds(-30)
        assertEquals("", remainingStepShort(negativ))
        assertEquals("jetzt", remainingStepLabel(negativ))
        assertFalse(isUrgent(negativ))
    }
}
