package de.gebetszeiten.ui

import de.gebetszeiten.prayer.PhotoKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die gebündelten Titelbilder: Dateien vorhanden, Größenbudget eingehalten,
 * jeder [PhotoKey] hat Bild, Unterschrift und Beschreibung, und die
 * Fotografen sind vollständig genannt. Quelltextlesend, weil Compose und
 * Ressourcen in diesem Projekt nicht instrumentiert getestet werden.
 * Das Arbeitsverzeichnis ist das Modul `app`.
 */
class PhotosAssetTest {

    private val drawables = File("src/main/res/drawable-nodpi")
    private val strings = File("src/main/res/values/strings.xml")
    private val photosKt = File("src/main/kotlin/de/gebetszeiten/ui/Photos.kt")

    private fun suffix(key: PhotoKey) = key.name.lowercase()

    @Test
    fun alleSechsDateienExistierenUndBleibenImBudget() {
        val files = PhotoKey.entries.map { File(drawables, "photo_${suffix(it)}.webp") }
        files.forEach { assertTrue("${it.path} fehlt", it.isFile) }
        val summe = files.sumOf { it.length() }
        assertTrue("Fotos zusammen $summe Byte, erlaubt 1572864", summe <= 1_572_864)
    }

    @Test
    fun jederSchluesselHatBildUnterschriftUndBeschreibung() {
        val xml = strings.readText()
        val quelltext = photosKt.readText()
        for (key in PhotoKey.entries) {
            val s = suffix(key)
            assertTrue("Bild für $key fehlt in Photos.kt", quelltext.contains("R.drawable.photo_$s"))
            assertTrue("Unterschrift für $key fehlt", xml.contains("name=\"photo_caption_$s\""))
            assertTrue("Beschreibung für $key fehlt", xml.contains("name=\"photo_description_$s\""))
            assertTrue("Unterschrift für $key nicht verdrahtet", quelltext.contains("R.string.photo_caption_$s"))
            assertTrue("Beschreibung für $key nicht verdrahtet", quelltext.contains("R.string.photo_description_$s"))
        }
    }

    @Test
    fun fotografenDeckenAlleSchluesselAb() {
        assertEquals(PhotoKey.entries.toSet(), PHOTO_CREDITS.map { it.key }.toSet())
        assertEquals(PhotoKey.entries.size, PHOTO_CREDITS.size)
        PHOTO_CREDITS.forEach {
            assertTrue(it.photographer.isNotBlank())
            assertTrue(it.unsplashId.isNotBlank())
        }
    }

    @Test
    fun fotoTexteEnthaltenKeineStriche() {
        val xml = strings.readText()
        val texte = Regex("""<string name="photo_[^"]*"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).map { it.groupValues[1] }.toList()
        assertTrue("keine Foto-Strings gefunden", texte.size >= 13)
        texte.forEach {
            assertFalse("Strich in: $it", it.contains('—') || it.contains('–'))
        }
    }
}
