package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.ui.verstehen.Stueck.Begriff
import de.gebetszeiten.ui.verstehen.Stueck.Text
import de.gebetszeiten.ui.verstehen.Stueck.Verweis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Textauszeichnung, Endnoten-Nummerierung und Rakʿa-Gewicht der Lehrseite. */
class LehrTextTest {
    private fun abschnitt(
        merksatz: String = "", wann: String = "", wieviele: String? = null,
        warum: String = "", achtung: String = "", mehr: List<Frage> = emptyList(),
    ) = Abschnitt(
        Teil.ZUHR, "Mittagsgebet", "Ẓuhr", merksatz, wann, wieviele, emptyList(),
        warum = warum, achtung = achtung, mehr = mehr,
    )

    private fun kapitel(vararg a: Abschnitt) = Kapitel(
        "Ẓuhr", Band.ZUHR, a.toList(),
        listOf("a", "b").map { Beleg(it, Quote("", "", "")) },
    )

    @Test fun zerlegtTextBegriffUndVerweis() {
        assertEquals(
            listOf(Text("Ab dem "), Begriff("Zawāl", "Zawāl"), Text("."), Verweis(1)),
            zerlege("Ab dem [[Zawāl]].{a}") { 1 },
        )
    }

    @Test fun begriffMitAnzeige() {
        assertEquals(listOf(Begriff("A", "b")), zerlege("[[A|b]]") { 1 })
        assertEquals(
            listOf(Begriff("Sunna muʾakkada", "bekräftigte Sunna")),
            zerlege("[[Sunna muʾakkada|bekräftigte Sunna]]") { 1 },
        )
    }

    @Test fun reinerTextBleibtEinStueck() {
        assertEquals(listOf(Text("Nur Text, 12 {Klammer} [eckig]")), zerlege("Nur Text, 12 {Klammer} [eckig]") { 1 })
        assertEquals(emptyList<Stueck>(), zerlege("") { 1 })
    }

    @Test fun nummernNachErstemAuftreten() {
        val k = kapitel(
            abschnitt(merksatz = "x {b}", wann = "y {a}", mehr = listOf(Frage("k {b}", "t"))),
        )
        assertEquals(mapOf("b" to 1, "a" to 2), nummern(k))
    }

    @Test fun nummernFolgenFeldReihenfolgeUndAbschnitten() {
        val k = kapitel(
            abschnitt(achtung = "{a}", warum = "{b}"),
        )
        assertEquals(mapOf("b" to 1, "a" to 2), nummern(k))
        val k2 = kapitel(
            abschnitt(mehr = listOf(Frage("f", "{a}"))),
            abschnitt(merksatz = "{b}"),
        )
        assertEquals(mapOf("a" to 1, "b" to 2), nummern(k2))
    }

    @Test fun unbekannterVerweisWirft() {
        assertThrows(IllegalArgumentException::class.java) {
            nummern(kapitel(abschnitt(merksatz = "{zz}")))
        }
    }

    @Test fun rakaGewicht() {
        assertEquals(2f, rakaGewicht(RakaBlock(RakaArt.MUAKKADA, 2)), 0f)
        assertEquals(3f, rakaGewicht(RakaBlock(RakaArt.WITR, 3)), 0f)
        assertEquals(2f, rakaGewicht(RakaBlock(RakaArt.FARD, 1)), 0f)
    }
}
