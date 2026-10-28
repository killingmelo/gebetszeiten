package de.gebetszeiten.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [dmgTitle]: ʿ und ʾ bekommen in Serifentiteln eine eigene Auszeichnung, sonst nichts. */
class DmgTextTest {

    private fun spans(text: String) = dmgTitle(text).spanStyles.map { it.start to it.end }

    @Test fun `ʿAṣr hat genau eine Auszeichnung am Anfang`() {
        assertEquals(listOf(0 to 1), spans("ʿAṣr"))
    }

    @Test fun `ʿIšāʾ hat Ain und Hamza ausgezeichnet`() {
        assertEquals(listOf(0 to 1, 4 to 5), spans("ʿIšāʾ"))
    }

    @Test fun `Text ohne Ain und Hamza bleibt ohne Auszeichnung`() {
        assertEquals(emptyList<Pair<Int, Int>>(), spans("Maġrib"))
    }

    @Test fun `Text bleibt unveraendert, Gewicht normal`() {
        val a = dmgTitle("Ǧamāʿa am Morgen")
        assertEquals("Ǧamāʿa am Morgen", a.text)
        assertEquals(listOf(4 to 5), a.spanStyles.map { it.start to it.end })
        assertTrue(a.spanStyles.all { it.item.fontWeight == FontWeight.Normal })
    }
}
