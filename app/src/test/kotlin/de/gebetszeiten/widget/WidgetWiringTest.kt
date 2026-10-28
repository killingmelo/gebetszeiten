package de.gebetszeiten.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import de.gebetszeiten.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung des Widgets im neuen Design, quelltextlesend (Glance wird
 * hier nicht gerendert, kein Robolectric).
 *
 * Gewacht wird über das, was ein Umbau still verlieren könnte: Titel, Foto
 * und Gebetsreihe kommen aus [de.gebetszeiten.prayer.heroState],
 * [de.gebetszeiten.prayer.dayRows] und [de.gebetszeiten.ui.photoRes]; der
 * Systemzähler („Genau“), die Dringlichkeitsfarbe und der Klick in die App
 * bleiben; die Vorschau trägt die Palette; Schrift auf dem Foto bleibt auch
 * über der hellsten Bildstelle lesbar.
 */
class WidgetWiringTest {

    private val widget = File("src/main/kotlin/de/gebetszeiten/widget/NextPrayerWidget.kt")
    private val heute = File("src/main/kotlin/de/gebetszeiten/ui/HeuteScreen.kt")

    private fun ohneKommentare(f: File): String {
        assertTrue("${f.absolutePath} fehlt", f.isFile)
        return f.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    private fun farbe(datei: String, name: String): String? =
        Regex("""<color name="$name">\s*(#[0-9A-Fa-f]{8})\s*</color>""")
            .find(File(datei).readText())?.groupValues?.get(1)?.uppercase()

    @Test fun `Widget nutzt die gemeinsame Zeitlogik und die Fotos`() {
        val text = ohneKommentare(widget)
        listOf("heroState(", "dayRows(", "photoRes(", "photoDescriptionRes(", "momentLabelRes(", "ContentScale.Crop").forEach {
            assertTrue("NextPrayerWidget.kt enthaelt $it nicht", text.contains(it))
        }
    }

    @Test fun `Systemzaehler, Dringlichkeit und Klick in die App bleiben`() {
        val text = ohneKommentare(widget)
        listOf(
            "setChronometerCountDown", "R.color.widget_urgent", "actionStartActivity",
            "remainingStepLabel(", "isUrgent(", "KarahaText(", "widgetTransparent", "NoTimesContent(",
        ).forEach {
            assertTrue("NextPrayerWidget.kt enthaelt $it nicht mehr", text.contains(it))
        }
    }

    @Test fun `Titelname liegt an einer gemeinsamen Stelle`() {
        assertFalse(
            "HeuteScreen.kt definiert momentLabelRes noch selbst",
            ohneKommentare(heute).contains("fun momentLabelRes"),
        )
    }

    @Test fun `Vorschau traegt die Palette`() {
        val tag = "src/main/res/values/colors.xml"
        val nacht = "src/main/res/values-night/colors.xml"
        assertEquals("#FFF7F1E8", farbe(tag, "widget_preview_bg"))
        assertEquals("#FF1C1B1A", farbe(tag, "widget_preview_text"))
        assertEquals("#FF224228", farbe(tag, "widget_preview_accent"))
        assertEquals("#FF000000", farbe(nacht, "widget_preview_bg"))
        assertEquals("#FFF7F1E8", farbe(nacht, "widget_preview_text"))
        assertEquals("#FF849671", farbe(nacht, "widget_preview_accent"))
    }

    @Test fun `Schrift auf dem Foto bleibt ueber weissem Bildgrund lesbar`() {
        // Schlimmster Fall: die Schrift steht über einer rein weißen Bildstelle.
        val grund = WidgetPhotoStyle.Scrim.compositeOver(Color.White)
        val ratio = contrastRatio(WidgetPhotoStyle.Text, grund)
        assertTrue("Kontrast auf dem Foto nur $ratio", ratio >= 4.5)
    }

    @Test fun `Dringlichkeit auf dem Foto steht hell auf rotem Feld`() {
        val ratio = contrastRatio(WidgetPhotoStyle.Text, de.gebetszeiten.ui.theme.Palette.Day.error)
        assertTrue("Kontrast auf dem roten Feld nur $ratio", ratio >= 4.5)
        assertTrue(
            "das rote Feld fehlt im Widget",
            ohneKommentare(widget).contains("background(ColorProvider(Palette.Day.error))"),
        )
    }
}
