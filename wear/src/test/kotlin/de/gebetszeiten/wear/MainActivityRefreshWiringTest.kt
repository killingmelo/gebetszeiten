package de.gebetszeiten.wear

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung von `MainActivity.onStart` — die VIERTE Oberflaeche.
 *
 * Die Uhr hat vier Dinge, die heilen muessen, wenn wieder amtliche Zeiten da
 * sind: Vibrationskette, Kachel, Komplikation und den App-Bildschirm selbst.
 * Die ersten drei haengen am geteilten `notifyWearOfficialRefreshed`
 * (`WearRefresh.kt`) und sind damit gegen Vergessen geschuetzt — der
 * Bildschirm NICHT: ihn erreicht kein geteilter Weg, `MainActivity` muss
 * `refresh()` selbst aufrufen, nachdem der Abruf zurueck ist.
 *
 * Genau das ging in Fix-Runde 3 verloren (Befund von Fix-Runde 4,
 * Important 1): der Abruf wanderte — richtig — in den langlebigen Scope, und
 * `refresh()` blieb dabei nur noch VOR dem Abruf stehen. Folge im Leerfall:
 * App oeffnen, Abruf gelingt nach drei Sekunden, Kachel und Komplikation
 * heilen — und genau der Bildschirm, auf den der Nutzer gerade schaut, zeigt
 * weiter „Keine amtlichen Zeiten", bis er die App verlaesst und neu oeffnet.
 * Kein einziger Test hat das gemerkt.
 *
 * Dieser Waechter haelt quelltextlesend zwei Dinge fest, die `onStart`
 * LEISTEN MUSS:
 * - `launchWearRefresh(...)` — der Abruf laeuft im langlebigen Scope, nicht
 *   am `MainScope`, den `onDestroy` abbricht (Fix-Runde 3, Important 1; wer
 *   den Bildschirm-Fix nachtraegt, darf diese Korrektur nicht wieder
 *   einkassieren);
 * - im SELBEN `scope.launch`-Block wie der Abruf (`refreshWearOfficial(...)`)
 *   noch ein `refresh()` — der Bildschirm zeichnet sich neu, wenn Zeiten
 *   ankamen. Der Zusammenhang, nicht die blosse Reihenfolge: siehe
 *   [abrufBlock].
 *
 * Quelltextlesend statt laufend, weil das wear-Modul kein Robolectric hat
 * (nur `testImplementation(libs.junit)`) — Context-verdrahtete Funktionen
 * sind dort grundsaetzlich ungetestet. Praezedenz und Werkzeug:
 * [CalculationFillsGapsMigrationWiringTest] im selben Verzeichnis; der
 * gelesene Pfad steht als Gradle-Eingabe in `wear/build.gradle.kts`
 * (`mainQuellsatz`, deckt `src/main/kotlin` ab, also auch `MainActivity.kt`).
 */
class MainActivityRefreshWiringTest {

    private val activity = File("src/main/kotlin/de/gebetszeiten/wear/MainActivity.kt")

    @Test
    fun `onStart ruft den Abruf im langlebigen Scope und zeichnet den Bildschirm danach neu`() {
        val block = onStartRumpf(ohneKommentareUndTexte(text()))
        assertTrue(
            "onStart startet den Abruf nicht (mehr) ueber launchWearRefresh - haengt er wieder am " +
                "abbrechbaren MainScope, geht die Fortsetzung (Vibrationskette, Kachel, Komplikation) " +
                "verloren, sobald die Activity waehrend des Abrufs verschwindet",
            block.contains("launchWearRefresh("),
        )
        assertTrue(
            "onStart wartet nicht (mehr) auf refreshWearOfficial - ohne das Ergebnis weiss der " +
                "Bildschirm nicht, wann er neu zeichnen muss",
            block.contains("refreshWearOfficial("),
        )
        assertTrue(
            "im selben scope.launch-Block wie refreshWearOfficial steht kein refresh() - Kachel und " +
                "Komplikation heilen, der Bildschirm vor dem Nutzer bleibt auf „Keine amtlichen " +
                "Zeiten\" stehen (notifyWearOfficialRefreshed erreicht die Activity NICHT)",
            abrufBlock(block).contains("refresh()"),
        )
    }

    // --- Werkzeug (Praezedenz: CalculationFillsGapsMigrationWiringTest) -----

    private fun text(): String {
        assertTrue("$activity fehlt", activity.isFile)
        return activity.readText()
    }

    /** Vom Kopf von `onStart` bis zu dessen Ende, per Klammerzaehlung. */
    private fun onStartRumpf(text: String): String {
        val marker = "override fun onStart()"
        val start = text.indexOf(marker)
        assertTrue("„$marker" + "\" nicht gefunden — wurde onStart umgebaut?", start >= 0)
        return rumpfAb(text, start, marker)
    }

    /**
     * Der `scope.launch`-Block, in dem der Abruf steht — ab dem letzten
     * `scope.launch` VOR `refreshWearOfficial(`, wieder per Klammerzaehlung.
     *
     * Bewusst der ZUSAMMENHANG statt der Position (Fix-Runde 5, Minor):
     * vorher stand hier `block.substringAfter("refreshWearOfficial(")
     * .contains("refresh()")`. Das haette eine harmlose Umsortierung
     * mitgefaerbt — schoebe jemand den Abruf-Block VOR den
     * Sync-Nachhol-Block, faerbte schon dessen `if (applied) refresh()` die
     * Zusicherung gruen, auch wenn das `refresh()` hinter dem Abruf geloescht
     * waere. Der Block-Zuschnitt haelt fest, was wirklich gemeint ist: das
     * Ergebnis des Abrufs und das Neuzeichnen stehen in EINER Coroutine.
     */
    private fun abrufBlock(onStart: String): String {
        val abruf = onStart.indexOf("refreshWearOfficial(")
        assertTrue("refreshWearOfficial( kommt in onStart nicht vor", abruf >= 0)
        val start = onStart.lastIndexOf("scope.launch", abruf)
        assertTrue("der Abruf steht in keinem scope.launch-Block", start >= 0)
        return rumpfAb(onStart, start, "scope.launch (Abruf)")
    }

    /** Ab [start] bis zur schliessenden Klammer des dort beginnenden Blocks. */
    private fun rumpfAb(text: String, start: Int, marker: String): String {
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
     *  schon der erklaerende Kommentar in `onStart` den Waechter faelschlich
     *  gruen (dort stehen alle drei gesuchten Namen im Fliesstext). */
    private fun ohneKommentareUndTexte(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")
        .replace(Regex("\"(\\\\.|[^\"\\\\\\n])*\""), "\"\"")
}
