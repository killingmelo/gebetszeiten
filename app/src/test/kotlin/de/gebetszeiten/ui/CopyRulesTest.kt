package de.gebetszeiten.ui

import de.gebetszeiten.prayer.timeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Schreibregeln fuer sichtbare Texte: weder Gedankenstrich (U+2014) noch
 * Halbgeviertstrich (U+2013) in einem Text, den der Nutzer sieht. Saetze
 * werden mit Punkt, Komma oder Doppelpunkt gebaut, Zeitspannen mit „bis“.
 *
 * Gelesen wird quelltextlesend (Compose ist hier nicht testbar): die
 * `<string>`-Werte aus `strings.xml` und alle String-Literale unter
 * `src/main/kotlin`. Kommentare bleiben frei. Die Literale werden zeilenweise
 * gesucht, damit ein einzelnes `"` in einem Zeichenliteral nicht die Paarung
 * der ganzen Datei verschiebt. Das Arbeitsverzeichnis ist das Modul `app`.
 */
class CopyRulesTest {

    private val verboten = Regex("[—–]|\\\\u201[34]")

    private fun ohneKommentare(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")

    @Test
    fun `kein Gedanken- oder Halbgeviertstrich in strings xml`() {
        val datei = File("src/main/res/values/strings.xml")
        assertTrue("${datei.absolutePath} fehlt", datei.isFile)
        val treffer = Regex("""<string\b[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(ohneXmlKommentare(datei.readText()))
            .map { it.groupValues[1] }
            .filter { verboten.containsMatchIn(it) }
            .toList()
        assertTrue("strings.xml enthaelt Gedanken-/Halbgeviertstriche:\n" + treffer.joinToString("\n"), treffer.isEmpty())
    }

    private fun ohneXmlKommentare(text: String): String =
        text.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun `kein Gedanken- oder Halbgeviertstrich in Kotlin-Literalen`() {
        val wurzel = File("src/main/kotlin")
        assertTrue("${wurzel.absolutePath} fehlt", wurzel.isDirectory)
        val literal = Regex("\"(?:[^\"\\\\\\n]|\\\\.)*\"")
        val treffer = wurzel.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { datei ->
                ohneKommentare(datei.readText()).lineSequence().flatMap { zeile ->
                    literal.findAll(zeile).map { it.value }.filter { verboten.containsMatchIn(it) }
                }.map { "${datei.name}: $it" }
            }
            .toList()
        assertTrue("Kotlin-Literale mit Gedanken-/Halbgeviertstrichen:\n" + treffer.joinToString("\n"), treffer.isEmpty())
    }

    @Test
    fun `Zeitspannen laufen ueber timeRange`() {
        assertEquals("11:45 bis 12:05", timeRange("11:45", "12:05"))
    }
}
