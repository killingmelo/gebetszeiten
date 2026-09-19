package de.gebetszeiten.wear

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Das wear-Pendant zu `CalculationIsNotAFallbackTest` am Telefon (Aufgabe
 * 17). Die Uhr hat seit Aufgabe 16 dieselbe Struktur wie `PrayerProvider`:
 * `WearPrayer.daily` fragt `daySourceOrder` ab, und die Berechnung
 * (`DiyanetPrayerTimesCalculator.calculate`) darf nur dort als letzter
 * Eintrag stehen - ein zweiter Aufruf anderswo waere ein zweiter, stiller
 * Rueckfall, den kein Test der Uhr sieht.
 *
 * Anders als am Telefon gibt es hier kein eigenes `PrayerSchedule`-Objekt:
 * `WearPrayer.kt` ruft den Rechner direkt auf, daher besteht die erlaubte
 * Menge nur aus dieser einen Datei.
 */
class CalculationIsNotAFallbackTest {

    @Test fun `die Berechnung wird nur an einer Stelle aufgerufen`() {
        val erlaubt = setOf("WearPrayer.kt")
        val treffer = File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.name !in erlaubt }
            .filter { it.readText().contains("DiyanetPrayerTimesCalculator.calculate") }
            .map { it.name }
            .toList()
        assertTrue("Berechnung ausserhalb von WearPrayer aufgerufen: $treffer", treffer.isEmpty())
    }
}
