package de.gebetszeiten.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung der Ersteinrichtung — das, was [OnboardingFlowTest] nicht
 * sehen kann, weil Compose hier nicht getestet wird.
 *
 * Drei Zusagen, jede mit einem Ausfall dahinter, der niemandem auffiele:
 * - Ohne das Tor liefe die Ersteinrichtung nie.
 * - Ohne `markOnboardingDone` kaeme sie bei jedem Start wieder.
 * - Ohne das Neuplanen erschiene die gerade gewaehlte Anzeige erst zum
 *   naechsten Gebet — derselbe stille Ausfall wie beim frueher leeren
 *   Berechtigungs-Rueckruf.
 */
class OnboardingWiringTest {

    private fun ohneKommentare(f: File): String {
        assertTrue("${f.absolutePath} fehlt", f.isFile)
        return f.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
            .replace(Regex("""\s+"""), "")
    }

    @Test fun `die Hauptansicht liegt hinter dem Tor`() {
        val text = ohneKommentare(File("src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt"))
        assertTrue(
            "MainScreen wird nicht mehr von settings.onboardingDone bewacht",
            text.contains("if(settings.onboardingDone){MainScreen(viewModel)}else{Onboarding("),
        )
    }

    @Test fun `der Abschluss speichert, merkt und plant neu`() {
        val text = ohneKommentare(File("src/main/kotlin/de/gebetszeiten/ui/PrayerViewModel.kt"))
        val start = text.indexOf("funcompleteOnboarding(")
        assertTrue("completeOnboarding fehlt", start >= 0)
        val rumpf = text.substring(start, text.indexOf("funensureScheduled(", start))
        listOf("repository.save(value)", "repository.markOnboardingDone()", "reschedule(value)").forEach {
            assertTrue("completeOnboarding ruft $it nicht", rumpf.contains(it))
        }
    }

    @Test fun `die Ersteinrichtung speichert erst am Ende`() {
        // Der technische Kern der Zusage „bis zur Antwort bleibt alles, wie
        // es war": im Ablauf selbst darf nichts committet oder gespeichert
        // werden, nur der Entwurf wandert weiter.
        val text = ohneKommentare(File("src/main/kotlin/de/gebetszeiten/ui/Onboarding.kt"))
        listOf("viewModel.save(", "repository.save(", "markOnboardingDone(").forEach {
            assertTrue(
                "Onboarding.kt schreibt mitten im Ablauf ($it) statt erst bei „Fertig\"",
                !text.contains(it),
            )
        }
        assertTrue(
            "der Abschluss meldet den Entwurf nicht nach oben",
            text.contains("onFinish(draft)"),
        )
    }

    @Test fun `die Vorauswahl dreht keine bewusste Abwahl um`() {
        // Nur eine Neuinstallation bekommt die Dauerzeile vorausgewaehlt. Bei
        // einem Bestandsnutzer koennte das gespeicherte `false` eine
        // Entscheidung sein — sie zu ueberschreiben waere genau das
        // ungefragte Anheften, das diese Ersteinrichtung vermeiden soll.
        val text = ohneKommentare(File("src/main/kotlin/de/gebetszeiten/ui/Onboarding.kt"))
        assertTrue(
            "die Vorauswahl unterscheidet die Kohorten nicht (mehr)",
            text.contains("if(settings.onboardingFresh)settings.copy(persistentNotification=true)elsesettings"),
        )
    }
}
