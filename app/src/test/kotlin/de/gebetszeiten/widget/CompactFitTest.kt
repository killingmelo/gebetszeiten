package de.gebetszeiten.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ein 2×1-Widget ist auf dem Pixel-Startbildschirm rund 99 dp hoch. */
class CompactFitTest {

    @Test fun `normale Schrift - alle drei Zeilen`() {
        assertEquals(CompactLines(karaha = false, next = true), compactLines(99f, 1.0f, true, false, true))
    }

    @Test fun `Schrift 1,4 - dann faellt weg statt abgeschnitten zu werden`() {
        assertEquals(CompactLines(karaha = false, next = false), compactLines(99f, 1.4f, true, false, true))
    }

    @Test fun `Karaha hat Vorrang vor dann`() {
        assertEquals(CompactLines(karaha = true, next = false), compactLines(99f, 1.0f, true, true, true))
    }

    @Test fun `hohes Widget - alles`() {
        assertEquals(CompactLines(karaha = true, next = true), compactLines(200f, 1.4f, true, true, true))
    }

    private val lang = "dann ʿAṣr um 15:38"
    private val kurz = "ʿAṣr 15:38"

    @Test fun `schmales Widget - Kurzform statt Umbruch`() {
        // Samsung 2×1, rund 140 dp breit: die lange Form brach um, und die
        // Uhrzeit fiel unten aus dem Widget.
        assertEquals(kurz, compactNextLine(lang, kurz, 140f, 1.0f))
    }

    @Test fun `breites Widget - lange Form`() {
        assertEquals(lang, compactNextLine(lang, kurz, 220f, 1.0f))
    }

    @Test fun `grosse Restzeit wird kleiner statt gekuerzt`() {
        // 2×1 rund 140 dp breit: 28 sp passt für „10+ Min“ nicht, 22 sp schon.
        assertEquals(22, bigCountdownSp("10+ Min", 140f, 1.0f))
        assertEquals(28, bigCountdownSp("10+ Min", 220f, 1.0f))
        assertEquals(18, bigCountdownSp("1 Std 30+ Min", 120f, 1.4f))
    }

    @Test fun `Tagesliste zeigt ab dem laufenden Gebet`() {
        // Sechs Einträge, drei passen, Ẓuhr (Index 2) läuft: Ẓuhr, ʿAṣr, Maġrib.
        assertEquals(2..4, listWindow(size = 6, currentIndex = 2, fits = 3))
        // Gegen Ende wird nach oben aufgefüllt: Maġrib, ʿIšāʾ plus ʿAṣr davor.
        assertEquals(3..5, listWindow(size = 6, currentIndex = 5, fits = 3))
        // Nichts läuft (vor Faǧr): von oben.
        assertEquals(0..2, listWindow(size = 6, currentIndex = -1, fits = 3))
        // Alles passt.
        assertEquals(0..5, listWindow(size = 6, currentIndex = 2, fits = 8))
    }

    @Test fun `Tagesliste rechnet so viele Zeilen, wie wirklich passen`() {
        // 2×1 (rund 99 dp): zwei Zeilen; die dritte ragte am Emulator heraus.
        assertEquals(2, listRowsThatFit(99f, 1.0f))
        // 2×2 (rund 200 dp): alle sechs.
        assertTrue(listRowsThatFit(200f, 1.0f) >= 6)
    }

    @Test fun `Tagesliste mit Nebenzeiten rechnet die Unterzeilen mit`() {
        // Gewichte: Faǧr 1, Šurūq mit Nebenzeiten 2, Ẓuhr 2, ʿAṣr 2, Maġrib 2, ʿIšāʾ 2.
        val w = listOf(1, 2, 2, 2, 2, 2)
        // ʿAṣr (3) läuft, vier Zeilen passen: ʿAṣr und Maġrib.
        assertEquals(3..4, listWindowWeighted(w, currentIndex = 3, fits = 4))
        // Am Ende wird nach oben aufgefüllt.
        assertEquals(4..5, listWindowWeighted(w, currentIndex = 5, fits = 4))
        // Zu wenig Platz: wenigstens das laufende.
        assertEquals(3..3, listWindowWeighted(w, currentIndex = 3, fits = 1))
        // Alles passt.
        assertEquals(0..5, listWindowWeighted(w, currentIndex = 2, fits = 20))
    }

    @Test fun `grosse Schrift kuerzt frueher`() {
        assertEquals(lang, compactNextLine(lang, kurz, 180f, 1.0f))
        assertEquals(kurz, compactNextLine(lang, kurz, 180f, 1.4f))
    }
}
