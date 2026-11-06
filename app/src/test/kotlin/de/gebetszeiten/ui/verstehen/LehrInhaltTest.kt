package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inhaltsregeln der Lehre. Gelten für jedes Kapitel in [LEHRE], auch für später hinzugefügte. */
class LehrInhaltTest {
    private val begriff = Regex("""\[\[([^\[\]|]+)(?:\|[^\[\]]+)?]]""")
    private val verweis = Regex("""\{([a-z0-9-]+)}""")
    private val quelle = Regex("""\([^)]*\d+\)""")

    private fun texte(a: Abschnitt): List<String> =
        listOfNotNull(a.kopf, a.name, a.merksatz, a.wann, a.wieviele, a.warum, a.achtung) +
            a.mehr.flatMap { listOf(it.kopf, it.text) }

    private fun alleTexte(k: Kapitel): List<String> =
        listOf(k.reiter) + k.abschnitte.flatMap { texte(it) } +
            k.belege.flatMap { listOf(it.id, it.quote.arabic, it.quote.german, it.quote.source) }

    private fun Abschnitt.rakaFard() = rakaat.filter { it.art == RakaArt.FARD }.sumOf { it.anzahl }

    @Test fun keinGedankenstrichUndKeinHalbgeviertstrich() {
        for (k in LEHRE) for (t in alleTexte(k)) {
            assertTrue("${k.reiter}: Strich in „$t“", 0x2013.toChar() !in t && 0x2014.toChar() !in t)
        }
    }

    @Test fun jederBegriffStehtImGlossar() {
        for (k in LEHRE) for (t in alleTexte(k)) for (m in begriff.findAll(t)) {
            assertTrue("${k.reiter}: Begriff ${m.groupValues[1]} fehlt im Glossar", m.groupValues[1] in GLOSSAR)
        }
    }

    @Test fun jederVerweisGehoertZuEinemBelegDesKapitels() {
        for (k in LEHRE) {
            val ids = k.belege.map { it.id }.toSet()
            for (t in alleTexte(k)) for (m in verweis.findAll(t)) {
                assertTrue("${k.reiter}: unbekannter Beleg ${m.groupValues[1]}", m.groupValues[1] in ids)
            }
        }
    }

    @Test fun keinBelegBleibtUnbenutzt() {
        for (k in LEHRE) {
            val benutzt = k.abschnitte.flatMap { texte(it) }.flatMap { verweis.findAll(it).map { m -> m.groupValues[1] } }.toSet()
            for (b in k.belege) assertTrue("${k.reiter}: Beleg ${b.id} unbenutzt", b.id in benutzt)
        }
    }

    @Test fun belegIdsHabenGueltigeForm() {
        for (k in LEHRE) for (b in k.belege) {
            assertTrue("${k.reiter}: ungültige Beleg-ID ${b.id}", Regex("[a-z0-9-]+").matches(b.id))
        }
    }

    @Test fun belegIdsSindEindeutig() {
        for (k in LEHRE) assertEquals(k.reiter, k.belege.size, k.belege.map { it.id }.toSet().size)
    }

    @Test fun pflichtfelderSindGefuellt() {
        for (k in LEHRE) for (a in k.abschnitte) {
            val w = "${k.reiter}/${a.name}"
            assertTrue("$w: merksatz leer", a.merksatz.isNotBlank())
            assertTrue("$w: wann leer", a.wann.isNotBlank())
            assertTrue("$w: warum leer", a.warum.isNotBlank())
            assertTrue("$w: achtung leer", a.achtung.isNotBlank())
        }
        for (k in LEHRE) for (b in k.belege) {
            assertTrue("${b.id}: arabisch leer", b.quote.arabic.isNotBlank())
            assertTrue("${b.id}: deutsch leer", b.quote.german.isNotBlank())
            assertTrue("${b.id}: quelle leer", b.quote.source.isNotBlank())
        }
    }

    @Test fun jedesWarumNenntEineQuelleInKlammern() {
        for (k in LEHRE) for (a in k.abschnitte) {
            assertTrue("${k.reiter}/${a.name}: warum ohne Quellenangabe", quelle.containsMatchIn(a.warum))
        }
    }

    @Test fun fardZahlenStimmen() {
        val erwartet = mapOf("Faǧr" to 2, "Ẓuhr" to 4, "ʿAṣr" to 4, "Maġrib" to 3, "ʿIšāʾ" to 4)
        for (k in LEHRE) {
            val soll = erwartet[k.reiter] ?: continue
            for (a in k.abschnitte) {
                assertEquals("${k.reiter}: Farḍ in der Folge", soll, a.rakaFard())
            }
        }
    }

    @Test fun ǧumʿaHatZweiFardUndVierVierSunna() {
        val a = LEHRE.first { it.reiter == "Ẓuhr" }.abschnitte.first()
        assertEquals(
            listOf(RakaArt.MUAKKADA to 4, RakaArt.FARD to 2, RakaArt.MUAKKADA to 4),
            a.rakaatGumua!!.map { it.art to it.anzahl },
        )
    }

    @Test fun freiwilligUndVerponntHatKeinFardUndRechnetDieGerechnetenTeile() {
        val k = LEHRE.firstOrNull { it.reiter == "Freiwillig und verpönt" } ?: return
        assertTrue(k.abschnitte.none { a -> a.rakaat.any { it.art == RakaArt.FARD } })
        assertEquals(
            listOf(Teil.SHURUQ, Teil.DUHA, Teil.ZAWAL, Teil.ISFIRAR, Teil.AWWABIN, Teil.TAHAJJUD),
            k.abschnitte.map { it.teil },
        )
        for (t in GERECHNET) assertTrue("Abschnitt $t fehlt", k.abschnitte.any { it.teil == t })
    }

    @Test fun nummerierungLaeuftDurch() {
        for (k in LEHRE) assertEquals(k.belege.size, nummern(k).size)
    }

    @Test fun alleKapitelDa() {
        assertEquals(6, LEHRE.size)
        assertEquals(
            listOf("Faǧr", "Ẓuhr", "ʿAṣr", "Maġrib", "ʿIšāʾ", "Freiwillig und verpönt"),
            LEHRE.map { it.reiter },
        )
        assertEquals(
            setOf(Teil.SHURUQ, Teil.DUHA, Teil.ZAWAL, Teil.ISFIRAR, Teil.AWWABIN, Teil.TAHAJJUD),
            LEHRE[5].abschnitte.map { it.teil }.toSet(),
        )
    }
}
