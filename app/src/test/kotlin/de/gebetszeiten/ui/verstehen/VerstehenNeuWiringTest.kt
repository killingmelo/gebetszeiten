package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Schalter und Weiche der neuen Verstehen-Seite, quelltextlesend.
 * Arbeitsverzeichnis ist das Modul `app`.
 */
class VerstehenNeuWiringTest {

    @Test
    fun weicheNachSchalter() {
        val datei = File("src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt")
        assertTrue("${datei.absolutePath} fehlt", datei.isFile)
        val text = datei.readText()
        // Der letzte Treffer ist die Seitenwahl; der erste ist die Titelzeile der Kopfleiste.
        val start = text.lastIndexOf("Tab.VERSTEHEN ->")
        assertTrue("Tab.VERSTEHEN -> fehlt", start >= 0)
        val zweig = text.substring(start, minOf(text.length, start + 400))
        assertTrue("VerstehenNeuScreen( fehlt", zweig.contains("VerstehenNeuScreen("))
        assertTrue("BuildConfig.VERSTEHEN_NEU fehlt", zweig.contains("BuildConfig.VERSTEHEN_NEU"))
    }

    @Test
    fun releaseHatSchalterAus() {
        val datei = File("build.gradle.kts")
        assertTrue("${datei.absolutePath} fehlt", datei.isFile)
        val text = datei.readText()
        val aus = """buildConfigField("boolean", "VERSTEHEN_NEU", "false")"""
        val an = """buildConfigField("boolean", "VERSTEHEN_NEU", "true")"""
        val dc = text.indexOf("defaultConfig {")
        val dcEnde = text.indexOf("flavorDimensions", dc)
        assertTrue("defaultConfig fehlt", dc >= 0 && dcEnde > dc)
        assertTrue("defaultConfig ohne $aus", text.substring(dc, dcEnde).contains(aus))
        val dbg = text.indexOf("debug {")
        val dbgEnde = text.indexOf("release {", dbg)
        assertTrue("debug fehlt", dbg >= 0 && dbgEnde > dbg)
        assertTrue("debug ohne $an", text.substring(dbg, dbgEnde).contains(an))
    }

    @Test
    fun nurLehrZeitenFormatiert() {
        // Uhrzeiten werden allein in LehrZeiten.kt formatiert; Himmelsband.kt gibt es erst später.
        val dateien = listOf("VerstehenNeuScreen.kt", "Himmelsband.kt")
            .map { File("src/main/kotlin/de/gebetszeiten/ui/verstehen/$it") }
            .filter { it.isFile }
        assertTrue("VerstehenNeuScreen.kt fehlt", dateien.any { it.name == "VerstehenNeuScreen.kt" })
        for (datei in dateien) {
            val text = datei.readText()
            assertTrue("${datei.name} enthält HH:mm", !text.contains("HH:mm"))
            assertTrue("${datei.name} enthält DateTimeFormatter", !text.contains("DateTimeFormatter"))
        }
    }
}
