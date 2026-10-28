package de.gebetszeiten.notify

import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.prayer.PhotoKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Gebetssymbol und Foto je Gebet, und die fuenf Vektoren dahinter.
 *
 * Die Zuordnung auf `R.drawable` wird ueber den Ressourcennamen geprueft
 * (Praezedenz `CountdownIconAssetsTest`): ein Zweig, der auf das Symbol des
 * Nachbargebets zeigt, kompiliert anstandslos und faellt nur hier auf.
 */
class PrayerIconsTest {

    private val drawables = File("src/main/res/drawable")

    private val pflicht = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    private val erwartet = mapOf(
        Prayer.FAJR to "ic_prayer_fajr",
        Prayer.DHUHR to "ic_prayer_dhuhr",
        Prayer.ASR to "ic_prayer_asr",
        Prayer.MAGHRIB to "ic_prayer_maghrib",
        Prayer.ISHA to "ic_prayer_isha",
    )

    @Test
    fun `jedes Pflichtgebet hat sein eigenes Symbol`() {
        pflicht.forEach { p ->
            assertEquals("$p zeigt auf das falsche Symbol", erwartet[p], resName(prayerIconRes(p)))
        }
        assertEquals(5, pflicht.map { prayerIconRes(it) }.distinct().size)
    }

    @Test
    fun `Sonnenaufgang behaelt den Mond`() {
        assertEquals("ic_notification", resName(prayerIconRes(Prayer.SUNRISE)))
    }

    @Test
    fun `Foto je Gebet`() {
        assertEquals(PhotoKey.FAJR, photoKeyFor(Prayer.FAJR))
        assertEquals(PhotoKey.SHURUQ, photoKeyFor(Prayer.SUNRISE))
        assertEquals(PhotoKey.ZUHR, photoKeyFor(Prayer.DHUHR))
        assertEquals(PhotoKey.ASR, photoKeyFor(Prayer.ASR))
        assertEquals(PhotoKey.MAGHRIB, photoKeyFor(Prayer.MAGHRIB))
        assertEquals(PhotoKey.ISHA, photoKeyFor(Prayer.ISHA))
    }

    @Test
    fun `Foto der Daueranzeige folgt dem laufenden Abschnitt`() {
        assertEquals(PhotoKey.ASR, ongoingPhotoKey(active = Prayer.ASR, next = Prayer.MAGHRIB))
        assertEquals(PhotoKey.ISHA, ongoingPhotoKey(active = Prayer.ISHA, next = Prayer.FAJR))
        // Zwischen Šurūq und Ẓuhr gibt es kein laufendes Gebet (die
        // Aufrufer filtern SUNRISE heraus): das ist der Šurūq-Abschnitt.
        assertEquals(PhotoKey.SHURUQ, ongoingPhotoKey(active = null, next = Prayer.DHUHR))
        // Vor Faǧr kennt der Tag noch kein laufendes Gebet: es ist Nacht.
        assertEquals(PhotoKey.ISHA, ongoingPhotoKey(active = null, next = Prayer.FAJR))
    }

    @Test
    fun `die fuenf Symbole sind weisse 24dp-Vektoren und verschieden`() {
        val pfade = erwartet.values.map { name ->
            val datei = File(drawables, "$name.xml")
            assertTrue("$datei fehlt", datei.isFile)
            val vector = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(datei).documentElement
            assertEquals("$name: Wurzel", "vector", vector.tagName)
            assertEquals("$name: Breite", "24dp", vector.getAttribute("android:width"))
            assertEquals("$name: Hoehe", "24dp", vector.getAttribute("android:height"))
            assertEquals("$name: Viewport-Breite", "24", vector.getAttribute("android:viewportWidth"))
            assertEquals("$name: Viewport-Hoehe", "24", vector.getAttribute("android:viewportHeight"))
            assertEquals("$name: Tint", "#FFFFFFFF", vector.getAttribute("android:tint"))
            val paths = vector.getElementsByTagName("path")
            assertTrue("$name: kein <path>", paths.length > 0)
            (0 until paths.length).joinToString("|") { i ->
                val path = paths.item(i) as Element
                assertEquals("$name: Fuellfarbe", "#FFFFFFFF", path.getAttribute("android:fillColor"))
                assertTrue("$name: Kontur statt Flaeche", path.getAttribute("android:strokeColor").isEmpty())
                path.getAttribute("android:pathData").also {
                    assertTrue("$name: leerer pathData", it.isNotBlank())
                }
            }
        }
        assertEquals("pfadgleiche Symbole", pfade.size, pfade.distinct().size)
    }

    private fun resName(id: Int): String {
        val felder = Class.forName("de.gebetszeiten.R\$drawable").fields
        return felder.firstOrNull { it.type == Int::class.javaPrimitiveType && it.getInt(null) == id }
            ?.name
            ?: "unbekannte Ressource 0x${id.toString(16)}"
    }
}
