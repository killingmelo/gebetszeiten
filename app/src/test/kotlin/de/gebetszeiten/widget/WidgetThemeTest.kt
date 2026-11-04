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

    // Sand, Abendrot, Nachtblau, grau, fast weiß, fast schwarz.
    private val bilder = listOf(
        intArrayOf(0xFFC89A5E.toInt(), 0xFF8A6235.toInt(), 0xFFE8CFA0.toInt()),
        intArrayOf(0xFFD0562C.toInt(), 0xFF6B2A1A.toInt()),
        intArrayOf(0xFF1B2A4A.toInt(), 0xFF33507A.toInt()),
        intArrayOf(0xFF808080.toInt()),
        intArrayOf(0xFFFAFAF5.toInt()),
        intArrayOf(0xFF050505.toInt()),
    ).map { farben -> IntArray(300) { farben[it % farben.size] } }

    @Test fun `Tagesbild-Thema haelt 4,5 zu 1 fuer jedes Bild`() {
        bilder.forEach { px ->
            val t = photoTokens(px)
            val grund = t.canvas.toArgb()
            listOf("Text" to t.ink, "Nebentext" to t.stone, "Akzent" to t.accent, "Gold" to t.gold, "Fehler" to t.error)
                .forEach { (rolle, farbe) ->
                    val k = contrast(farbe.toArgb(), grund)
                    assertTrue("$rolle: %.2f".format(k), k >= 4.5)
                }
        }
    }

    @Test fun `Restzeit gross braucht die Stufen-Weckkette auch bei Countdown aus`() {
        val aus = de.gebetszeiten.data.AppSettings.DEFAULT
        assertTrue(!aus.widgetNeedsStepAlarms())
        assertTrue(aus.copy(widgetFormat = "countdown").widgetNeedsStepAlarms())
        // Genau zeichnet der Systemzähler, keine Kette nötig.
        assertTrue(
            !aus.copy(widgetFormat = "countdown", countdownMode = de.gebetszeiten.data.AppSettings.PRECISION_EXACT)
                .widgetNeedsStepAlarms(),
        )
        assertEquals(WidgetFormat.STANDARD, WidgetFormat.of("quer"))
    }

    @Test fun `eigene Werte eines Widgets gehen vor, fehlende kommen aus der App`() {
        val app = de.gebetszeiten.data.AppSettings.DEFAULT.copy(widgetTheme = "forest", widgetOpacity = 80)
        val eigen = androidx.datastore.preferences.core.mutablePreferencesOf(
            NextPrayerWidget.CHOICE_THEME to "sand",
            NextPrayerWidget.CHOICE_FORMAT to "list",
        )
        val w = NextPrayerWidget.withWidgetChoice(app, eigen)
        assertEquals("sand", w.widgetTheme)
        assertEquals("list", w.widgetFormat)
        assertEquals(80, w.widgetOpacity)
        // Ohne eigenen Zustand: genau die App-Werte (bestehende Widgets bleiben, wie sie sind).
        assertEquals(app, NextPrayerWidget.withWidgetChoice(app, null))
    }

    @Test fun `Widget-Einstellungen sind angemeldet`() {
        val info = java.io.File("src/main/res/xml/next_prayer_widget_info.xml").readText()
        assertTrue(info.contains("android:configure=\"de.gebetszeiten.widget.WidgetConfigActivity\""))
        assertTrue(info.contains("reconfigurable"))
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".widget.WidgetConfigActivity"))
        assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_CONFIGURE"))
        val planer = java.io.File("src/main/kotlin/de/gebetszeiten/alarm/PrayerAlarmScheduler.kt").readText()
        assertTrue(planer.contains("NextPrayerWidget.withWidgetNeeds(context, settings)"))
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
        assertTrue(widget.contains("val state = currentState<Preferences>()"))
        assertTrue(widget.contains("produceState(first, state) { value = buildModel(context, state) }"))
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
