package de.gebetszeiten

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Telefon und Uhr zielen auf dieselbe API-Ebene.
 *
 * Der Anlass: Beim Hochladen von Wear 1016 wies Play ab — „Deine App ist
 * derzeit auf API-Ebene 34 ausgerichtet, sollte jedoch eine API-Mindestebene
 * von 35 haben." Das Telefon stand da laengst auf 36, und auch der
 * `compileSdk` der Uhr war 36; zurueckgeblieben war allein ihr `targetSdk`.
 *
 * Auffallen konnte das nicht: Play prueft die Ebene erst beim Hochladen, und
 * das Wear-Bundle wurde monatelang nicht hochgeladen, weil sich am Modul
 * nichts aenderte. Ein Wert, den nur ein Fremdsystem prueft und nur dann,
 * wenn man ihn ohnehin gerade anfasst, driftet unbemerkt.
 *
 * Dieser Test vergleicht die beiden Module miteinander statt gegen eine feste
 * Zahl: eine feste Zahl waere derselbe Wert an einer dritten Stelle und
 * muesste jedes Jahr mitgepflegt werden. Wer das Telefon anhebt, hebt damit
 * auch die Uhr an — oder er sieht hier, dass er es vergessen hat.
 *
 * Quelltextlesend, weil die Gradle-Dateien zur Testlaufzeit nicht ausgewertet
 * werden. Die gelesenen Pfade stehen als Gradle-Eingabe in
 * `app/build.gradle.kts` (`modulkanten`).
 */
class SdkZieleTest {

    private fun wert(datei: File, schluessel: String): Int {
        assertTrue("${datei.absolutePath} fehlt", datei.isFile)
        val ohneKommentare = datei.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
        val treffer = Regex("""$schluessel\s*=\s*(\d+)""").find(ohneKommentare)
        assertTrue("$schluessel steht nicht in ${datei.name}", treffer != null)
        return treffer!!.groupValues[1].toInt()
    }

    private val telefon = File("build.gradle.kts")
    private val uhr = File("../wear/build.gradle.kts")

    @Test fun `Telefon und Uhr zielen auf dieselbe API-Ebene`() {
        assertEquals(
            "Das Ziel der Uhr haengt hinter dem des Telefons zurueck. Genau so kam " +
                "die Ablehnung von Play beim Hochladen von Wear 1016 zustande.",
            wert(telefon, "targetSdk"),
            wert(uhr, "targetSdk"),
        )
    }

    @Test fun `das Ziel erfuellt die Play-Mindestanforderung`() {
        // Play verlangt seit 2025 mindestens 35. Untergrenze, keine Vorgabe:
        // der Test oben haelt die beiden Module zusammen, dieser haelt sie
        // ueber der Latte.
        listOf(telefon, uhr).forEach {
            val ziel = wert(it, "targetSdk")
            assertTrue("${it.name}: targetSdk $ziel, Play verlangt mindestens 35", ziel >= 35)
        }
    }

    @Test fun `niemand zielt hoeher, als er uebersetzt`() {
        // targetSdk ueber compileSdk laesst sich nicht bauen; als Zusage
        // steht es hier trotzdem, weil der naechste Anhebungsversuch sonst
        // mit einer Gradle-Fehlermeldung endet statt mit einem Satz.
        listOf(telefon, uhr).forEach {
            assertTrue(
                "${it.name}: targetSdk ueber compileSdk",
                wert(it, "targetSdk") <= wert(it, "compileSdk"),
            )
        }
    }
}
