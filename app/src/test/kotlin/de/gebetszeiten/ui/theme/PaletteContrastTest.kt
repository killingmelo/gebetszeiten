package de.gebetszeiten.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kontrast-Wächter: prüft die Tokens, die tatsächlich in die Material-Schemata eingehen. */
class PaletteContrastTest {
    private fun check(t: Tokens, min: Double) {
        for ((name, fg) in listOf("ink" to t.ink, "stone" to t.stone, "accent" to t.accent, "gold" to t.gold, "error" to t.error))
            for ((bgName, bg) in listOf("canvas" to t.canvas, "surface" to t.surface))
                assertTrue("$name auf $bgName: ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= min)
    }

    /** Auf dem leicht grünen Grund der laufenden Gebetszeile stehen Name, Zeit (Akzent),
     *  Nebenzeiten (Stein) und Makrūh (Gold); alle müssen dort genauso lesbar sein. */
    private fun checkCurrentRow(t: Tokens, min: Double) {
        val bg = t.currentRow
        for ((name, fg) in listOf("ink" to t.ink, "stone" to t.stone, "accent" to t.accent, "gold" to t.gold))
            assertTrue("$name auf currentRow: ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= min)
    }

    @Test fun currentRowTintKeepsContrast() {
        checkCurrentRow(Palette.Day, 4.5); checkCurrentRow(Palette.Night, 4.5)
        checkCurrentRow(Palette.DayHc, 7.0); checkCurrentRow(Palette.NightHc, 7.0)
    }

    @Test fun currentRowIsVisiblyTinted() {
        for (t in listOf(Palette.Day, Palette.Night))
            assertTrue("kaum sichtbar: ${contrastRatio(t.currentRow, t.surface)}", contrastRatio(t.currentRow, t.surface) >= 1.05)
        // Im Hochkontrast ungetönt (Gold hielte 7:1 sonst nicht), dort markiert nur die Schrift.
        assertEquals(Palette.DayHc.surface, Palette.DayHc.currentRow)
        assertEquals(Palette.NightHc.surface, Palette.NightHc.currentRow)
    }

    @Test fun dayAndNightMeetAa() { check(Palette.Day, 4.5); check(Palette.Night, 4.5) }
    @Test fun highContrastMeetsAaa() { check(Palette.DayHc, 7.0); check(Palette.NightHc, 7.0) }
    @Test fun nightCanvasIsTrueBlack() { assertEquals(Color(0xFF000000), Palette.Night.canvas) }
    @Test fun knownRatio() { assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01) }
}
