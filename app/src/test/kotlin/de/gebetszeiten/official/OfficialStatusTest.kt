package de.gebetszeiten.official

import de.gebetszeiten.prayer.officialStatusText
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OfficialStatusTest {

    private val now = 1_786_000_000_000L // fester Zeitpunkt, kein System.now

    @Test fun `amtlicher Status nennt Standort und Abdeckung`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            sourceName = "Sakarya",
        )
        assertTrue(text, text.contains("Sakarya"))
        assertTrue(text, text.contains("9807"))
        assertTrue(text, text.contains("31.12.2026"))
    }

    @Test fun `Fehlergrund erscheint im Klartext`() {
        val text = officialStatusText(
            OfficialStatus(null, null, now, "Kein Diyanet-Standort aufloesbar"),
            sourceName = null,
        )
        assertTrue(text, text.contains("Kein Diyanet-Standort aufloesbar"))
    }

    @Test fun `ohne jeden Abruf wird das gesagt statt ein leerer Text`() {
        val text = officialStatusText(
            OfficialStatus(null, null, null, null),
            sourceName = null,
        )
        assertTrue(text, text.isNotBlank())
        assertTrue(text, text.contains("noch kein"))
    }

    @Test fun `mit Abdeckung aber ohne Versuchsdatensatz heisst es unbekannt statt noch kein Versuch`() {
        // Standort/Abdeckung belegen einen frueheren Erfolg, aber der
        // Versuchsdatensatz wurde von einem Abruf an einem anderen Ort
        // verdraengt (ein Datensatz fuer alle Orte, siehe Fix-Runde 1).
        // "noch kein Versuch" waere hier eine Falschaussage.
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), null, null),
            sourceName = "Sakarya",
        )
        assertTrue(text, text.contains("unbekannt"))
        assertTrue(text, !text.contains("noch kein Versuch"))
    }

    @Test fun `Zeitstempel wird in der uebergebenen Zone formatiert`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            sourceName = "Sakarya",
            zone = ZoneId.of("Europe/Istanbul"),
        )
        // Erwarteter Zeitstempel unabhaengig ausgerechnet (nicht mit dem
        // gleichen DateTimeFormatter erzeugt, den die Funktion selbst nutzt):
        // 1_786_000_000_000 ms = 2026-08-06T07:06:40Z = 2026-08-06T10:06:40
        // in Europe/Istanbul (fest UTC+3, keine Sommerzeit seit 2016).
        assertTrue(text, text.contains("Letzter Abruf: 06.08.2026, 10:06"))
    }
}
