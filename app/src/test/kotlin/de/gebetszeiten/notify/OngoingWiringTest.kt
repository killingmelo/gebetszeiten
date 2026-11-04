package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung der Dauerbenachrichtigung — das, was kein reiner Test
 * sehen kann.
 *
 * `ongoingTexts` entscheidet die drei Texte und ist ohne Geraet pruefbar
 * ([OngoingTextTest]). Was `updateOngoing` damit macht, ist es nicht — dort
 * braeuchte es Robolectric oder ein Geraet. Genau in dieser Luecke lag ein
 * Befund der Pruefung: die genaue Uhrzeit aus dem aufgeklappten Text zu
 * entfernen liess alles gruen.
 *
 * Das faellt jetzt hier auf. Das Mittel ist das Idiom des Hauses: ein
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

    @Test
    fun `die Dauerbenachrichtigung setzt keinen Untertitel`() {
        // Samsung setzt den Untertitel in die Titelzeile; dort kuerzte der
        // Ort die Restzeit („Noch 50+ Min bis…  Istanbul"). Deshalb gibt es
        // ihn nicht mehr, und er soll auch nicht still zurueckkommen.
        assertTrue("$notifier fehlt", notifier.isFile)
        val rumpf = rumpfVon(ohneKommentareUndTexte(notifier.readText()), "fun updateOngoing(")
        assertTrue("updateOngoing setzt wieder setSubText", !rumpf.contains("setSubText("))
    }

    @Test
    fun `die drei Texte kommen unveraendert aus ongoingTexts`() {
        // `updateOngoing` setzt nur noch zusammen. Schiebt jemand einen
        // eigenen Wortlaut dazwischen — etwa `bigText("")`, die Mutation aus
        // der Pruefung —, ist die Entscheidung wieder an einem Ort, den kein
        // Test ausfuehrt. Also darf jeder dieser drei Setzer GENAU EINMAL
        // vorkommen und GENAU sein Feld bekommen.
        assertTrue("$notifier fehlt", notifier.isFile)
        val rumpf = rumpfVon(ohneKommentareUndTexte(notifier.readText()), "fun updateOngoing(")
            .replace(Regex("""\s+"""), "")
        listOf(
            "setContentTitle" to "title",
            "setContentText" to "contentText",
            "bigText" to "bigText",
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

    @Test
    fun `updateOngoing bestimmt Symbol und Modus nicht mehr selbst`() {
        // Seit es die Vorschau im Onboarding gibt, muessen echte Anzeige und
        // Vorschau aus DERSELBEN Rechnung kommen — sonst verspricht die
        // Vorschau ein Symbol, das die Anzeige nie zeigt. Die Rechnung heisst
        // `ongoing(...)`. Baut jemand sie hier wieder von Hand nach
        // (`countdownGlyph(remaining, countdown)`, `ongoingMode(...)`), faellt
        // dieser Test — nicht der Nutzer.
        assertTrue("$notifier fehlt", notifier.isFile)
        val rumpf = rumpfVon(ohneKommentareUndTexte(notifier.readText()), "fun updateOngoing(")
            .replace(Regex("""\s+"""), "")
        assertEquals(
            "updateOngoing ruft ongoing(...) nicht genau einmal auf",
            1,
            Regex("""(?<![A-Za-z])ongoing\(""").findAll(rumpf).count(),
        )
        listOf("countdownGlyph(", "ongoingMode(", "ongoingTexts(", "remainingStepShort(").forEach {
            assertTrue(
                "updateOngoing rechnet mit $it wieder selbst, statt ongoing(...) zu fragen",
                !rumpf.contains(it),
            )
        }
        assertTrue(
            "das Symbol kommt nicht aus demselben Ergebnis wie die Texte",
            rumpf.contains("setSmallIcon(countdownIconRes(anzeige.glyph))"),
        )
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
     * (ein Kommentar `setSubText(...)` waere kein Aufruf), und ein `(` oder `{`
     * in einem Text wuerde die Klammerzaehlung verrutschen lassen. Die
     * leeren Anfuehrungszeichen bleiben stehen, damit `bigText("")` als
     * `bigText("")` sichtbar bleibt und auffliegt.
     */
    private fun ohneKommentareUndTexte(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")
        .replace(Regex("\"(\\\\.|[^\"\\\\\\n])*\""), "\"\"")
}
