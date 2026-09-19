package de.gebetszeiten.prayer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Rueckfall auf die Berechnung ist genau einmal erlaubt: in
 * PrayerProvider.daily, hinter daySourceOrder. Taucht PrayerSchedule.forDate
 * anderswo auf, ist ein zweiter Rueckfall entstanden - und der zweite ist
 * garantiert der ungetestete.
 */
class CalculationIsNotAFallbackTest {

    @Test fun `die Berechnung wird nur an einer Stelle aufgerufen`() {
        val erlaubt = setOf("PrayerProvider.kt", "PrayerSchedule.kt")
        val treffer = File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.name !in erlaubt }
            .filter { it.readText().contains("PrayerSchedule.forDate") }
            .map { it.name }
            .toList()
        assertTrue("Berechnung ausserhalb von PrayerProvider aufgerufen: $treffer", treffer.isEmpty())
    }
}
