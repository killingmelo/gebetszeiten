package de.gebetszeiten.official

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Macht aus einer Review-Konvention einen Build-Fehler.
 *
 * Die App wirbt damit, dass der offline-Flavor **beweisbar** netzfrei ist: er
 * bekommt die INTERNET-Berechtigung gar nicht erst. Diese Zusicherung haelt
 * aber nur, solange auch niemand Netzcode in den GETEILTEN Quellsatz legt —
 * `app/src/main`, `app/src/offline`, `core-prayertimes` und `wear` werden von
 * beiden Flavors uebersetzt. Ein `HttpURLConnection` dort waere im
 * offline-Build zwar zur Laufzeit wirkungslos (keine Berechtigung), aber die
 * Behauptung „kein Netzcode" waere trotzdem falsch, und im online-Flavor
 * liefe er.
 *
 * Bisher hing das an der Aufmerksamkeit beim Lesen. Jetzt bricht der Build.
 *
 * Netzcode gehoert ausschliesslich nach `app/src/online`.
 *
 * Praezedenz fuer das Lesen von Repo-Dateien aus einem JVM-Test:
 * `OfficialAssetsIntegrityTest`. Das Arbeitsverzeichnis ist das Modul (`app/`),
 * daher die `../`-Pfade.
 */
class NoNetworkInSharedCodeTest {

    /** Bezeichner, die es in geteiltem Code nicht geben darf. Bewusst
     *  API-Namen und keine Wortfelder: „Netz" oder „online" stehen in diesem
     *  Projekt zu Recht in vielen Kommentaren. */
    private val verboten = listOf(
        "java.net.",
        // NICHT von „java.net." mitgefangen: nach „java" folgt ein „x".
        // `javax.net.ssl.SSLException` und Verwandte sind Netzcode wie jeder
        // andere — seit `fetchErrorText` sie behandelt, ist die Luecke keine
        // theoretische mehr.
        "javax.net.ssl",
        "HttpURLConnection",
        "URLConnection",
        "openConnection",
        "okhttp3",
        "OkHttpClient",
        "retrofit2",
        "android.webkit",
    )

    private val geteilt = listOf(
        File("src/main"),
        File("src/offline"),
        File("../core-prayertimes/src/main"),
        File("../wear/src/main"),
    )

    @Test
    fun `kein Netzcode im geteilten Quellsatz`() {
        val treffer = mutableListOf<String>()
        for (wurzel in geteilt) {
            assertTrue("Pfad existiert nicht — Modulstruktur geaendert? $wurzel", wurzel.isDirectory)
            wurzel.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { datei ->
                    ohneKommentare(datei.readText()).lineSequence().forEachIndexed { i, zeile ->
                        verboten.filter { it in zeile }.forEach { marke ->
                            treffer += "${datei.path}:${i + 1}  $marke"
                        }
                    }
                }
        }
        assertTrue(
            "Netzcode im geteilten Quellsatz — er gehoert nach app/src/online:\n" +
                treffer.joinToString("\n"),
            treffer.isEmpty(),
        )
    }

    @Test
    fun `die INTERNET-Berechtigung steht nur im online-Flavor`() {
        // Das ist die andere Haelfte der Zusicherung: selbst wenn jemand
        // Netzcode einschmuggelte, koennte der offline-Build ihn nicht
        // ausfuehren — vorausgesetzt, die Berechtigung bleibt, wo sie ist.
        val mitBerechtigung = listOf(
            File("src/main/AndroidManifest.xml"),
            File("src/offline/AndroidManifest.xml"),
            File("src/online/AndroidManifest.xml"),
            File("../wear/src/main/AndroidManifest.xml"),
            // :net-diyanet traegt die Berechtigung jetzt selbst (Modulschnitt
            // Aufgabe 2) - src/online/AndroidManifest.xml hat sie nicht mehr,
            // sie kommt ueber die Modulabhaengigkeit `onlineImplementation`.
            File("../net-diyanet/src/main/AndroidManifest.xml"),
        ).filter { it.isFile }
            .filter { manifest ->
                ohneXmlKommentare(manifest.readText())
                    .contains(Regex("""uses-permission[^>]*android\.permission\.INTERNET"""))
            }
            .map { it.path }

        assertTrue(
            "INTERNET darf ausschliesslich im online-Flavor bzw. dem Modul stehen, " +
                "das nur er einbindet, gefunden in: $mitBerechtigung",
            mitBerechtigung.size == 1 &&
                mitBerechtigung.single().let { it.contains("online") || it.contains("net-diyanet") },
        )
    }

    /** Kommentare zaehlen nicht. Dieses Projekt erklaert seine Entscheidungen
     *  ausfuehrlich, und ein Satz wie „hier darf kein HttpURLConnection
     *  stehen" ist genau das Gegenteil eines Verstosses.
     *
     *  Das `//` wird nur entfernt, wenn KEIN `:` davorsteht — sonst
     *  verschluckte die Zeile alles hinter einem `https://` und koennte damit
     *  einen echten Treffer dahinter verstecken. */
    private fun ohneKommentare(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")

    private fun ohneXmlKommentare(text: String): String =
        text.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
}
