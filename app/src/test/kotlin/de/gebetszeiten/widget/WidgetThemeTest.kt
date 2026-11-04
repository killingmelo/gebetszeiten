package de.gebetszeiten.widget

import androidx.compose.ui.graphics.toArgb
import de.gebetszeiten.data.widgetOpacityFromPrefs
import de.gebetszeiten.ui.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Widget-Themen: lesbar auf vollem Grund, alte Einstellung bleibt erhalten. */
class WidgetThemeTest {

    @Test fun `jedes Thema haelt 4,5 zu 1 auf seinem Grund`() {
        WidgetTheme.entries.forEach { theme ->
            listOf(theme.tokens.first, theme.tokens.second).forEach { t ->
                val grund = t.canvas.toArgb()
                listOf(
                    "Text" to t.ink,
                    "Nebentext" to t.stone,
                    "Akzent" to t.accent,
                    "Gold" to t.gold,
                    "Fehler" to t.error,
                ).forEach { (rolle, farbe) ->
                    val k = contrast(farbe.toArgb(), grund)
                    assertTrue("$theme $rolle: %.2f".format(k), k >= 4.5)
                }
            }
        }
    }

    @Test fun `unbekannter Schluessel faellt auf System zurueck`() {
        assertEquals(WidgetTheme.SYSTEM, WidgetTheme.of(null))
        assertEquals(WidgetTheme.SYSTEM, WidgetTheme.of("lila"))
        assertEquals(WidgetTheme.FOREST, WidgetTheme.of("forest"))
    }

    @Test fun `eine zweite Aenderung kommt auch in laufender Glance-Sitzung an`() {
        // Gelesen wird in der Komposition, an einem Zähler im Widget-Zustand;
        // `updateAll` allein zeichnete mit den Werten vom Sitzungsbeginn.
        val widget = java.io.File("src/main/kotlin/de/gebetszeiten/widget/NextPrayerWidget.kt").readText()
        assertTrue(widget.contains("currentState<Preferences>()[REFRESH]"))
        assertTrue(widget.contains("produceState(first, token) { value = buildModel(context) }"))
        java.io.File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val code = f.readLines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") }.joinToString("\n")
            assertTrue("${f.name} ruft noch updateAll", !code.contains("NextPrayerWidget().updateAll("))
        }
    }

    @Test fun `der alte Schalter transparent wird zu 45 Prozent`() {
        assertEquals(45, widgetOpacityFromPrefs(stored = null, legacyTransparent = true))
        assertEquals(100, widgetOpacityFromPrefs(stored = null, legacyTransparent = false))
        assertEquals(100, widgetOpacityFromPrefs(stored = null, legacyTransparent = null))
        // Ein gespeicherter Wert gewinnt und bleibt im erlaubten Bereich.
        assertEquals(70, widgetOpacityFromPrefs(stored = 70, legacyTransparent = true))
        assertEquals(100, widgetOpacityFromPrefs(stored = 140, legacyTransparent = null))
    }
}
