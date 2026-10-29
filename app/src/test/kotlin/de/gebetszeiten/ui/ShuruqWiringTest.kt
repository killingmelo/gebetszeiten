package de.gebetszeiten.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Šurūq in Monatsansicht und breitem Widget, quelltextlesend (kein
 * Robolectric). Nur amtliche Zeiten: der Wert kommt aus `daily(...).sunrise`.
 */
class ShuruqWiringTest {

    private fun lesen(pfad: String): String {
        val f = File(pfad)
        assertTrue("${f.absolutePath} fehlt", f.isFile)
        return f.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    private val monat get() = lesen("src/main/kotlin/de/gebetszeiten/ui/MonatScreen.kt")
    private val widget get() = lesen("src/main/kotlin/de/gebetszeiten/widget/NextPrayerWidget.kt")
    private val empfaenger get() = lesen("src/main/kotlin/de/gebetszeiten/widget/NextPrayerWidgetReceiver.kt")

    @Test fun `Monat hat eine Šurūq-Spalte aus sunrise zwischen Faǧr und Ẓuhr`() {
        val text = monat
        assertTrue("sunrise wird nicht gelesen", text.contains("it.sunrise"))
        assertTrue("Spaltenkopf Šurūq fehlt", text.contains("R.string.moment_shuruq"))
        assertTrue(
            "Reihenfolge der Zeiten: fajr, sunrise, dhuhr",
            Regex("""it\.fajr,\s*it\.sunrise,\s*it\.dhuhr""").containsMatchIn(text),
        )
        assertTrue("TalkBack-Namen ohne Šurūq", text.contains("\"Šurūq\""))
        assertTrue("Zeilen nicht sechs Zeiten lang", text.contains("List(6)"))
    }

    @Test fun `Widget zeigt Šurūq erst ab einer Mindestbreite und nie als aktuell`() {
        val text = widget
        assertTrue("Breiten-Schwelle fehlt", Regex("""SHURUQ_MIN_WIDTH\s*=\s*\d+\.dp""").containsMatchIn(text))
        assertTrue(
            "Schwelle wird nicht gegen LocalSize.width geprueft",
            Regex("""LocalSize\.current\.width\s*>=\s*SHURUQ_MIN_WIDTH""").containsMatchIn(text),
        )
        assertTrue("Šurūq-Name fehlt", text.contains("R.string.moment_shuruq"))
        assertTrue("Šurūq darf nie aktuell sein", text.contains("current = false"))
        assertFalse(
            "Sonnenaufgang wird noch ausgefiltert",
            text.contains("it.prayer != Prayer.SUNRISE && !it.nextDay"),
        )
    }

    @Test fun `Erstes Platzieren stoesst die Neuplanung der Alarme an`() {
        val text = empfaenger
        assertTrue("onEnabled fehlt", text.contains("override fun onEnabled("))
        assertTrue("scheduleNext wird nicht gerufen", text.contains("PrayerAlarmScheduler.scheduleNext("))
        assertTrue("nicht im Hintergrund (goAsync)", text.contains("goAsync()"))
        assertTrue("Exceptions werden nicht abgefangen", text.contains("catch (e: Exception)"))
        assertTrue(
            "finish() steht nicht in einem finally-Block",
            Regex("""finally\s*\{[^}]*pending\.finish\(\)""").containsMatchIn(text),
        )
    }
}
