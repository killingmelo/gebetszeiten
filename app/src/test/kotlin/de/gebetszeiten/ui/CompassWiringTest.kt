package de.gebetszeiten.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Kompass zeigt geografisch Nord (Missweisung) und meldet schwache
 * Sensorgenauigkeit. Quelltextlesend, weil Compose hier nicht getestet wird.
 */
class CompassWiringTest {

    private fun lesen(pfad: String): String {
        val datei = File(pfad)
        assertTrue("${datei.absolutePath} fehlt", datei.isFile)
        return datei.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    private val compass get() = lesen("src/main/kotlin/de/gebetszeiten/ui/Compass.kt")
    private val screen get() = lesen("src/main/kotlin/de/gebetszeiten/ui/QiblaScreen.kt")

    @Test fun kompassKorrigiertMissweisung() {
        assertTrue(compass.contains("GeomagneticField("))
        assertTrue(compass.contains("trueAzimuth("))
    }

    @Test fun genauigkeitswechselWirdAusgewertet() {
        val m = Regex("""onAccuracyChanged\s*\([^)]*\)\s*\{([^}]*)\}""").find(compass)
        assertTrue("onAccuracyChanged fehlt", m != null)
        assertTrue("onAccuracyChanged ist leer", m!!.groupValues[1].isNotBlank())
        assertTrue(compass.contains("DeviceHeading("))
    }

    @Test fun qiblaScreenNutztOrtUndWarnt() {
        assertTrue(screen.contains("rememberDeviceAzimuth(settings.latitude, settings.longitude)"))
        assertTrue(screen.contains("qibla_compass_inaccurate"))
        assertTrue(screen.contains("qibla_compass_interference"))
        assertFalse(screen.contains("rememberDeviceAzimuth()"))
    }

    @Test fun richtungOhneVerwaistenPunktUndScrollbar() {
        // Gradzahl und Himmelsrichtung getrennt, TalkBack liest beides als eine Einheit.
        assertTrue(screen.contains("R.string.qibla_bearing_desc"))
        assertTrue(screen.contains("clearAndSetSemantics"))
        val strings = File("src/main/res/values/strings.xml").readText()
        val bearing = Regex("""<string name="qibla_bearing">([^<]*)</string>""").find(strings)!!.groupValues[1]
        assertFalse("qibla_bearing enthaelt noch ·: $bearing", bearing.contains("·"))
        // Bei Schriftgröße 1,4 scrollen statt abschneiden, mittig solange es passt.
        assertTrue(screen.contains("verticalScroll(rememberScrollState())"))
        assertTrue(screen.contains("heightIn(min = maxHeight)"))
        assertTrue(screen.contains("BoxWithConstraints("))
    }
}
