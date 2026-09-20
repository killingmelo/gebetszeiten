package de.gebetszeiten.ui

import de.gebetszeiten.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Ersteinrichtung — die Entscheidung, nicht das Bild.
 *
 * Compose wird in diesem Projekt nicht getestet; was hier steht, sind die
 * Zusagen, die davon unabhaengig gelten muessen: die Reihenfolge, die
 * Unentrinnbarkeit, und dass kein Satz der einen Kohorte etwas behauptet,
 * das nur fuer die andere stimmt.
 */
class OnboardingFlowTest {

    private val entwurf = AppSettings.DEFAULT

    @Test fun `die Reihenfolge ist Ort, Anzeige, Erinnerungen`() {
        assertEquals(OnboardingStep.ANZEIGE, nextStep(OnboardingStep.ORT))
        assertEquals(OnboardingStep.ERINNERUNGEN, nextStep(OnboardingStep.ANZEIGE))
        assertNull(nextStep(OnboardingStep.ERINNERUNGEN))
    }

    @Test fun `zurueck fuehrt nie hinaus`() {
        // Die Ersteinrichtung ist Pflicht: aus dem ersten Schritt gibt es
        // keinen Rueckweg in eine Hauptansicht, die es noch nicht gibt.
        assertNull(previousStep(OnboardingStep.ORT))
        assertEquals(OnboardingStep.ORT, previousStep(OnboardingStep.ANZEIGE))
        assertEquals(OnboardingStep.ANZEIGE, previousStep(OnboardingStep.ERINNERUNGEN))
    }

    @Test fun `vor und zurueck heben sich auf`() {
        OnboardingStep.values().forEach { s ->
            nextStep(s)?.let { assertEquals(s, previousStep(it)) }
            previousStep(s)?.let { assertEquals(s, nextStep(it)) }
        }
    }

    @Test fun `ohne Ort geht es nicht weiter, die anderen Schritte sind frei`() {
        assertTrue(canContinue(OnboardingStep.ORT, entwurf))
        assertFalse(canContinue(OnboardingStep.ORT, entwurf.copy(city = "")))
        assertFalse(canContinue(OnboardingStep.ORT, entwurf.copy(city = "   ")))
        // Bewusst ohne Pflichtangabe: eine Anzeige-Wahl ist immer gueltig
        // (auch „Aus"), und die Erinnerungen sind ab Werk sinnvoll gesetzt.
        assertTrue(canContinue(OnboardingStep.ANZEIGE, entwurf.copy(city = "")))
        assertTrue(canContinue(OnboardingStep.ERINNERUNGEN, entwurf.copy(city = "")))
    }

    @Test fun `der letzte Schritt heisst Fertig, nicht Weiter`() {
        assertEquals("Weiter", onboardingContinueLabel(OnboardingStep.ORT))
        assertEquals("Weiter", onboardingContinueLabel(OnboardingStep.ANZEIGE))
        assertEquals("Fertig", onboardingContinueLabel(OnboardingStep.ERINNERUNGEN))
    }

    // --- Der Wortlaut ---

    @Test fun `jeder Schritt hat in beiden Kohorten Ueberschrift und Satz`() {
        OnboardingStep.values().forEach { s ->
            listOf(true, false).forEach { fresh ->
                val c = onboardingCopy(s, fresh)
                assertTrue("$s/$fresh ohne Ueberschrift", c.headline.isNotBlank())
                assertTrue("$s/$fresh ohne Satz", c.detail.isNotBlank())
            }
        }
    }

    @Test fun `der Bestandsnutzer liest nirgends, er sei neu hier`() {
        OnboardingStep.values().forEach { s ->
            val text = onboardingCopy(s, fresh = false).let { it.headline + " " + it.detail }
            listOf("Willkommen", "zum ersten Mal", "neu installiert").forEach {
                assertFalse("$s behauptet gegenueber Bestandsnutzern „$it\": $text", text.contains(it))
            }
        }
    }

    @Test fun `der neue Nutzer liest nirgends, es habe sich etwas geaendert`() {
        OnboardingStep.values().forEach { s ->
            val text = onboardingCopy(s, fresh = true).let { it.headline + " " + it.detail }
            listOf("geändert", "wie bisher", "gefunden hat es").forEach {
                assertFalse("$s behauptet gegenueber Neuinstallationen „$it\": $text", text.contains(it))
            }
        }
    }

    @Test fun `der Anzeige-Schritt nennt die Folge fuer die Eintritts-Meldungen`() {
        // Die eine Sache, die diese Entscheidung teuer macht und deshalb
        // dastehen MUSS: bei stillem Stil ersetzt die Dauerzeile die fuenf
        // einzelnen Meldungen. Wer das nicht liest, verliert sie, ohne zu
        // wissen warum.
        listOf(true, false).forEach { fresh ->
            val text = onboardingCopy(OnboardingStep.ANZEIGE, fresh).detail
            assertTrue("die Folge fehlt (fresh=$fresh): $text", text.contains("ersetzt"))
            assertTrue("der Stil wird nicht genannt (fresh=$fresh): $text", text.contains("Still"))
        }
    }

    @Test fun `der Ort-Schritt sagt, dass nicht nach dem Standort gefragt wird`() {
        // Die Datenschutz-Haltung der App ist ein Verkaufsargument und eine
        // Zusage: es gibt keine Standortberechtigung im Manifest.
        listOf(true, false).forEach { fresh ->
            val text = onboardingCopy(OnboardingStep.ORT, fresh).detail
            assertTrue("kein Wort zur Position (fresh=$fresh): $text", text.contains("Position"))
        }
    }
}
