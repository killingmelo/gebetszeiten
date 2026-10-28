package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.prayer.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inhalt des Reiters „Zeiten verstehen“: jeder Abschnitt genau einmal, Zitate
 * vollständig (Arabisch, Deutsch, Quelle) und ohne Gedanken- oder Halbgeviertstrich.
 * Ob ein Zitat stimmt, prüft dieser Test nicht; dafür gibt es die Belegdatei
 * docs/superpowers/specs/2026-10-28-zitate-belege.md.
 */
class VerstehenContentTest {

    @Test
    fun jederAbschnittGenauEinmalInEnumReihenfolge() {
        assertEquals(Section.entries.toList(), VERSTEHEN.map { it.section })
    }

    @Test
    fun zitateSindVollstaendig() {
        for (entry in VERSTEHEN) {
            assertTrue("${entry.section}: höchstens zwei Zitate", entry.quotes.size <= 2)
            for (q in entry.quotes) {
                assertTrue("${entry.section}: arabic leer", q.arabic.isNotBlank())
                assertTrue("${entry.section}: german leer", q.german.isNotBlank())
                assertTrue("${entry.section}: source leer", q.source.isNotBlank())
            }
        }
    }

    @Test
    fun keineGedankenOderHalbgeviertstriche() {
        val texts = buildList<String> {
            add(TRANSLATION_NOTE)
            for (e in VERSTEHEN) {
                add(e.title)
                e.note?.let { add(it) }
                for (q in e.quotes) {
                    add(q.arabic); add(q.german); add(q.source)
                }
            }
        }
        for (t in texts) {
            assertFalse("Gedankenstrich in: $t", t.contains('—'))
            assertFalse("Halbgeviertstrich in: $t", t.contains('–'))
        }
    }

    @Test
    fun sonnenuhrNurBeiZuhrUndAsr() {
        val withDial = VERSTEHEN.filter { it.showsSundial }.map { it.section }.toSet()
        assertEquals(setOf(Section.ZUHR, Section.ASR), withDial)
    }

    @Test
    fun asrHatDiyanetHinweis() {
        val asr = VERSTEHEN.single { it.section == Section.ASR }
        assertNotNull(asr.note)
        assertTrue(asr.note!!.contains("Diyanet"))
        assertEquals("Die angezeigte ʿAṣr-Zeit ist die amtliche Zeit von Diyanet.", asr.note)
    }

    @Test
    fun titelNichtLeer() {
        for (e in VERSTEHEN) assertTrue("${e.section}: Titel leer", e.title.isNotBlank())
    }
}
