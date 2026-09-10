package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Haelt die Symbole und den Code zusammen, der sie auswaehlt.
 *
 * Die Grafiken kommen aus `tools/notification-icons/build_icons.py`. Ein
 * Zahlendreher dort — zweimal dieselbe Ziffer, eine vergessene Datei, ein
 * `when`-Zweig, der auf das falsche Symbol zeigt — waere im Quelltext
 * unsichtbar und faellt in der Statusleiste erst auf, wenn die Uhrzeit
 * stimmt. Also faellt er hier auf.
 *
 * Praezedenz fuer das Lesen von Repo-Dateien aus einem JVM-Test:
 * `OfficialAssetsIntegrityTest` und `NoNetworkInSharedCodeTest`. Das
 * Arbeitsverzeichnis ist das Modul (`app/`), daher die relativen Pfade.
 */
class CountdownIconAssetsTest {

    private val drawables = File("src/main/res/drawable")
    private val manifest = File("../tools/notification-icons/icons.sha256")

    /** Der bestehende Mond, den sich `Now` und `None` absichtlich teilen. */
    private val mond = "ic_notification"

    /**
     * Alle Glyph-Werte, die `countdownGlyph` ueberhaupt erzeugen kann —
     * ERZEUGT, nicht abgeschrieben. Waechst die Funktion um einen Wert und
     * vergisst jemand die Grafik, faellt dieser Test, und keine Liste hier
     * verdeckt es.
     */
    private val erreichbar: List<CountdownGlyph> = buildList {
        add(countdownGlyph(null, enabled = true))
        add(countdownGlyph(Duration.ofHours(3), enabled = false))
        for (minute in 0..14 * 60) {
            add(countdownGlyph(Duration.ofMinutes(minute.toLong()), enabled = true))
        }
    }.distinct()

    /**
     * Der Dateiname folgt einer Regel, nicht einer Tabelle: eine abgeschriebene
     * Tabelle koennte denselben Zahlendreher enthalten wie das `when`.
     */
    private fun dateiname(glyph: CountdownGlyph): String = when (glyph) {
        is CountdownGlyph.Hours -> "ic_countdown_${glyph.hours}h"
        is CountdownGlyph.Minutes -> "ic_countdown_${glyph.minutes}"
        CountdownGlyph.Now, CountdownGlyph.None -> mond
    }

    private val countdownSymbole: List<String> =
        erreichbar.map(::dateiname).distinct().filterNot { it == mond }

    @Test
    fun `fuer jeden erreichbaren Glyph gibt es eine Datei`() {
        val fehlend = erreichbar.map(::dateiname).distinct()
            .filterNot { File(drawables, "$it.xml").isFile }
        assertTrue("Symbol ohne Datei: $fehlend", fehlend.isEmpty())
    }

    @Test
    fun `es sind 23 Symbole plus der Mond`() {
        // 1h…9h (9) + 50 40 30 20 10 (5) + 9…1 (9) = 23 Grafiken, dazu Now und
        // None, die beide den Mond zeigen.
        //
        // Glyph-Werte sind es aber 26, nicht 25: `Hours(9)` gibt es zweimal,
        // gedeckelt (ab 10 h) und ungedeckelt (9 h…9:59 h). Beide zeigen
        // dasselbe Bild — deshalb faellt `capped` in `countdownIconRes` heraus,
        // und deshalb steht die Zahl hier und nicht nur im Kopf.
        assertEquals("erreichbare Glyph-Werte: $erreichbar", 26, erreichbar.size)
        assertEquals(23, countdownSymbole.size)
    }

    @Test
    fun `countdownIconRes zeigt auf genau die erwartete Datei`() {
        // Bindet das `when` an die Dateien: ein Zweig, der auf das Symbol der
        // Nachbarminute zeigt, kompiliert anstandslos und faellt nur hier auf.
        erreichbar.forEach { glyph ->
            assertEquals(
                "$glyph zeigt auf das falsche Symbol",
                dateiname(glyph),
                resName(countdownIconRes(glyph)),
            )
        }
    }

    @Test
    fun `jede Datei ist ein wohlgeformter 24dp-Vektor`() {
        (countdownSymbole + mond).forEach { name ->
            val vector = wurzel(name)
            assertEquals("$name: falsches Wurzelelement", "vector", vector.tagName)
            assertEquals("$name: Breite", "24dp", vector.getAttribute("android:width"))
            assertEquals("$name: Hoehe", "24dp", vector.getAttribute("android:height"))
            assertEquals("$name: Viewport-Breite", "24", vector.getAttribute("android:viewportWidth"))
            assertEquals("$name: Viewport-Hoehe", "24", vector.getAttribute("android:viewportHeight"))
            assertEquals("$name: Tint", "#FFFFFFFF", vector.getAttribute("android:tint"))
            assertTrue("$name: leerer pathData", pfad(name).isNotEmpty())
        }
    }

    @Test
    fun `keine zwei Symbole sind pfadgleich`() {
        // Der Mond bleibt aussen vor: Now und None teilen ihn absichtlich.
        val nachPfad = countdownSymbole.groupBy { pfad(it) }.filterValues { it.size > 1 }
        assertTrue("pfadgleiche Symbole: ${nachPfad.values}", nachPfad.isEmpty())
    }

    @Test
    fun `die Dateien im Repo stimmen mit dem Generator-Manifest ueberein`() {
        // Kein Python im Test — der Generator wird also nicht ausgefuehrt.
        // Das Manifest faengt trotzdem den realistischen Fall: jemand bessert
        // einen Pfad von Hand nach, und der naechste Generatorlauf loescht die
        // Korrektur wieder.
        assertTrue("$manifest fehlt", manifest.isFile)
        val erwartet = manifest.readLines().filter { it.isNotBlank() }.associate { zeile ->
            val (hash, datei) = zeile.split("  ", limit = 2)
            datei to hash
        }
        assertEquals(
            "Manifest listet andere Dateien als der Code erreicht",
            countdownSymbole.map { "$it.xml" }.toSortedSet(),
            erwartet.keys.toSortedSet(),
        )
        val abweichend = erwartet.filter { (datei, hash) -> sha256(File(drawables, datei)) != hash }
        assertTrue(
            "von Hand geaendert oder Generator nicht neu gelaufen: ${abweichend.keys}",
            abweichend.isEmpty(),
        )
    }

    // --- Werkzeug ------------------------------------------------------------

    private fun wurzel(name: String): Element =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(drawables, "$name.xml"))
            .documentElement

    private fun pfad(name: String): String {
        val pfade = wurzel(name).getElementsByTagName("path")
        assertEquals("$name: genau ein <path> erwartet", 1, pfade.length)
        return (pfade.item(0) as Element).getAttribute("android:pathData")
    }

    /**
     * Vom Ressourcen-Bezeichner zurueck zum Namen. Ohne diesen Umweg koennte
     * der Test nur pruefen, dass `countdownIconRes` irgendeine Zahl liefert.
     */
    private fun resName(id: Int): String {
        val felder = Class.forName("de.gebetszeiten.R\$drawable").fields
        return felder.firstOrNull { it.type == Int::class.javaPrimitiveType && it.getInt(null) == id }
            ?.name
            ?: "unbekannte Ressource 0x${id.toString(16)}"
    }

    /** Zeilenenden normiert: das Repo laeuft mit core.autocrlf=true. */
    private fun sha256(file: File): String {
        val bytes = file.readText().replace("\r\n", "\n").toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
