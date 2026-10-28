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

    @Test fun dayAndNightMeetAa() { check(Palette.Day, 4.5); check(Palette.Night, 4.5) }
    @Test fun highContrastMeetsAaa() { check(Palette.DayHc, 7.0); check(Palette.NightHc, 7.0) }
    @Test fun nightCanvasIsTrueBlack() { assertEquals(Color(0xFF000000), Palette.Night.canvas) }
    @Test fun knownRatio() { assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01) }
}
