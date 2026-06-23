package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizeTest {
    @Test fun stripsUmlautsAndCase() {
        assertEquals("nurnberg", TextNormalize.normalize("Nürnberg"))
        assertEquals("nurnberg", TextNormalize.normalize("  NÜRNBERG "))
    }

    @Test fun stripsTurkishLetters() {
        assertEquals("istanbul", TextNormalize.normalize("İstanbul"))
        assertEquals("sisli", TextNormalize.normalize("Şişli"))
    }
}
