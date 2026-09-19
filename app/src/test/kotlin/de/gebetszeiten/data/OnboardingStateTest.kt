package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Merker, der entscheidet, ob die Ersteinrichtung laeuft.
 *
 * Er ist der einzige Grund, warum diese Aenderung niemandem etwas
 * unterschiebt: die Werkseinstellungen bleiben, wie sie sind
 * (`persistentNotification = false`, `countdownMode = OFF`), und erst die
 * ANTWORT des Nutzers setzt sie. Bis dahin verhaelt sich die App exakt wie
 * vorher — kein Bestandsnutzer bekommt nach einem Update ungefragt eine
 * Dauerbenachrichtigung angeheftet, und keiner verliert ungefragt seine
 * fuenf stillen Eintritts-Meldungen (die `PrayerAlarmReceiver` bei aktiver
 * Dauerzeile und stillem Stil unterdrueckt).
 *
 * Geprueft wird quelltextlesend, weil der DataStore-Fluss ohne Robolectric
 * nicht ausfuehrbar ist — dasselbe Idiom wie
 * `CalculationFallbackMigrationWiringTest` und `NoNetworkInSharedCodeTest`.
 * Der gelesene Pfad steht als Gradle-Eingabe (`mainQuellsatz`).
 */
class OnboardingStateTest {

    private val repo = File("src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt")

    private fun quelltext(): String {
        assertTrue("${repo.absolutePath} fehlt", repo.isFile)
        return repo.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    @Test fun `der Feld-Vorgabewert ist erledigt, damit nichts aufblitzt`() {
        // Gegen die naheliegende „Korrektur" auf false: der Wert ist der
        // Startwert von PrayerViewModel.settings (stateIn), bevor der Store
        // gelesen ist. Auf false saehe JEDER Nutzer bei jedem Start die
        // Ersteinrichtung fuer ein paar Frames aufblitzen.
        assertEquals(true, AppSettings.DEFAULT.onboardingDone)
        assertTrue(
            "der Feld-Vorgabewert selbst steht nicht auf true",
            quelltext().replace(Regex("""\s+"""), "")
                .contains("valonboardingDone:Boolean=true"),
        )
    }

    @Test fun `aufgeloest wird gegen false, nicht gegen den Feld-Vorgabewert`() {
        // Die Stelle, an der die beiden auseinandergehen muessen. Stuende
        // dort `?: AppSettings.DEFAULT.onboardingDone`, liefe die
        // Ersteinrichtung NIE — und der Test darueber bliebe gruen.
        assertTrue(
            "onboardingDone wird nicht gegen `false` aufgeloest — dann sieht die " +
                "Ersteinrichtung niemand",
            quelltext().contains("onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false"),
        )
    }

    @Test fun `save schreibt den Merker nicht`() {
        // Sonst setzte jedes Speichern aus dem Einstellungsblatt die
        // Ersteinrichtung auf „erledigt": wer sie abbricht und stattdessen
        // die Einstellungen oeffnet, saehe sie nie wieder. Dieselbe
        // Begruendung wie bei PAUSE_NOTICE_SHOWN.
        val rumpf = rumpfVon(quelltext(), "suspend fun save(")
        assertTrue(
            "save() schreibt ONBOARDING_DONE — das ist Systemzustand, kein Einstellungswert",
            !rumpf.contains("ONBOARDING_DONE"),
        )
    }

    @Test fun `nur markOnboardingDone setzt ihn, und zwar auf true`() {
        val text = quelltext()
        val rumpf = rumpfVon(text, "suspend fun markOnboardingDone(")
        assertTrue(
            "markOnboardingDone setzt den Merker nicht",
            rumpf.replace(Regex("""\s+"""), "").contains("it[Keys.ONBOARDING_DONE]=true"),
        )
        // Genau zwei Erwaehnungen im Schreibsinn: die Aufloesung beim Lesen
        // und diese eine Zuweisung. Eine dritte waere ein zweiter Schreiber.
        assertEquals(
            "ONBOARDING_DONE wird an mehr als einer Stelle zugewiesen",
            1,
            Regex("""ONBOARDING_DONE\]\s*=""").findAll(text).count(),
        )
    }

    /** Der Rumpf einer Funktion ab ihrer Signatur, per Klammerzaehlung. */
    private fun rumpfVon(text: String, signatur: String): String {
        val start = text.indexOf(signatur)
        assertTrue("$signatur nicht gefunden", start >= 0)
        val offen = text.indexOf('{', text.indexOf(')', start))
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
        throw AssertionError("$signatur ist nicht geschlossen")
    }
}
