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

    /** Der ASCII-Fastpath muss dasselbe liefern wie der volle NFD-Pfad. */
    @Test fun asciiFastPathMatchesFullPath() {
        assertEquals("esenkoy", TextNormalize.normalize("Esenkoy"))
        assertEquals("esenkoy", TextNormalize.normalize("Esenköy"))
        assertEquals("new york", TextNormalize.normalize("  New York "))
    }
}
