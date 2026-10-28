package de.gebetszeiten.widget

import org.junit.Assert.assertEquals
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
}
