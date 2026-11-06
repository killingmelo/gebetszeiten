package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suche der Lehrseite: Normalisierung, Reihenfolge, Zielauflösung. */
class LehrSucheTest {
    private fun abschnitt(
        teil: Teil, name: String, merksatz: String = "", wann: String = "",
        mehr: List<Frage> = emptyList(),
    ) = Abschnitt(teil, "Kopf", name, merksatz, wann, null, emptyList(), warum = "", achtung = "", mehr = mehr)

    private val lehre = listOf(
        Kapitel("Faǧr", Band.FAJR, listOf(abschnitt(Teil.FAJR, "Faǧr", merksatz = "Vor Sonnenaufgang, siehe auch Ẓuhr.")), emptyList()),
        Kapitel("Ẓuhr", Band.ZUHR, listOf(abschnitt(Teil.ZUHR, "Ẓuhr", wann = "Nach dem Zenit.{b1}")), listOf(Beleg("b1", Quote("", "Betet zur Mittagszeit.", "")))),
        Kapitel("ʿAṣr", Band.ASR, listOf(abschnitt(Teil.ASR, "ʿAṣr")), emptyList()),
        Kapitel(
            "Freiwillig", Band.TAG,
            listOf(
                abschnitt(Teil.DUHA, "Ḍuḥā", wann = "Endet vor dem [[Zawāl]]."),
                abschnitt(Teil.ISFIRAR, "Isfirār", mehr = listOf(Frage("Warum Gelbfärbung?", "Die Sonne wird [[gelb|gelblich]]."))),
            ),
            emptyList(),
        ),
    )
    private val glossar = mapOf("Zawāl" to GlossaryTerm("Zawāl", "Sonne im Zenit."))

    @Test fun normalisiertDmgUndDeutscheSchreibung() {
        assertEquals("isa", normalisiere("ʿIšāʾ"))
        assertEquals("isa", normalisiere("Ischa"))
        assertEquals("guma", normalisiere("Dschumʿa"))
        assertEquals("guma", normalisiere("Ǧumʿa"))
        assertEquals("zuhr", normalisiere("Ẓuhr"))
        assertEquals("magrib", normalisiere("Maġrib"))
    }

    @Test fun zuhrErsterTrefferIstDasZiel() {
        val t = suche("zuhr", lehre, glossar)
        assertEquals(Ziel(1, Teil.ZUHR), t.first().ziel)
        assertEquals("Ẓuhr", t.first().ort)
    }

    @Test fun asrFindetAsr() {
        assertTrue(suche("asr", lehre, glossar).any { it.ziel == Ziel(2, Teil.ASR) })
    }

    @Test fun zawalFindetAbschnittMitBegriff() {
        val t = suche("Zawal", lehre, glossar)
        assertTrue(t.any { it.ziel == Ziel(3, Teil.DUHA) && it.ort == "Ḍuḥā · Wann bete ich?" })
        assertTrue(t.any { it.ort == "Ḍuḥā · Begriff Zawāl" })
    }

    @Test fun kopfDerFrageTragtDenFragekopf() {
        val t = suche("gelbfarbung", lehre, glossar)
        assertEquals("Isfirār · Warum Gelbfärbung?", t.first().ort)
    }

    @Test fun belegWirdGefundenUndFuehrtZumAbschnitt() {
        val t = suche("mittagszeit", lehre, glossar)
        assertEquals(Ziel(1, Teil.ZUHR), t.single().ziel)
        assertEquals("Ẓuhr · Beleg", t.single().ort)
    }

    @Test fun nameVorText() {
        val t = suche("zuhr", lehre, glossar)
        // Der Text-Treffer in Faǧr (Merksatz) kommt nach dem Namenstreffer.
        assertTrue(t.indexOfFirst { it.ziel.teil == Teil.FAJR } > t.indexOfFirst { it.ziel.teil == Teil.ZUHR })
    }

    @Test fun ausschnittIstKurzUndEnthaeltDenTreffer() {
        val lang = "a".repeat(100) + " Ẓuhr " + "b".repeat(100)
        val l = listOf(Kapitel("X", Band.TAG, listOf(abschnitt(Teil.ZUHR, "Name", merksatz = lang)), emptyList()))
        val a = suche("zuhr", l, emptyMap()).single().ausschnitt
        assertTrue(a.contains("Ẓuhr"))
        assertTrue(a.length <= 84)
        assertEquals("Vor Sonnenaufgang, siehe auch Ẓuhr.", suche("sonnenaufgang", lehre, glossar).single().ausschnitt)
    }

    @Test fun leereOderUnbekannteFrageIstLeer() {
        assertTrue(suche("", lehre, glossar).isEmpty())
        assertTrue(suche("   ", lehre, glossar).isEmpty())
        assertTrue(suche("xyz", lehre, glossar).isEmpty())
    }

    @Test fun hoechstensZwanzigTreffer() {
        val viele = (1..30).map { abschnitt(Teil.ZUHR, "Ẓuhr $it") }
        assertEquals(20, suche("zuhr", listOf(Kapitel("X", Band.TAG, viele, emptyList())), emptyMap()).size)
    }
}
