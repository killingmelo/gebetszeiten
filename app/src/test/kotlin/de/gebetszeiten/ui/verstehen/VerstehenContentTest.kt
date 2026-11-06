package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gemeinsame Bausteine der Verstehen-Seite (VerstehenContent.kt): Glossar,
 * geteilte Zitate und Übersetzungshinweis. Vollständig und ohne Gedanken- oder
 * Halbgeviertstrich. Ob ein Zitat stimmt, prüft dieser Test nicht; dafür gibt
 * es die Belegdatei docs/superpowers/specs/2026-10-28-zitate-belege.md.
 */
class VerstehenContentTest {
    private val begriff = Regex("""\[\[([^\[\]|]+)(?:\|[^\[\]]+)?]]""")

    /**
     * Freigegebene Einträge, die kein Lehrtext als [[Begriff]] verlinkt und kein
     * anderer Glossartext nennt. Sie stehen nur im Wortlaut eines Belegs
     * (Kapitel „Freiwillig und verpönt“) und bleiben deshalb im Glossar.
     */
    private val nurImBeleg = setOf("Ṭawāf", "Ẓāhir ar-riwāya")

    private val geteilteZitate = listOf(SUNNA_MUAKKADA, SUNNA_MANDUB)

    private fun lehrTexte(): List<String> = LEHRE.flatMap { k ->
        listOf(k.reiter) + k.abschnitte.flatMap { a ->
            listOfNotNull(a.kopf, a.name, a.merksatz, a.wann, a.wieviele, a.warum, a.achtung) +
                a.mehr.flatMap { listOf(it.kopf, it.text) }
        } + k.belege.flatMap { listOf(it.quote.arabic, it.quote.german, it.quote.source) }
    }

    @Test
    fun geteilteZitateSindVollstaendig() {
        for (q in geteilteZitate) {
            assertTrue("arabic leer", q.arabic.isNotBlank())
            assertTrue("german leer", q.german.isNotBlank())
            assertTrue("source leer", q.source.isNotBlank())
        }
    }

    @Test
    fun keineGedankenOderHalbgeviertstriche() {
        val texts = buildList {
            add(TRANSLATION_NOTE)
            for (g in GLOSSAR.values) { add(g.term); add(g.text) }
            for (q in geteilteZitate) { add(q.arabic); add(q.german); add(q.source) }
        }
        for (t in texts) {
            assertFalse("Gedankenstrich in: $t", t.contains('—'))
            assertFalse("Halbgeviertstrich in: $t", t.contains('–'))
        }
    }

    @Test
    fun glossarSchluesselIstDerBegriff() {
        for ((key, g) in GLOSSAR) {
            assertEquals(key, g.term)
            assertTrue("$key: Text leer", g.text.isNotBlank())
        }
    }

    @Test
    fun jederGlossareintragWirdGebraucht() {
        val verlinkt = lehrTexte().flatMap { t -> begriff.findAll(t).map { it.groupValues[1] } }.toSet()
        for (key in GLOSSAR.keys - nurImBeleg) {
            val inAnderemEintrag = GLOSSAR.values.any { it.term != key && it.text.contains(key) }
            assertTrue("$key: weder in LEHRE verlinkt noch in einem anderen Glossartext genannt", key in verlinkt || inAnderemEintrag)
        }
        // Die Ausnahmen bleiben ehrlich: sie stehen wenigstens im Wortlaut der Lehre
        // und würden, sobald verlinkt, aus der Liste gestrichen.
        val alles = lehrTexte().joinToString("\n")
        for (key in nurImBeleg) {
            assertTrue("$key fehlt im Glossar", key in GLOSSAR)
            assertTrue("$key steht nirgends in LEHRE", alles.contains(key))
            assertFalse("$key ist inzwischen verlinkt, aus nurImBeleg streichen", key in verlinkt)
        }
    }

    @Test
    fun gebeteHabenGlossareintraege() {
        for (name in listOf("Faǧr", "Šurūq", "Ḍuḥā", "Zawāl", "Ẓuhr", "ʿAṣr", "Maġrib", "ʿIšāʾ", "Iṣfirār", "Auwābīn", "Tahaǧǧud")) {
            assertTrue("$name fehlt im Glossar", name in GLOSSAR)
        }
    }
}
