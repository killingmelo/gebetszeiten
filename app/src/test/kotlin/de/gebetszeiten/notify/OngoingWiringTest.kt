package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung der Dauerbenachrichtigung — das, was kein reiner Test
 * sehen kann.
 *
 * `ongoingTexts` entscheidet die vier Texte und ist ohne Geraet pruefbar
 * ([OngoingTextTest]). Was `updateOngoing` damit macht und was die vier
 * Aufrufstellen ihr mitgeben, ist es nicht — dort braeuchte es Robolectric
 * oder ein Geraet. Genau in dieser Luecke lagen zwei Befunde der Pruefung:
 *
 * - `city = settings.city` an EINER der vier Aufrufstellen zu loeschen liess
 *   alles gruen; der Vorgabewert `null` macht das Weglassen lautlos.
 * - Die genaue Uhrzeit aus dem aufgeklappten Text zu entfernen liess alles
 *   gruen.
 *
 * Beides faellt jetzt hier auf. Das Mittel ist das Idiom des Hauses: ein
 * Test, der den Quelltext liest — Praezedenz `NoNetworkInSharedCodeTest`,
 * `CountdownIconAssetsTest`, `OfficialAssetsIntegrityTest`. Das
 * Arbeitsverzeichnis ist das Modul (`app/`), daher die relativen Pfade.
 *
 * Die gelesenen Pfade stehen als Gradle-Eingabe in `app/build.gradle.kts`
 * (`mainQuellsatz`). Ueber die Uebersetzung waeren sie zwar mittelbar schon
 * Eingabe, aber nur fuer Aenderungen, die den Bytecode veraendern — eine
 * reine Umformatierung von `PrayerNotifier.kt` liesse diesen Test sonst als
 * UP-TO-DATE ueberspringen.
 */
class OngoingWiringTest {

    private val notifier = File("src/main/kotlin/de/gebetszeiten/notify/PrayerNotifier.kt")
    private val quellsatz = File("src/main")

    @Test
    fun `jede Aufrufstelle von updateOngoing nennt den Ort`() {
        // Der Untertitel ist der aktive Ort. Fehlt das Argument, zeigt die
        // Kopfzeile an dieser Stelle einfach keinen — ohne Fehler, ohne
        // Absturz, ohne dass es jemandem auffaellt.
        assertTrue("Pfad existiert nicht — Modulstruktur geaendert? $quellsatz", quellsatz.isDirectory)
        val ohneOrt = mutableListOf<String>()
        var aufrufe = 0
        quellsatz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { datei ->
                val text = ohneKommentareUndTexte(datei.readText())
                Regex("""(?<!fun )updateOngoing\(""").findAll(text).forEach { treffer ->
                    aufrufe++
                    val klammer = treffer.range.last
                    val args = argumente(datei.path, text, klammer)
                    if (!args.contains("city =")) ohneOrt += "${datei.path}: $args"
                }
            }
        assertEquals("Aufrufstellen von updateOngoing", 4, aufrufe)
        assertTrue(
            "updateOngoing ohne `city =` — der Vorgabewert null laesst den Ort lautlos weg:\n" +
                ohneOrt.joinToString("\n"),
            ohneOrt.isEmpty(),
        )
    }

    @Test
    fun `die vier Texte kommen unveraendert aus ongoingTexts`() {
        // `updateOngoing` setzt nur noch zusammen. Schiebt jemand einen
        // eigenen Wortlaut dazwischen — etwa `bigText("")`, die Mutation aus
        // der Pruefung —, ist die Entscheidung wieder an einem Ort, den kein
        // Test ausfuehrt. Also darf jeder dieser vier Setzer GENAU EINMAL
        // vorkommen und GENAU sein Feld bekommen.
        assertTrue("$notifier fehlt", notifier.isFile)
        val rumpf = rumpfVon(ohneKommentareUndTexte(notifier.readText()), "fun updateOngoing(")
            .replace(Regex("""\s+"""), "")
        listOf(
            "setContentTitle" to "title",
            "setContentText" to "contentText",
            "bigText" to "bigText",
            "setSubText" to "subText",
        ).forEach { (setzer, feld) ->
            assertEquals(
                "$setzer steht nicht genau einmal in updateOngoing",
                1,
                Regex(Regex.escape("$setzer(")).findAll(rumpf).count(),
            )
            assertTrue(
                "$setzer bekommt nicht texts.$feld — dann ist der Wortlaut wieder ungeprueft",
                rumpf.contains("$setzer(texts.$feld)"),
            )
        }
    }

    // --- Werkzeug ------------------------------------------------------------

    /** Die Argumentliste ab der oeffnenden Klammer an [klammer]. */
    private fun argumente(pfad: String, text: String, klammer: Int): String {
        var tiefe = 0
        for (i in klammer until text.length) {
            when (text[i]) {
                '(' -> tiefe++
                ')' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(klammer + 1, i)
                }
            }
        }
        throw AssertionError("$pfad: unbalancierte Klammern ab Position $klammer")
    }

    /** Der Rumpf der mit [kopf] beginnenden Funktion, per Klammerzaehlung. */
    private fun rumpfVon(text: String, kopf: String): String {
        val start = text.indexOf(kopf)
        assertTrue("$kopf nicht gefunden", start >= 0)
        val signatur = argumente(notifier.path, text, start + kopf.length - 1)
        val auf = text.indexOf('{', start + kopf.length + signatur.length)
        assertTrue("kein Rumpf hinter $kopf", auf >= 0)
        var tiefe = 0
        for (i in auf until text.length) {
            when (text[i]) {
                '{' -> tiefe++
                '}' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(auf + 1, i)
                }
            }
        }
        throw AssertionError("unbalancierte geschweifte Klammern hinter $kopf")
    }

    /**
     * Kommentare und Zeichenkettenliterale zaehlen nicht — und zwar aus zwei
     * Gruenden: dieses Projekt erklaert seine Entscheidungen ausfuehrlich
     * (ein Kommentar `city = ...` waere kein Argument), und ein `(` oder `{`
     * in einem Text wuerde die Klammerzaehlung verrutschen lassen. Die
     * leeren Anfuehrungszeichen bleiben stehen, damit `bigText("")` als
     * `bigText("")` sichtbar bleibt und auffliegt.
     */
    private fun ohneKommentareUndTexte(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")
        .replace(Regex("\"(\\\\.|[^\"\\\\\\n])*\""), "\"\"")
}
