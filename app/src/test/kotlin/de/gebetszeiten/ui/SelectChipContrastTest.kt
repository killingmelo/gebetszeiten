package de.gebetszeiten.ui

import de.gebetszeiten.ui.theme.Palette
import de.gebetszeiten.ui.theme.Tokens
import de.gebetszeiten.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der gewählte Chip muss sich vom Grund abheben (Rand ≥ 3:1) und seine
 * Beschriftung lesbar bleiben (≥ 4,5:1, Hochkontrast ≥ 7:1). Vorher war ein
 * gewählter Chip weiß auf Creme, etwa 1,1:1.
 */
class SelectChipContrastTest {

    private fun check(name: String, t: Tokens, text: Double) {
        val fill = selectedChipFill(t)
        val rand = contrastRatio(t.accent, t.canvas)
        assertTrue("$name: Akzentrand auf Grund nur $rand", rand >= 3.0)
        val schrift = contrastRatio(t.ink, fill)
        assertTrue("$name: Beschriftung auf gewähltem Chip nur $schrift", schrift >= text)
        val haken = contrastRatio(t.accent, fill)
        assertTrue("$name: Häkchen auf gewähltem Chip nur $haken", haken >= 3.0)
    }

    @Test fun `Tag und Nacht`() {
        check("Tag", Palette.Day, 4.5)
        check("Nacht", Palette.Night, 4.5)
    }

    @Test fun `Hochkontrast`() {
        check("Tag HC", Palette.DayHc, 7.0)
        check("Nacht HC", Palette.NightHc, 7.0)
    }
}
