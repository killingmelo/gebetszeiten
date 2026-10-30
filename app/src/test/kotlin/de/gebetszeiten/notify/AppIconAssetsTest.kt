package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Haelt das Startbildschirm-Symbol an seine Quelle, `tools/app-icon/`.
 *
 * Form und Store-Icon kommen aus derselben `geometry.json`. Bessert jemand
 * den Vordergrund von Hand nach, sieht das Geraet etwas anderes als der
 * Store, und das faellt erst auf, wenn man beide nebeneinander haelt. Also
 * faellt es hier auf. Arbeitsverzeichnis ist das Modul (`app/`).
 */
class AppIconAssetsTest {

    private val drawables = File("src/main/res/drawable")
    private val manifest = File("../tools/app-icon/icon.sha256")

    private val erwartet: Map<String, String> by lazy {
        assertTrue("$manifest fehlt", manifest.isFile)
        manifest.readLines().filter { it.isNotBlank() }.associate { zeile ->
            val (hash, datei) = zeile.split("  ", limit = 2)
            datei to hash
        }
    }

    @Test
    fun `das Manifest deckt Vorder- und Hintergrund`() = assertEquals(
        setOf("ic_launcher_foreground.xml", "ic_launcher_background.xml"),
        erwartet.keys,
    )

    @Test
    fun `die Dateien stimmen mit dem Generator ueberein`() {
        val abweichend = erwartet.filter { (datei, hash) -> sha256(File(drawables, datei)) != hash }
        assertTrue("von Hand geaendert oder Generator nicht neu gelaufen: ${abweichend.keys}", abweichend.isEmpty())
    }

    @Test
    fun `der Grund ist Creme`() =
        assertTrue(File(drawables, "ic_launcher_background.xml").readText().contains("#FFF7F1E8"))

    /** Der Vordergrund dient auch als einfarbige Ebene; ein Tint wuerde das System uebersteuern. */
    @Test
    fun `der Vordergrund hat keinen Tint`() =
        assertFalse(File(drawables, "ic_launcher_foreground.xml").readText().contains("android:tint"))

    @Test
    fun `das adaptive Symbol ist verdrahtet`() = listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach {
        val xml = File("src/main/res/mipmap-anydpi-v26/$it").readText()
        assertTrue("$it: Hintergrund", xml.contains("@drawable/ic_launcher_background"))
        assertTrue("$it: einfarbige Ebene", xml.contains("<monochrome android:drawable=\"@drawable/ic_launcher_foreground\""))
    }

    /** Zeilenenden normiert: das Repo laeuft mit core.autocrlf=true. */
    private fun sha256(file: File): String {
        val bytes = file.readText().replace("\r\n", "\n").toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
