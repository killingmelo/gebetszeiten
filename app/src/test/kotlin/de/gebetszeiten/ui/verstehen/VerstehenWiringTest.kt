package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verdrahtung des Reiters „Zeiten verstehen“ und der Sonnenuhr, quelltextlesend
 * (Compose wird in diesem Projekt nicht gerendert getestet, kein Robolectric).
 *
 * Geprüft wird: der Bildschirm liest den Inhalt aus `VERSTEHEN`, setzt Arabisch in
 * Naskh und von rechts nach links, springt bei einem Fokus an den Abschnitt; die
 * Sonnenuhr zeichnet beide ʿAṣr-Kurven (einfacher und doppelter Schatten) und
 * formatiert keine Uhrzeit. Kommentare werden vor dem Abgleich entfernt.
 * Arbeitsverzeichnis ist das Modul `app`.
 */
class VerstehenWiringTest {

    private val dir = "src/main/kotlin/de/gebetszeiten/ui/verstehen"

    private fun quelltext(name: String): String {
        val datei = File("$dir/$name")
        assertTrue("${datei.absolutePath} fehlt", datei.isFile)
        return datei.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    @Test
    fun bildschirmLiestInhaltUndSetztArabischRechtsNachLinks() {
        val text = quelltext("VerstehenScreen.kt")
        assertTrue("VERSTEHEN wird nicht gelesen", Regex("""\bVERSTEHEN\b""").containsMatchIn(text))
        assertTrue("NaskhFamily fehlt", text.contains("NaskhFamily"))
        assertTrue("LayoutDirection.Rtl fehlt", text.contains("LayoutDirection.Rtl"))
        assertTrue("TRANSLATION_NOTE fehlt", text.contains("TRANSLATION_NOTE"))
    }

    @Test
    fun fokusSpringtZumAbschnitt() {
        val text = quelltext("VerstehenScreen.kt")
        assertTrue("scrollToItem fehlt", text.contains("scrollToItem("))
        assertTrue("onFocusConsumed wird nicht aufgerufen", text.contains("onFocusConsumed()"))
    }

    @Test
    fun sonnenuhrZeichnetBeideAsrKurven() {
        val text = quelltext("SundialView.kt")
        assertTrue("ʿAṣr-Kurve für einfachen Schatten fehlt", Regex("""Sundial\.asrCurve\([^()]*\b1\)""").containsMatchIn(text))
        assertTrue("ʿAṣr-Kurve für doppelten Schatten fehlt", Regex("""Sundial\.asrCurve\([^()]*\b2\)""").containsMatchIn(text))
        assertTrue("Beschriftung „2× Schatten“ fehlt", text.contains("\"2× Schatten\""))
        assertTrue("TalkBack-Beschreibung fehlt", text.contains("Sonnenuhr für"))
    }

    @Test
    fun sonnenuhrZeigtKeineUhrzeit() {
        for (name in listOf("SundialView.kt", "VerstehenScreen.kt")) {
            val text = quelltext(name)
            assertFalse("$name formatiert eine Uhrzeit", Regex("""format\([^)]*HH:mm""").containsMatchIn(text))
            assertFalse("$name enthält das Uhrzeitmuster HH:mm", text.contains("HH:mm"))
            assertFalse("$name nutzt DateTimeFormatter", text.contains("DateTimeFormatter"))
        }
    }

    @Test
    fun minutenSchleifeNurImVordergrund() {
        val text = quelltext("VerstehenScreen.kt")
        assertTrue("repeatOnLifecycle fehlt", text.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
    }

    @Test
    fun titelMitDmgAuszeichnung() {
        assertTrue(quelltext("VerstehenScreen.kt").contains("dmgTitle(entry.title)"))
    }
}
