package de.gebetszeiten.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wie die drei Gebets-Meldungen aussehen: Eintritt und Vorlauf mit eigenem
 * Gebetssymbol und Foto, die Daueranzeige mit Restzeit-Symbol und Text.
 *
 * Quelltextlesend, weil `NotificationCompat.Builder` ohne Geraet nicht
 * pruefbar ist (kein Robolectric). Geprueft wird je Funktionsrumpf, nicht in
 * der ganzen Datei: ein einziges `BigPictureStyle` irgendwo liesse sonst
 * beide Meldungen gruen aussehen, auch wenn nur eine es benutzt.
 * Praezedenz: `SettingsLayoutTest` (Klammerzaehlung ab einer Textmarke),
 * `OngoingWiringTest`.
 */
class NotificationStyleWiringTest {

    private val notifier = File("src/main/kotlin/de/gebetszeiten/notify/PrayerNotifier.kt")

    private fun quelltext(): String {
        assertTrue("${notifier.absolutePath} fehlt", notifier.isFile)
        return notifier.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    /** Der Rumpf von `fun <name>(...) { ... }`, ab der Signatur. */
    private fun rumpf(name: String): String {
        val text = quelltext()
        val start = text.indexOf("fun $name(")
        assertTrue("fun $name nicht gefunden", start >= 0)
        // Erste Klammer NACH der Parameterliste: die Parameter enthalten
        // keine geschweiften Klammern, wohl aber Vorgabewerte mit `(`.
        val offen = text.indexOf('{', start)
        var tiefe = 0
        for (i in offen until text.length) {
            when (text[i]) {
                '{' -> tiefe++
                '}' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(offen, i)
                }
            }
        }
        throw AssertionError("fun $name ist nicht geschlossen")
    }

    @Test
    fun `Eintritt und Vorlauf tragen Gebetssymbol, Akzentfarbe und Foto`() {
        // Beide teilen sich EINEN Baustein, `prayerLook`; geprueft wird, dass
        // beide ihn rufen UND dass er alles Verlangte setzt.
        listOf("notifyPrayer", "notifyPre").forEach { name ->
            val body = rumpf(name)
            assertTrue("$name: ruft prayerLook( nicht", body.contains(".prayerLook("))
            assertFalse("$name: noch der Mond", body.contains("R.drawable.ic_notification"))
        }
        val look = rumpf("NotificationCompat.Builder.prayerLook")
        assertTrue("kein prayerIconRes(", look.contains("prayerIconRes("))
        assertTrue("kein setColor(", look.contains("setColor("))
        assertTrue("kein BigPictureStyle", look.contains("BigPictureStyle"))
        assertTrue("kein setLargeIcon(", look.contains("setLargeIcon("))
        // Aufgeklappt ersetzt das Bild die Inhaltszeile; ohne
        // Zusammenfassung verschwaende dort der Text.
        assertTrue("kein setSummaryText(", look.contains("setSummaryText("))
        assertTrue("Foto nicht verkleinert", rumpf("decodePhoto").contains("inSampleSize"))
    }

    @Test
    fun `Daueranzeige behaelt Restzeit-Symbol und Text, bekommt Farbe und kleines Foto`() {
        val body = rumpf("updateOngoing")
        assertTrue("kein countdownIconRes(", body.contains("countdownIconRes("))
        assertTrue("kein BigTextStyle", body.contains("BigTextStyle"))
        assertTrue("kein setColor(", body.contains("setColor("))
        assertTrue("kein setLargeIcon(", body.contains("setLargeIcon("))
        assertFalse("Daueranzeige mit BigPictureStyle", body.contains("BigPictureStyle"))
        assertFalse("Daueranzeige mit Gebetssymbol", body.contains("prayerIconRes("))
    }
}
