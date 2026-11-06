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
    fun himmelsbandOhneUhrzeit() {
        val text = File("src/main/kotlin/de/gebetszeiten/ui/verstehen/Himmelsband.kt").readText()
        for (verboten in listOf("format(", "HH", "ZonedDateTime")) {
            assertTrue("Himmelsband.kt enthält $verboten", !text.contains(verboten))
        }
    }

    @Test
    fun himmelsbandBeschrieben() {
        val text = File("src/main/kotlin/de/gebetszeiten/ui/verstehen/Himmelsband.kt").readText()
        assertTrue("contentDescription fehlt", text.contains("contentDescription"))
        assertTrue("beschreibung( nicht in semantics", text.contains("semantics { contentDescription = beschreibung(band) }"))
        val start = text.indexOf("private fun beschreibung(band: Band)")
        assertTrue("beschreibung fehlt", start >= 0)
        val ende = text.indexOf("/**", start)
        val zweige = text.substring(start, ende)
        for (band in Band.values()) {
            assertTrue("Band.${band.name} -> fehlt in beschreibung", zweige.contains("Band.${band.name} -> \""))
        }
    }

    @Test
    fun nurLehrZeitenFormatiert() {
        // Uhrzeiten werden allein in LehrZeiten.kt formatiert; Screen und Himmelsband.kt formatieren nichts.
        val dateien = listOf("VerstehenNeuScreen.kt", "VerstehenNeuTeile.kt", "Himmelsband.kt")
            .map { File("src/main/kotlin/de/gebetszeiten/ui/verstehen/$it") }
            .filter { it.isFile }
        assertTrue("VerstehenNeuScreen.kt fehlt", dateien.any { it.name == "VerstehenNeuScreen.kt" })
        for (datei in dateien) {
            val text = datei.readText()
            assertTrue("${datei.name} enthält HH:mm", !text.contains("HH:mm"))
            assertTrue("${datei.name} enthält DateTimeFormatter", !text.contains("DateTimeFormatter"))
        }
    }

    /** Die Seite: VerstehenNeuScreen.kt und die ausgelagerten Bausteine in VerstehenNeuTeile.kt. */
    private fun seite(): String {
        val dateien = listOf("VerstehenNeuScreen.kt", "VerstehenNeuTeile.kt")
            .map { File("src/main/kotlin/de/gebetszeiten/ui/verstehen/$it") }
        for (d in dateien) assertTrue("${d.absolutePath} fehlt", d.isFile)
        return dateien.joinToString("\n") { it.readText() }
    }

    @Test
    fun nutztZerlegeUndGlossar() {
        val text = seite()
        assertTrue("zerlege( fehlt", text.contains("zerlege("))
        assertTrue("GLOSSAR[ fehlt", text.contains("GLOSSAR["))
    }

    @Test
    fun arabischRechtsNachLinks() {
        val text = seite()
        assertTrue("RTL fehlt", text.contains("LayoutDirection.Rtl") || text.contains("TextDirection.Rtl"))
    }

    @Test
    fun fokusUeberZielVon() {
        val text = seite()
        assertTrue("zielVon( fehlt", text.contains("zielVon("))
        assertTrue("zielVonMoment( fehlt", text.contains("zielVonMoment("))
    }

    @Test
    fun minutenSchleifeNurImVordergrund() {
        assertTrue(seite().contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
    }

    @Test
    fun keineFestenFarben() {
        assertTrue("feste Farbe Color(0x", !seite().contains("Color(0x"))
    }

    @Test
    fun ohneZeitenKeinAbsturz() {
        val text = seite()
        assertTrue("!! in der Seite", !text.contains("!!"))
        val aufrufe = text.lines()
            .filter { it.contains("zeitzeile(") }
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
        assertTrue("zeitzeile( fehlt", aufrufe.isNotEmpty())
        for (zeile in aufrufe) {
            assertTrue("zeitzeile( ohne ?.let: $zeile", zeile.contains(")?.let"))
        }
    }

    @Test
    fun tagesbandGleichHoch() {
        val text = File("src/main/kotlin/de/gebetszeiten/ui/verstehen/Himmelsband.kt").readText()
        assertTrue("IntrinsicSize.Max fehlt", text.contains(".height(IntrinsicSize.Max)"))
        assertTrue("fillMaxHeight fehlt", text.contains(".fillMaxHeight()"))
    }

    @Test
    fun regexFuerAndroidMaskiert() {
        // Androids ICU-Regex wirft bei nacktem „]“ oder „}“ (die JVM der Unit-Tests nicht):
        // Absturz beim ersten Öffnen der Seite am 06.11.2026.
        val text = File("src/main/kotlin/de/gebetszeiten/ui/verstehen/LehrText.kt").readText()
        val muster = Regex("Regex\\(\"\"\"(.*)\"\"\"\\)").findAll(text).map { it.groupValues[1] }.toList()
        assertTrue("keine Regex gefunden", muster.isNotEmpty())
        for (m in muster) {
            var i = 0
            var inKlasse = false
            while (i < m.length) {
                val c = m[i]
                when {
                    c == '\\' -> i++
                    c == '[' -> inKlasse = true
                    c == ']' && inKlasse -> inKlasse = false
                    c == ']' || c == '}' -> throw AssertionError("unmaskiertes $c in $m")
                }
                i++
            }
        }
    }

    @Test
    fun belegZeigtDirektzitat() {
        assertTrue("beleg.quote.german fehlt", seite().contains("beleg.quote.german"))
    }

    @Test
    fun geoeffnetVorDemSprung() {
        // opened vor springe(): sonst springt LaunchedEffect(day) während des Pager-Sprungs dazwischen.
        val text = seite()
        for (aufruf in listOf("springe(zielVon(focus))", "springe(zielVonMoment(moment))")) {
            val i = text.indexOf(aufruf)
            assertTrue("$aufruf fehlt", i >= 0)
            val davor = text.substring(maxOf(0, i - 300), i)
            assertTrue("opened = true nicht vor $aufruf", davor.contains("opened = true"))
        }
    }

    @Test
    fun linksBleibenFuerTalkBack() {
        // Eine contentDescription über dem ganzen Lehrtext verdeckte die Link-Aktionen.
        val text = File("src/main/kotlin/de/gebetszeiten/ui/verstehen/VerstehenNeuTeile.kt").readText()
        val start = text.indexOf("private fun LehrText(")
        assertTrue("LehrText fehlt", start >= 0)
        val rumpf = text.substring(start, text.indexOf("/**", start))
        assertTrue("LehrText setzt contentDescription", !rumpf.contains("contentDescription"))
    }

    @Test
    fun fokusWirdZurueckgegeben() {
        assertTrue("onFocusConsumed() fehlt", seite().contains("onFocusConsumed()"))
    }
}
