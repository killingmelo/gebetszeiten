package de.gebetszeiten.official

import de.gebetszeiten.prayer.officialStatusText
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OfficialStatusTest {

    private val now = 1_786_000_000_000L // fester Zeitpunkt, kein System.now

    @Test fun `amtlicher Status nennt Standort und Abdeckung`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            sourceName = "Sakarya",
            nowEpochMs = now,
        )
        assertTrue(text, text.contains("Sakarya"))
        assertTrue(text, text.contains("9807"))
        assertTrue(text, text.contains("31.12.2026"))
    }

    @Test fun `Fehlergrund erscheint im Klartext`() {
        val text = officialStatusText(
            OfficialStatus(null, null, now, "Kein Diyanet-Standort aufloesbar"),
            sourceName = null,
            nowEpochMs = now,
        )
        assertTrue(text, text.contains("Kein Diyanet-Standort aufloesbar"))
    }

    @Test fun `ohne jeden Abruf wird das gesagt statt ein leerer Text`() {
        val text = officialStatusText(
            OfficialStatus(null, null, null, null),
            sourceName = null,
            nowEpochMs = now,
        )
        assertTrue(text, text.isNotBlank())
        assertTrue(text, text.contains("noch kein"))
    }
}
