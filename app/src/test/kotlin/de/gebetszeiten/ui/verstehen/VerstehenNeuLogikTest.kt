package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Logik der neuen Seite: Kapitel zum Sprungziel, Fragekopf, Legende. */
class VerstehenNeuLogikTest {

    private fun abschnitt(teil: Teil) = Abschnitt(
        teil = teil, kopf = "k", name = teilName(teil), merksatz = "m", wann = "w", wieviele = null,
        rakaat = emptyList(), warum = "w", achtung = "a", mehr = emptyList(),
    )

    private fun kapitel(band: Band, vararg teile: Teil) =
        Kapitel(reiter = band.name, band = band, abschnitte = teile.map { abschnitt(it) }, belege = emptyList())

    @Test
    fun kapitelIndexNachTeilNichtNachNummer() {
        // Nur zwei Kapitel: Ẓuhr steht an Stelle 0, obwohl Ziel(1, ZUHR) Kapitel 1 meint.
        val lehre = listOf(
            kapitel(Band.ZUHR, Teil.ZUHR),
            kapitel(Band.TAG, Teil.SHURUQ, Teil.DUHA, Teil.ZAWAL),
        )
        assertEquals(0, kapitelIndex(Ziel(1, Teil.ZUHR), lehre))
        assertEquals(1, kapitelIndex(Ziel(5, Teil.ZAWAL), lehre))
        assertEquals(1, kapitelIndex(Ziel(5, Teil.DUHA), lehre))
        assertNull(kapitelIndex(Ziel(0, Teil.FAJR), lehre))
        assertNull(kapitelIndex(Ziel(0, Teil.FAJR), emptyList()))
    }

    @Test
    fun kapitelIndexInDerLehre() {
        val zuhr = LEHRE.indexOfFirst { it.reiter == "Ẓuhr" }
        assertTrue(zuhr >= 0)
        assertEquals(zuhr, kapitelIndex(zielVonMoment(de.gebetszeiten.prayer.Moment.DHUHR), LEHRE))
        assertEquals(zuhr, kapitelIndex(zielVon(de.gebetszeiten.prayer.Section.ZUHR), LEHRE))
    }

    @Test
    fun wannKopfBeiMakruh() {
        for (teil in listOf(Teil.SHURUQ, Teil.ZAWAL, Teil.ISFIRAR)) assertEquals("Wann gilt das?", wannKopf(teil))
        for (teil in Teil.values().filterNot { it in setOf(Teil.SHURUQ, Teil.ZAWAL, Teil.ISFIRAR) }) {
            assertEquals("Wann bete ich?", wannKopf(teil))
        }
    }

    @Test
    fun legendeMitGlossarBegriffen() {
        for (art in RakaArt.values()) {
            val stuecke = zerlege(legende(art)) { 0 }
            val begriffe = stuecke.filterIsInstance<Stueck.Begriff>()
            assertEquals("Legende $art ohne Begriff", 1, begriffe.size)
            assertTrue("Glossar ohne ${begriffe[0].key}", GLOSSAR.containsKey(begriffe[0].key))
        }
        val anzeige = RakaArt.values().associateWith { art ->
            zerlege(legende(art)) { 0 }.joinToString("") {
                when (it) {
                    is Stueck.Text -> it.s
                    is Stueck.Begriff -> it.anzeige
                    is Stueck.Verweis -> ""
                }
            }
        }
        assertEquals("Farḍ", anzeige[RakaArt.FARD])
        assertEquals("Sunna muʾakkada", anzeige[RakaArt.MUAKKADA])
        assertEquals("empfohlen", anzeige[RakaArt.MANDUB])
        assertEquals("Witr, wāǧib", anzeige[RakaArt.WITR])
    }
}
