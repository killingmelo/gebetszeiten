package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Netzschicht soll in ein eigenes Modul ziehen. Dafuer darf sie nichts
 * aus dem app-Modul kennen. Der Test liest den Quelltext, weil die Kante
 * sonst erst beim Modulschnitt auffaellt - und dann teuer ist.
 */
class CompositeFetcherContractTest {

    private val netFiles = listOf(
        "CompositeDiyanetFetcher.kt",
        "DiyanetDirectFetcher.kt",
        "DiyanetProxyFetcher.kt",
        "EzanVaktiFetcher.kt",
        "DiyanetHttp.kt",
        "DiyanetYearPageParser.kt",
    ).map { File("src/online/kotlin/de/gebetszeiten/official/$it") }

    private val verboten = listOf("de.gebetszeiten.data.", "de.gebetszeiten.prayer.", "de.gebetszeiten.ui.")

    /** Prueft den GANZEN Dateiinhalt, nicht nur Zeilen, die mit `import `
     *  beginnen: eine vollqualifizierte Referenz im Rumpf
     *  (`de.gebetszeiten.data.Foo.bar()`, ganz ohne eigene Importzeile)
     *  waere sonst unsichtbar. Der urspruengliche Test pruefte nur
     *  Importzeilen und uebersah deshalb genau diese Faelle. */
    @Test fun `die Abrufer kennen keine app-Typen`() {
        netFiles.forEach { f ->
            assertTrue("${f.path} fehlt - Pfad im Test anpassen", f.isFile)
            val text = f.readText()
            val treffer = verboten.filter { text.contains(it) }
            assertTrue(
                "${f.name} nennt app-Typen (Import oder vollqualifizierte " +
                    "Referenz im Rumpf), das verhindert den Modulschnitt:\n" +
                    treffer.joinToString("\n"),
                treffer.isEmpty(),
            )
        }
    }

    /**
     * Der gefaehrlichere Weg an derselben Kante, den auch der Volltext-Scan
     * oben NICHT sieht: kein Import, keine vollqualifizierte Referenz - nur
     * derselbe Paketname. Ein app-Typ, der (versehentlich oder beim
     * Refactoring) ebenfalls als `de.gebetszeiten.official` deklariert wird,
     * waere fuer die sechs Abrufer-Dateien ohne jede Textspur erreichbar.
     * Genau das geschah mit `TextNormalize`: es lag kurz in
     * `app/src/main/kotlin/de/gebetszeiten/official/`, `DiyanetProxyFetcher`
     * brauchte dafuer keine Importzeile, und der Test oben blieb gruen.
     *
     * Deshalb wird hier das GESAMTE Hauptquellset-Verzeichnis `official/`
     * (der Teil des gemeinsamen Pakets, der NICHT zu den sechs Abrufern
     * gehoert) auf eine feste, geprüfte Liste eingeschraenkt: jede
     * unerwartete Datei darin faellt auf, statt sich unsichtbar
     * mitzuschleichen. Wer hier absichtlich einen neuen, wirklich
     * netzschicht-tauglichen Typ ergaenzt, traegt ihn in [erwartet] nach -
     * das ist der Preis fuer Sichtbarkeit, den dieser Test verlangt.
     */
    @Test fun `im Hauptquellset schleicht sich kein unerwarteter Typ ins Paket official`() {
        val mainOfficialDir = File("src/main/kotlin/de/gebetszeiten/official")
        assertTrue("${mainOfficialDir.path} fehlt - Pfad im Test anpassen", mainOfficialDir.isDirectory)
        val erwartet = setOf(
            "BundledOfficialSource.kt",
            "CacheFreshness.kt",
            "OfficialTimes.kt",
            "OfficialTimesCache.kt",
        )
        val tatsaechlich = mainOfficialDir.listFiles { kandidat -> kandidat.extension == "kt" }
            .orEmpty()
            .map { it.name }
            .toSet()
        assertEquals(
            "Unerwartete Datei(en) im Paket de.gebetszeiten.official (Hauptquellset): " +
                (tatsaechlich - erwartet).joinToString().ifEmpty { "(fehlt statt zu viel: siehe Diff)" } +
                " - entweder ein neuer, ungeprueft app-gekoppelter Typ, der den sechs " +
                "Abrufer-Dateien ohne Import erreichbar wird, oder die Liste hier muss " +
                "bewusst nachgezogen werden.",
            erwartet,
            tatsaechlich,
        )
    }
}
