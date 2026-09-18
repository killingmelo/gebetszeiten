package de.gebetszeiten.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung der Migration im `settings`-Flow — das, was
 * [CalculationFallbackMigrationTest] nicht sehen kann.
 *
 * [CalculationFallbackMigrationTest] prueft die reine Funktion
 * [calculationFillsGapsFromPrefs] vollstaendig, aber nichts zwingt
 * `SettingsRepository.settings` dazu, diese Funktion auch tatsaechlich
 * aufzurufen. Fix-Runde 1 zu Aufgabe 15 fand genau diese Luecke per Mutation:
 * `calculationFillsGaps = prefs[Keys.CALCULATION_FILLS_GAPS] ?: false`
 * (ohne jeden Aufruf von `calculationFillsGapsFromPrefs`) liess ALLE
 * bestehenden Tests gruen — Bestandsnutzer mit dem alten "immer
 * rechnen"-Schalter haetten beim Update ihren Notausgang verloren, ohne dass
 * ein einziger Test das gemerkt haette.
 *
 * Dieser Waechter haelt drei Dinge quelltextlesend fest, die die Migration
 * im Rumpf des `settings`-Flows LEISTEN MUSS:
 * - den Aufruf von `calculationFillsGapsFromPrefs(...)`,
 * - das Setzen des Merkers `CALCULATION_FILLS_GAPS_MIGRATED = true`,
 * - das Entfernen des Altschluessels `USE_CALCULATED_LEGACY` — eine
 *   bindende Randbedingung des Aufgabenbriefs ("Den alten Schluessel nach
 *   der Migration entfernen").
 *
 * Praezedenz fuer das Idiom (Quelltext-Lese-Test statt Robolectric):
 * `NoNetworkInSharedCodeTest`, `PrayerAlarmSchedulerWiringTest`. Der
 * gelesene Pfad steht als Gradle-Eingabe in `app/build.gradle.kts`
 * (`mainQuellsatz`, deckt den ganzen Quellsatz `src/main/kotlin` ab, also
 * auch `SettingsRepository.kt`). Das Arbeitsverzeichnis ist das Modul
 * (`app/`), daher der relative Pfad.
 */
class CalculationFallbackMigrationWiringTest {

    private val repo = File("src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt")

    @Test
    fun `die Migration ruft calculationFillsGapsFromPrefs, setzt den Merker und entfernt den Altschluessel`() {
        val block = migrationsBlock(ohneKommentareUndTexte(text()))
        assertTrue(
            "die Migration berechnet calculationFillsGaps nicht (mehr) ueber calculationFillsGapsFromPrefs " +
                "- Bestandsnutzer mit dem alten Schalter wuerden ihren Notausgang beim Update verlieren",
            block.contains("calculationFillsGapsFromPrefs("),
        )
        assertTrue(
            "die Migration setzt CALCULATION_FILLS_GAPS_MIGRATED nicht auf true - der alte Schluessel " +
                "wuerde bei jedem Lesevorgang erneut ausgewertet",
            block.contains("Keys.CALCULATION_FILLS_GAPS_MIGRATED] = true"),
        )
        assertTrue(
            "die Migration entfernt den Altschluessel USE_CALCULATED_LEGACY nicht - er wuerde nach einer " +
                "spaeteren manuellen Aenderung wieder gelesen (bindende Randbedingung des Aufgabenbriefs)",
            block.contains("migrated.remove(Keys.USE_CALCULATED_LEGACY)"),
        )
    }

    // --- Werkzeug (Praezedenz: PrayerAlarmSchedulerWiringTest) --------------

    private fun text(): String {
        assertTrue("$repo fehlt", repo.isFile)
        return repo.readText()
    }

    /** Vom Beginn der Migrationsberechnung bis zum Ende des
     *  `if (!calculationFillsGapsMigrated) { ... }`-Blocks, per
     *  Klammerzaehlung — derselbe Trick wie `rumpfVon` in
     *  `PrayerAlarmSchedulerWiringTest`, nur ab einer Text-Marke statt einem
     *  Funktionskopf. */
    private fun migrationsBlock(text: String): String {
        val marker = "val calculationFillsGapsMigrated"
        val start = text.indexOf(marker)
        assertTrue("„$marker" + "\" nicht gefunden — wurde die Migration umgebaut?", start >= 0)
        val ifKopf = "if (!calculationFillsGapsMigrated)"
        val ifStart = text.indexOf(ifKopf, start)
        assertTrue("„$ifKopf\" nicht gefunden", ifStart >= 0)
        val auf = text.indexOf('{', ifStart)
        assertTrue("kein Rumpf hinter $ifKopf", auf >= 0)
        var tiefe = 0
        for (i in auf until text.length) {
            when (text[i]) {
                '{' -> tiefe++
                '}' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw AssertionError("unbalancierte geschweifte Klammern hinter $ifKopf")
    }

    /** Kommentare und Zeichenkettenliterale zaehlen nicht — siehe
     *  Begruendung in `OngoingWiringTest`. Ohne diesen Schritt wuerde schon
     *  der erklaerende Kommentar direkt vor dem Migrationsblock ("...siehe
     *  calculationFillsGapsFromPrefs...") den ersten Test faelschlich gruen
     *  faerben. */
    private fun ohneKommentareUndTexte(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")
        .replace(Regex("\"(\\\\.|[^\"\\\\\\n])*\""), "\"\"")
}
