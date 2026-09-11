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

    // --- Task 15, Teil 4: "jetzt" ist dringend, auch nach dem Gebetseintritt ---

    @Test fun urgentUndShortStimmenBeiNegativerDauerUeberein() {
        // Ersetzt urgentUndShortFallenBeiNegativerDauerAuseinander: dort war
        // festgehalten, dass isUrgent bei negativer Dauer false liefert,
        // waehrend remainingStepShort auf ZERO klemmt und "jetzt" ergibt.
        // Sichtbare Folge war ein Ausblinken der Dringlichkeitsfarbe genau im
        // Moment des Gebets — eine Sekunde davor "jetzt" in Rot, eine Sekunde
        // danach "jetzt" in Normalfarbe. isUrgent klemmt jetzt genauso.
        val negativ = Duration.ofSeconds(-30)
        assertEquals("", remainingStepShort(negativ))
        assertEquals("jetzt", remainingStepLabel(negativ))
        assertTrue(isUrgent(negativ))
    }

    @Test fun urgentBleibtUeberDenGebetseintrittHinwegStehen() {
        // Kein Wechsel an der Null-Grenze, in keine Richtung.
        assertTrue(isUrgent(Duration.ofSeconds(1)))
        assertTrue(isUrgent(Duration.ZERO))
        assertTrue(isUrgent(Duration.ofSeconds(-1)))
        assertTrue(isUrgent(Duration.ofMinutes(-5)))
        // Auch weit hinter dem Gebet: dort steht ohnehin schon das naechste
        // Gebet an, aber falsch waere hier ein Umschlagen auf false.
        assertTrue(isUrgent(Duration.ofHours(-3)))
    }

    @Test fun urgentIstGenauDannWahrWennDasLabelHoechstensNeunMinutenZeigt() {
        val proben = listOf(
            Duration.ofHours(14),
            Duration.ofHours(1),
            Duration.ofMinutes(10),
            Duration.ofMinutes(9),
            Duration.ofMinutes(1),
            Duration.ofSeconds(59),
            Duration.ZERO,
            Duration.ofSeconds(-30),
            Duration.ofMinutes(-5),
        )
        proben.forEach { d ->
            val kurzfristig = remainingStepShort(d)
                .let { it.isEmpty() || (it.endsWith(" Min") && !it.contains("+")) }
            assertEquals("bei $d", kurzfristig, isUrgent(d))
        }
    }
}
