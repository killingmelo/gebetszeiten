package de.gebetszeiten.notify

import de.gebetszeiten.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Die Vorschau in der Ersteinrichtung — die Entscheidung dahinter, nicht das
 * Bild.
 *
 * Sie hat eine Zusage, die sie nicht brechen darf: sie zeigt dem Nutzer, was
 * er bekommt. Deshalb baut sie jede Zeile ueber [ongoing], dieselbe Funktion,
 * aus der die echte Dauerbenachrichtigung entsteht — und deshalb steht hier,
 * was daraus folgt.
 */
class OngoingPreviewTest {

    private fun rows(minuten: Long) = ongoingPreviewRows(
        remaining = Duration.ofMinutes(minuten),
        titleWithStep = { step -> "Noch $step bis Isha" },
        titleWithTime = "Isha um 22:48",
        timeLine = "um 22:48",
        activeLine = "aktuell: Maghrib",
        city = "Nuernberg",
    )

    @Test fun `genau drei Zeilen, in der Reihenfolge der Auswahl`() {
        val r = rows(41)
        assertEquals(3, r.size)
        assertEquals(
            listOf(AppSettings.COUNTDOWN_OFF, AppSettings.PRECISION_STEPS, AppSettings.PRECISION_EXACT),
            r.map { it.mode },
        )
    }

    @Test fun `Aus zeigt den Mond`() {
        assertEquals(CountdownGlyph.None, rows(41).first().glyph)
    }

    @Test fun `Stufen und Genau zeigen dasselbe Symbol - ueber den ganzen Bereich`() {
        // DIE Zusage, auf der die Oberflaeche steht: sie zeichnet die
        // Statusleiste EINMAL und schreibt daneben, das Symbol sei in beiden
        // Modi gleich. Waere das falsch, versprsche das Bild etwas, das die
        // echte Anzeige nie zeigt.
        for (minuten in 0L..14L * 60L) {
            val r = rows(minuten)
            assertEquals(
                "Minute $minuten",
                r[1].glyph,
                r[2].glyph,
            )
        }
    }

    @Test fun `verschieden ist der Titel - sonst zeigte die Auswahl drei gleiche Dinge`() {
        val r = rows(41)
        assertNotEquals(r[1].texts.title, r[2].texts.title)
        assertTrue(r[1].texts.title, r[1].texts.title.contains("40+ Min"))
        assertTrue(r[2].texts.title, r[2].texts.title.contains("22:48"))
    }

    @Test fun `in der letzten Minute sind die Titel gleich, der Zaehler laeuft weiter`() {
        // Dort gibt es keine Stufe mehr: „Stufen" faellt auf den klassischen
        // Titel zurueck, „Genau" trug ihn ohnehin. Die Vorschau darf hier also
        // keinen Text-Unterschied vorgaukeln.
        //
        // Der Zaehler laeuft aber weiter — `ongoingMode` prueft `exact` VOR
        // der leeren Stufe. Das ist richtig so (die letzte Minute ist genau
        // die, in der ein Sekundenzaehler etwas wert ist), und es ist der
        // einzige Unterschied, der in dieser Minute bleibt.
        val r = rows(0)
        assertEquals(r[1].texts.title, r[2].texts.title)
        assertTrue("der Zaehler gehoert gerade in die letzte Minute", r[2].showsChronometer)
        assertTrue("Stufen zaehlt nie selbst", !r[1].showsChronometer)
    }

    @Test fun `nur Genau laesst den Systemzaehler mitlaufen`() {
        val r = rows(41)
        assertEquals(listOf(false, false, true), r.map { it.showsChronometer })
    }

    @Test fun `die genaue Uhrzeit steht in jeder Zeile im Aufgeklappten`() {
        // Dieselbe Zusage wie in der echten Anzeige: runterziehen gibt immer
        // Ort und genaue Zeit.
        rows(41).forEach { assertTrue(it.texts.bigText, it.texts.bigText.contains("um 22:48")) }
    }

    @Test fun `der Ort steht in jeder Zeile im Untertitel`() {
        rows(41).forEach { assertEquals("Nuernberg", it.texts.subText) }
    }

    // --- Woher die Zahlen kommen ---

    @Test fun `mit echter naechster Gebetszeit nimmt die Vorschau diese`() {
        assertEquals(
            PreviewSource.Real("Isha", 2_000L),
            previewSource("Isha", 2_000L, nowMillis = 1_000L),
        )
    }

    @Test fun `ohne naechste Gebetszeit ein Beispiel`() {
        assertEquals(PreviewSource.Example, previewSource(null, null, 1_000L))
        assertEquals(PreviewSource.Example, previewSource("Isha", null, 1_000L))
        assertEquals(PreviewSource.Example, previewSource(null, 2_000L, 1_000L))
    }

    @Test fun `ein Ziel in der Vergangenheit ist kein Ziel`() {
        // Sonst rechnete die Vorschau mit einer negativen Restzeit und zeigte
        // ein Symbol, das es im Betrieb nie gibt.
        assertEquals(PreviewSource.Example, previewSource("Isha", 900L, 1_000L))
        assertEquals(PreviewSource.Example, previewSource("Isha", 1_000L, 1_000L))
    }
}
