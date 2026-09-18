package de.gebetszeiten.wear

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung der Migration in `WearSettings.resolveCalculationFillsGaps`
 * — das, was [CalculationFillsGapsMigrationTest] nicht sehen kann.
 *
 * [CalculationFillsGapsMigrationTest] prueft die reine Funktion
 * [calculationFillsGapsFromPrefs] vollstaendig, aber nichts zwingt
 * `resolveCalculationFillsGaps` dazu, diese Funktion auch tatsaechlich
 * aufzurufen. Genau dasselbe Muster wie `CalculationFallbackMigrationWiringTest`
 * am Telefon (Aufgabe 15, Fix-Runde 1) — dort fand ein Mutationstest per
 * Hand genau diese Luecke: `calculationFillsGaps = prefs[Keys.
 * CALCULATION_FILLS_GAPS] ?: false` (ohne jeden Aufruf von
 * `calculationFillsGapsFromPrefs`) liess ALLE bestehenden Tests gruen —
 * Bestandsnutzer der Uhr mit dem alten "immer rechnen"-Schalter haetten
 * beim Update ihren Notausgang verloren, ohne dass ein einziger Test das
 * gemerkt haette.
 *
 * Dieser Waechter haelt drei Dinge quelltextlesend fest, die
 * `resolveCalculationFillsGaps` LEISTEN MUSS:
 * - den Aufruf von `calculationFillsGapsFromPrefs(...)`,
 * - das Setzen des Merkers `CALCULATION_FILLS_GAPS_MIGRATED] = true`,
 * - das Entfernen des Altschluessels `USE_CALCULATED_LEGACY`.
 *
 * Praezedenz: `CalculationFallbackMigrationWiringTest` (Telefon). Der
 * gelesene Pfad steht als Gradle-Eingabe in `wear/build.gradle.kts`
 * (`mainQuellsatz`, deckt `src/main/kotlin` ab, also auch
 * `WearSettings.kt`). Das Arbeitsverzeichnis ist das Modul (`wear/`),
 * daher der relative Pfad.
 */
class CalculationFillsGapsMigrationWiringTest {

    private val settings = File("src/main/kotlin/de/gebetszeiten/wear/WearSettings.kt")

    @Test
    fun `die Migration ruft calculationFillsGapsFromPrefs, setzt den Merker und entfernt den Altschluessel`() {
        val block = migrationsFunktion(ohneKommentareUndTexte(text()))
        assertTrue(
            "resolveCalculationFillsGaps berechnet nicht (mehr) ueber calculationFillsGapsFromPrefs " +
                "- Bestandsnutzer der Uhr mit dem alten Schalter wuerden ihren Notausgang beim Update " +
                "verlieren",
            block.contains("calculationFillsGapsFromPrefs("),
        )
        assertTrue(
            "die Migration setzt CALCULATION_FILLS_GAPS_MIGRATED nicht auf true - der alte Schluessel " +
                "wuerde bei jedem Lesevorgang erneut ausgewertet",
            block.contains("CALCULATION_FILLS_GAPS_MIGRATED] = true"),
        )
        assertTrue(
            "die Migration entfernt den Altschluessel USE_CALCULATED_LEGACY nicht - er wuerde nach " +
                "einer spaeteren manuellen Aenderung wieder gelesen",
            block.contains("prefs.remove(USE_CALCULATED_LEGACY)"),
        )
    }

    // --- Werkzeug (Praezedenz: CalculationFallbackMigrationWiringTest) -----

    private fun text(): String {
        assertTrue("$settings fehlt", settings.isFile)
        return settings.readText()
    }

    /** Vom Kopf der Funktion `resolveCalculationFillsGaps` bis zu ihrem Ende,
     *  per Klammerzaehlung — derselbe Trick wie `migrationsBlock` im
     *  Telefon-Pendant, nur ab einem Funktionskopf statt einer Text-Marke. */
    private fun migrationsFunktion(text: String): String {
        val marker = "private suspend fun resolveCalculationFillsGaps"
        val start = text.indexOf(marker)
        assertTrue("„$marker" + "\" nicht gefunden — wurde die Migration umgebaut?", start >= 0)
        val auf = text.indexOf('{', start)
        assertTrue("kein Rumpf hinter $marker", auf >= 0)
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
        throw AssertionError("unbalancierte geschweifte Klammern hinter $marker")
    }

    /** Kommentare und Zeichenkettenliterale zaehlen nicht — sonst faerbte
     *  schon der erklaerende Kommentar vor der Funktion den ersten Test
     *  faelschlich gruen. */
    private fun ohneKommentareUndTexte(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")
        .replace(Regex("\"(\\\\.|[^\"\\\\\\n])*\""), "\"\"")
}
