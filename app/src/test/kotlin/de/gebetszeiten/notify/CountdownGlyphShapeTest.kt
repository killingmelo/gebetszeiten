package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Fragt, was auf dem Symbol wirklich steht.
 *
 * `CountdownIconAssetsTest` prueft, dass jede Datei da ist, wohlgeformt ist,
 * vom `when` erreicht wird und keine zwei Dateien pfadgleich sind. Was keiner
 * dieser Tests bemerkt: ob `ic_countdown_6.xml` eine Sechs zeigt. Vertauscht
 * jemand im Generator die Eintraege fuer `6` und `9`, bleiben alle Dateien
 * verschieden, das Manifest wird mitgeschrieben, alles ist gruen — und auf dem
 * Geraet steht die falsche Zahl vor dem Gebet.
 *
 * Dieser Test loest die `pathData` darum zurueck auf: Rechtecke aus dem Pfad
 * lesen, per x-Ueberlappung zu Zeichenzellen gruppieren, jedes Rechteck
 * geometrisch einem Segment zuordnen und die Belegung gegen die Sollbelegung
 * halten, die hier unten steht.
 *
 * **Die Sollbelegung wird bewusst nicht aus `build_icons.py` importiert.** Ein
 * Test, der die Tabelle des Generators gegen den Generator haelt, prueft nur
 * seine eigene Kopie und faellt bei genau dem Zahlendreher nicht, den er
 * fangen soll.
 *
 * Arbeitsverzeichnis ist das Modul (`app/`), daher der relative Pfad —
 * Praezedenz: `OfficialAssetsIntegrityTest`, `NoNetworkInSharedCodeTest`.
 */
class CountdownGlyphShapeTest {

    private val drawables = File("src/main/res/drawable")

    /**
     * Sieben-Segment-Belegung, aus einem Datenblatt notiert:
     *
     *     a = oben, b = rechts oben, c = rechts unten, d = unten,
     *     e = links unten, f = links oben, g = Mitte.
     *
     * Alphabetisch sortiert, nicht in Zeichenreihenfolge — die Reihenfolge, in
     * der der Generator seine Rechtecke ausgibt, geht diesen Test nichts an.
     */
    private val soll = mapOf(
        '0' to "abcdef",
        '1' to "bc",
        '2' to "abdeg",
        '3' to "abcdg",
        '4' to "bcfg",
        '5' to "acdfg",
        '6' to "acdefg",
        '7' to "abc",
        '8' to "abcdefg",
        '9' to "abcdfg",
        // Kleines h: linker Stamm durchgehend (f+e), Querbalken (g), rechter
        // Stamm nur unten (c).
        'h' to "cefg",
    )

    /**
     * Die alleinstehende `1` faellt aus der Sieben-Segment-Form heraus: sie
     * bekommt Stamm in der Mitte (`i` oben, `j` unten), einen Fuss (`d`) und
     * eine Fahne oben links (`k`), sonst waere sie in der Statusleiste ein
     * blosser Strich. In `1h` und `10` bleibt es beim Balken (`bc`).
     */
    private val sollAlleinstehendeEins = "dijk"

    /**
     * Die 23 Symbole nach ihrer Regel, nicht nach einer abgeschriebenen Liste:
     * `1h`…`9h`, `50 40 30 20 10`, `9`…`1`. Der Dateiname ist zugleich die
     * Behauptung, die hier geprueft wird.
     */
    private val symbole: List<String> =
        (9 downTo 1).map { "${it}h" } + (5 downTo 1).map { "${it}0" } + (9 downTo 1).map { "$it" }

    @Test
    fun `jede Zeichenzelle traegt die Segmente, die der Dateiname verspricht`() {
        var zellen = 0
        symbole.forEach { text ->
            val gelesen = belegungen(text)
            assertEquals(
                "ic_countdown_$text: unerwartete Zahl von Zeichenzellen ($gelesen)",
                text.length,
                gelesen.size,
            )
            text.forEachIndexed { stelle, zeichen ->
                assertEquals(
                    "ic_countdown_$text zeigt an Stelle ${stelle + 1} nicht '$zeichen'",
                    erwartet(zeichen, alleinstehend = text.length == 1),
                    gelesen[stelle],
                )
                zellen++
            }
        }
        // 14 zweistellige (9 Stunden + 5 Zehner) und 9 einstellige Symbole:
        // 14 * 2 + 9 = 37 Zeichenzellen, nicht 46 — jedes Symbol traegt zwei
        // Zeichen zu zaehlen waere zu grosszuegig.
        assertEquals("23 Symbole mit zusammen 37 Zeichenzellen", 37, zellen)
    }

    @Test
    fun `die kritischen Paare unterscheiden sich in genau einem Segment`() {
        // Warum dieser Test ueberhaupt noetig ist: bei diesen vier Paaren
        // haengt die Ziffer an einem einzigen Rechteck. Faellt es weg oder
        // kommt eines dazu, steht eine andere Zahl da — und zwar eine, die
        // genauso plausibel aussieht.
        listOf('0' to '8', '5' to '6', '3' to '9', '1' to '7').forEach { (links, rechts) ->
            val unterschied = soll.getValue(links).toSet() xorMit soll.getValue(rechts).toSet()
            assertEquals("$links und $rechts", 1, unterschied.size)
        }
    }

    // --- Vom Pfad zurueck zur Belegung ---------------------------------------

