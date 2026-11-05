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
            for (q in entry.quotes + entry.groups.flatMap { it.quotes }) {
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
                for (q in e.quotes + e.groups.flatMap { it.quotes }) {
                    add(q.arabic); add(q.german); add(q.source)
                }
                e.groups.forEach { add(it.heading) }
            }
            for (g in GLOSSAR.values) { add(g.term); add(g.text) }
            for (tab in VERSTEHEN_TABS) {
                add(tab.beginLabel); add(tab.endLabel)
                tab.rakaat.forEach { (a, b) -> add(a); add(b) }
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
        // Diyanet folgt der Ansicht der beiden Schüler (einfacher Schatten); das muss dastehen.
        assertTrue(asr.note!!.startsWith("Die angezeigte ʿAṣr-Zeit ist die amtliche Zeit von Diyanet."))
        assertTrue(asr.note!!.contains("Abū Yūsuf und Muḥammad"))
        assertTrue(asr.note!!.contains("Abū Ḥanīfa"))
    }

    @Test
    fun jamaaSagtDassSieNichtAmtlichIst() {
        val jamaa = VERSTEHEN.single { it.section == Section.JAMAA }
        assertEquals(
            "Die Ǧamāʿa-Zeit ist keine amtliche Zeit. Sie ergibt sich aus dem Sonnenaufgang " +
                "minus dem Vorlauf aus den Einstellungen. Die eigene Moschee kann abweichen.",
            jamaa.note,
        )
    }

    @Test
    fun makruhSpannenNennenIhreHerleitung() {
        // Die Minuten kommen aus core Karaha.kt; ändert sich dort eine Zahl,
        // muss der Hinweis mitwandern.
        val erwartet = mapOf(
            Section.SHURUQ_MAKRUH to "45 Minuten",
            Section.ZUHR to "20 Minuten",
            Section.ISFIRAR_MAKRUH to "40 Minuten",
        )
        for ((section, minuten) in erwartet) {
            val note = VERSTEHEN.single { it.section == section }.note
            assertNotNull("$section ohne Hinweis", note)
            assertTrue("$section: $note", note!!.contains(minuten))
            assertTrue("$section: $note", note.startsWith("Die angezeigte Spanne rechnet die App"))
        }
    }

    @Test
    fun angezeigteNafilaSpannenNennenIhreHerleitung() {
        for (section in listOf(Section.DUHA, Section.AWWABIN, Section.TAHAJJUD)) {
            val note = VERSTEHEN.single { it.section == section }.note
            assertNotNull("$section ohne Hinweis", note)
            assertTrue("$section: $note", note!!.startsWith("Die angezeigte"))
            assertTrue("$section: $note", note.contains("rechnet die App"))
        }
    }

    @Test
    fun jederBegriffImReiterHatEinenGlossareintrag() {
        for (tab in VERSTEHEN_TABS) {
            assertTrue("${tab.label}: keine Begriffe", tab.glossary.isNotEmpty())
            for (key in tab.glossary) assertNotNull("${tab.label}: $key fehlt im Glossar", GLOSSAR[key])
        }
        // Kein Eintrag, den kein Reiter zeigt.
        val shown = VERSTEHEN_TABS.flatMap { it.glossary }.toSet()
        assertEquals(GLOSSAR.keys, shown)
    }

    @Test
    fun fardZahlenJeGebet() {
        val fard = VERSTEHEN_TABS.associate { tab -> tab.label to tab.rakaat.firstOrNull { it.first == "Farḍ" }?.second }
        assertEquals(mapOf("Faǧr" to "2", "Šurūq" to null, "Ẓuhr" to "4", "ʿAṣr" to "4", "Maġrib" to "3", "ʿIšāʾ" to "4"), fard)
    }

    @Test
    fun rakaZitateStehenInDenGebetsreitern() {
        // Jede Angabe der Rakʿa-Kachel muss im selben Reiter belegt sein.
        for (section in listOf(Section.FAJR, Section.ZUHR, Section.ASR, Section.MAGHRIB, Section.ISHA)) {
            val groups = VERSTEHEN.single { it.section == section }.groups
            assertTrue("$section ohne Rakʿa-Zitate", groups.any { it.heading == "Rakʿa" && it.quotes.isNotEmpty() })
        }
    }

    @Test
    fun keineZweiGleichenFotosHintereinander() {
        val fotos = VERSTEHEN.map { it.photo }
        fotos.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("Eintrag ${i + 1} und ${i + 2} zeigen dasselbe Foto $a", a == null || a != b)
        }
        val ohneFoto = VERSTEHEN.filter { it.photo == null }.map { it.section }.toSet()
        assertEquals(setOf(Section.DUHA, Section.ISFIRAR_MAKRUH, Section.AWWABIN, Section.TAHAJJUD), ohneFoto)
    }

    @Test
    fun titelNichtLeer() {
        for (e in VERSTEHEN) assertTrue("${e.section}: Titel leer", e.title.isNotBlank())
    }
}
