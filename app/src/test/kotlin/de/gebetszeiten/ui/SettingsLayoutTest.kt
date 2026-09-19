package de.gebetszeiten.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wo im Einstellungsblatt die Daueranzeige steht — quelltextlesend, weil
 * Compose in diesem Projekt nicht getestet wird (kein Robolectric).
 *
 * Der Anlass: „Restzeit" stand in der Sektion „Anzeige", der Schalter
 * „Dauerhafte Anzeige" als LETZTER Eintrag der Sektion „Erinnerungen", rund
 * achtzig Zeilen Oberflaeche weiter. Der Hinweis unter „Restzeit" verwies
 * dabei auf „unten die dauerhafte Anzeige" — ein „unten", an dem der
 * Schalter nicht stand und das beim Lesen des Hinweises nicht sichtbar war.
 * Wer die Restzeit auf dem Sperrbildschirm wollte, fand den zweiten noetigen
 * Schalter nicht.
 *
 * Die beiden beschreiben EINE Sache: die Daueranzeige ist die Flaeche, auf
 * der die Restzeit ueberhaupt erst erscheint. Sie gehoeren nebeneinander,
 * und dieser Waechter haelt sie dort — ein Umbau, der sie wieder trennt,
 * faellt hier auf und nicht erst beim Nutzer.
 *
 * Praezedenz fuer das Idiom: `CalculationFallbackMigrationWiringTest`
 * (Klammerzaehlung ab einer Textmarke), `NoNetworkInSharedCodeTest`. Der
 * gelesene Pfad steht als Gradle-Eingabe (`mainQuellsatz`); das
 * Arbeitsverzeichnis ist das Modul, daher der relative Pfad.
 */
class SettingsLayoutTest {

    private val sheet = File("src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt")

    private fun quelltext(): String {
        assertTrue("${sheet.absolutePath} fehlt", sheet.isFile)
        return sheet.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    /** Der Rumpf einer `SettingsSection(...) { ... }`, ab ihrer Ueberschrift. */
    private fun sektion(text: String, titel: String): String {
        val marke = "SettingsSection(stringResource(R.string.$titel))"
        val start = text.indexOf(marke)
        assertTrue("Sektion $titel nicht gefunden", start >= 0)
        val offen = text.indexOf('{', start)
        assertTrue("Sektion $titel hat keinen Rumpf", offen >= 0)
        var tiefe = 0
        for (i in offen until text.length) {
            when (text[i]) {
                '{' -> tiefe++
                '}' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(offen, i)
                }
            }
        }
        throw AssertionError("Sektion $titel ist nicht geschlossen")
    }

    // Die Klammer am Ende trennt den Schalter von seinem Hinweistext
    // `settings_persistent_hint`, der dieselbe Zeichenfolge beginnt.
    private val schalter = "R.string.settings_persistent)"
    private val regler = "R.string.settings_remaining_all"

    @Test fun `Restzeit-Regler und Daueranzeige stehen in derselben Sektion`() {
        val anzeige = sektion(quelltext(), "settings_section_display")
        assertTrue(
            "Der Restzeit-Regler ($regler) steht nicht in der Sektion „Anzeige\"",
            anzeige.contains(regler),
        )
        assertTrue(
            "Der Schalter „Dauerhafte Anzeige\" ($schalter) steht nicht in derselben Sektion wie " +
                "der Restzeit-Regler. Getrennt findet ihn niemand, der die Restzeit auf dem " +
                "Sperrbildschirm sucht — genau das war der Zustand vor dieser Aufgabe.",
            anzeige.contains(schalter),
        )
    }

    @Test fun `unter Erinnerungen steht die Daueranzeige nicht mehr`() {
        // Gegenprobe: ohne sie bliebe der Test oben auch dann gruen, wenn
        // der Schalter versehentlich ZWEIMAL im Blatt steht.
        val erinnerungen = sektion(quelltext(), "settings_section_reminders")
        assertTrue(
            "Der Schalter „Dauerhafte Anzeige\" steht (wieder) unter „Erinnerungen\". Sie ist " +
                "keine Erinnerung an ein einzelnes Gebet, sondern eine Darstellung.",
            !erinnerungen.contains(schalter),
        )
    }

    @Test fun `der Sperrbildschirm-Hinweis verweist auf keine Richtung mehr`() {
        // Er stand einmal auf „unten die dauerhafte Anzeige". Jetzt steht
        // der Schalter unmittelbar darueber — eine Richtungsangabe waere
        // wieder falsch, sobald jemand die Reihenfolge aendert.
        val text = File("src/main/res/values/strings.xml").readText()
            .substringAfter("""<string name="settings_lockscreen_hint">""")
            .substringBefore("</string>")
        assertTrue(
            "Der Hinweis nennt eine Richtung: $text",
            !text.contains("unten") && !text.contains("oben"),
        )
    }
}