    private infix fun Set<Char>.xorMit(other: Set<Char>): Set<Char> = (this - other) + (other - this)

    private data class Kasten(val x: Double, val y: Double, val w: Double, val h: Double) {
        val x1 get() = x + w
        val y1 get() = y + h
    }

    private fun erwartet(zeichen: Char, alleinstehend: Boolean): String {
        val belegung =
            if (alleinstehend && zeichen == '1') sollAlleinstehendeEins else soll.getValue(zeichen)
        return belegung.toList().sorted().joinToString("")
    }

    /** Belegung je Zeichenzelle, von links nach rechts. */
    private fun belegungen(text: String): List<String> =
        zellen(kaesten(pfad(text))).map { zelle ->
            zelle.map { einordnen(it, zelle) }.sorted().joinToString("")
        }

    private val alsRechteck =
        Regex("""M(-?[\d.]+),(-?[\d.]+)H(-?[\d.]+)V(-?[\d.]+)H(-?[\d.]+)Z""")

    private fun kaesten(pathData: String): List<Kasten> {
        val stuecke = pathData.split(" ").filter { it.isNotBlank() }
        return stuecke.map { stueck ->
            val treffer = alsRechteck.matchEntire(stueck)
            assertTrue("kein Rechteck: $stueck", treffer != null)
            val (x0, y0, x1, y1, zurueck) = treffer!!.destructured
            assertEquals("Rechteck nicht geschlossen: $stueck", x0, zurueck)
            Kasten(x0.toDouble(), y0.toDouble(), x1.toDouble() - x0.toDouble(), y1.toDouble() - y0.toDouble())
        }
    }

    /**
     * Zeichenzellen ueber x-Ueberlappung: was sich waagerecht beruehrt, gehoert
     * zum selben Zeichen. Zwischen zwei Zeichen steht `PAIR_GAP`, das genuegt.
     */
    private fun zellen(kaesten: List<Kasten>): List<List<Kasten>> {
        val gruppen = mutableListOf<MutableList<Kasten>>()
        var rand = Double.NEGATIVE_INFINITY
        kaesten.sortedBy { it.x }.forEach { kasten ->
            if (gruppen.isEmpty() || kasten.x > rand + EPS) {
                gruppen += mutableListOf(kasten)
                rand = kasten.x1
            } else {
                gruppen.last() += kasten
                rand = maxOf(rand, kasten.x1)
            }
        }
        return gruppen
    }

    /** Ein Rechteck geometrisch als Segment lesen — allein aus seiner Lage in
     *  der Zelle, ohne zu wissen, welches Zeichen gemeint war. */
    private fun einordnen(kasten: Kasten, zelle: List<Kasten>): Char {
        val links = zelle.minOf { it.x }
        val rechts = zelle.maxOf { it.x1 }
        val oben = zelle.minOf { it.y }
        val unten = zelle.maxOf { it.y1 }
        val breite = rechts - links
        // Strichstaerke aus der Zelle selbst, damit der Test keine Konstante
        // des Generators kennen muss.
        val strich = zelle.filter { it.h > it.w }.minOfOrNull { it.w } ?: zelle.minOf { it.h }
        val obenBuendig = nah(kasten.y, oben)
        val untenBuendig = nah(kasten.y1, unten)

        return when {
            kasten.w > kasten.h -> when {
                // Ein waagerechter Balken, der die Zelle nicht ausfuellt, kann
                // nur die Fahne der alleinstehenden `1` sein.
                !nah(kasten.w, breite) -> {
                    assertTrue("$kasten passt in kein Feld von $zelle", nah(kasten.x, links) && obenBuendig)
                    'k'
                }
                obenBuendig -> 'a'
                untenBuendig -> 'd'
                else -> 'g'
            }

            kasten.h > kasten.w -> {
                val spalte = when {
                    // Ist die Zelle nur einen Strich breit — die `1` in `1h`
                    // und `10` —, dann fallen linke und rechte Spalte
                    // zusammen: `bc` und `fe` ergaeben dasselbe Bild. Gelesen
                    // wird `bc`, wie im Datenblatt.
                    nah(breite, strich) -> "bc"
                    nah(kasten.x, links) -> "fe"
                    nah(kasten.x1, rechts) -> "bc"
                    nah(kasten.x + kasten.w / 2, (links + rechts) / 2) -> "ij"
                    else -> unbekannt(kasten, zelle)
                }
                when {
                    obenBuendig -> spalte[0]
                    untenBuendig -> spalte[1]
                    else -> unbekannt(kasten, zelle)
                }
            }

            else -> unbekannt(kasten, zelle)
        }
    }

    private fun unbekannt(kasten: Kasten, zelle: List<Kasten>): Nothing =
        throw AssertionError("$kasten passt in kein Feld von $zelle")

    private fun nah(a: Double, b: Double) = kotlin.math.abs(a - b) <= EPS

    private fun pfad(text: String): String {
        val datei = File(drawables, "ic_countdown_$text.xml")
        assertTrue("$datei fehlt", datei.isFile)
        val wurzel: Element = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(datei)
            .documentElement
        val pfade = wurzel.getElementsByTagName("path")
        assertEquals("$datei: genau ein <path> erwartet", 1, pfade.length)
        return (pfade.item(0) as Element).getAttribute("android:pathData")
    }

    private companion object {
        /** Der Generator rundet auf zwei Nachkommastellen. */
        const val EPS = 0.01
    }
}
