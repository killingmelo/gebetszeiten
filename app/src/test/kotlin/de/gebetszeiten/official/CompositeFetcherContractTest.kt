package de.gebetszeiten.official

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

    @Test fun `die Abrufer kennen keine app-Typen`() {
        netFiles.forEach { f ->
            assertTrue("${f.path} fehlt - Pfad im Test anpassen", f.isFile)
            val treffer = f.readLines()
                .filter { it.startsWith("import ") }
                .filter { zeile -> verboten.any { zeile.contains(it) } }
            assertTrue(
                "${f.name} importiert app-Typen, das verhindert den Modulschnitt:\n" +
                    treffer.joinToString("\n"),
                treffer.isEmpty(),
            )
        }
    }
}
