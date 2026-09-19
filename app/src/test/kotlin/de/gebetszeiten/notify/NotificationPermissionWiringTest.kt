package de.gebetszeiten.notify

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Zwei Zusagen an der Android-Naht, die [NotificationBlockTest] nicht sehen
 * kann — beide waren echte, stille Ausfaelle.
 *
 * 1. Der Systemdialog kam ab Android 13 ungefragt beim allerersten Zeichnen
 *    (`LaunchedEffect(Unit) { launcher.launch(...) }`), ohne ein Wort dazu,
 *    wofuer. Gefragt wird ab jetzt dort, wo vorher erklaert wurde.
 * 2. Der Ergebnis-Rueckruf war leer (`{ /* optional */ }`). Wer die Erlaubnis
 *    spaeter erteilte, sah bis zum naechsten Gebets-Wecker trotzdem nichts,
 *    weil `ensureScheduled()` nur in `onCreate` laeuft.
 *
 * Quelltextlesend, weil Compose in diesem Projekt nicht getestet wird; der
 * gelesene Pfad steht als Gradle-Eingabe (`mainQuellsatz`).
 */
class NotificationPermissionWiringTest {

    private val activity = File("src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt")

    private fun quelltext(): String {
        assertTrue("${activity.absolutePath} fehlt", activity.isFile)
        return activity.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    @Test fun `der Systemdialog startet nicht mehr von selbst`() {
        val text = quelltext().replace(Regex("""\s+"""), "")
        assertTrue(
            "POST_NOTIFICATIONS wird wieder aus einem LaunchedEffect heraus angefragt — " +
                "dann kommt der Dialog ungefragt beim ersten Zeichnen, ohne Erklaerung",
            !text.contains("LaunchedEffect(Unit){launcher.launch("),
        )
    }

    @Test fun `der Ergebnis-Rueckruf ist nicht leer`() {
        val text = quelltext()
        val start = text.indexOf("rememberLauncherForActivityResult(")
        assertTrue("kein Berechtigungs-Launcher gefunden", start >= 0)
        // Der Rueckruf ist der Block hinter der schliessenden Klammer der
        // Argumentliste.
        val block = text.substring(text.indexOf('{', text.indexOf(')', start)))
            .let { it.substring(0, it.indexOf('}') + 1) }
            .replace(Regex("""\s+"""), "")
        assertTrue(
            "der Ergebnis-Rueckruf des Berechtigungs-Launchers ist leer ($block) — wer die " +
                "Erlaubnis erteilt, saehe bis zum naechsten Gebets-Wecker nichts",
            block != "{}",
        )
    }

    @Test fun `die Zusage loest ein Neuplanen aus`() {
        // Ohne das bliebe der Rueckruf zwar nicht leer, taete aber nichts,
        // was der Nutzer bemerkt.
        val text = quelltext().replace(Regex("""\s+"""), "")
        assertTrue(
            "nach erteilter Erlaubnis wird nicht neu geplant (ensureScheduled fehlt an der " +
                "Stelle, an der der Streifen die Zusage einholt)",
            text.contains("onNotificationsGranted={viewModel.ensureScheduled()}"),
        )
    }

    @Test fun `canPost fragt den vollstaendigen Hinderungsgrund ab`() {
        // Sonst faellt die App auf `checkSelfPermission` zurueck und sieht
        // einen abgeschalteten Kanal nicht — samt falschem Pausenmerker.
        val notifier = File("src/main/kotlin/de/gebetszeiten/notify/PrayerNotifier.kt")
        assertTrue("${notifier.absolutePath} fehlt", notifier.isFile)
        val text = notifier.readText().replace(Regex("""\s+"""), "")
        assertTrue(
            "canPost prueft nicht mehr ueber notificationBlock",
            text.contains("funcanPost(context:Context):Boolean=blockOf(context)==NotificationBlock.NONE"),
        )
        assertTrue(
            "areNotificationsEnabled wird nicht abgefragt — eine im System abgeschaltete App " +
                "bliebe unerkannt",
            text.contains("areNotificationsEnabled()"),
        )
    }
}
