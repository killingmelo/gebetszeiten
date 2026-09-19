package de.gebetszeiten.alarm

import de.gebetszeiten.prayer.remainingStepShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Die Grenzberechnung der Anzeige-Weckkette, aus `scheduleDisplayStep`
 * herausgezogen und damit erstmals pruefbar.
 *
 * Die 500 ms stehen hier absichtlich als Zahl und nicht als Konstante: der
 * Test soll den zugesicherten Wert festhalten, nicht das wiederholen, was die
 * Quelle gerade sagt.
 */
class DisplayStepBoundariesTest {

    private val target = 1_700_000_000_000L

    // --- Aufloesung und Anzahl: die Weckvorgaenge duerfen nicht mehr werden ---

    @Test fun neunMinutenFuenfZehnminutenUndDieStundenBisZumZiel() {
        // 3 h Vorlauf: 9 Minutengrenzen + 5 Zehnminutengrenzen + 3 Stunden.
        val boundaries = displayStepBoundaries(target, target - 3 * 3_600_000L)
        assertEquals(9 + 5 + 3, boundaries.size)
    }

    @Test fun dieStundenschleifeHoertBeiJetztAuf() {
        // 90 Minuten Vorlauf: nur die eine volle Stunde liegt noch davor.
        val boundaries = displayStepBoundaries(target, target - 90 * 60_000L)
        assertEquals(9 + 5 + 1, boundaries.size)
    }

    @Test fun keineStundengrenzeWennDasZielSchonNaeherAlsEineStundeIst() {
        val boundaries = displayStepBoundaries(target, target - 59 * 60_000L)
        assertEquals(9 + 5, boundaries.size)
    }

    @Test fun jedeGrenzeKommtNurEinmalVor() {
        val boundaries = displayStepBoundaries(target, target - 5 * 3_600_000L)
        assertEquals(boundaries.size, boundaries.toSet().size)
    }

    // --- Teil 3: der Zuschlag von 500 ms auf jede Grenze ---

    @Test fun jedeGrenzeLiegtEineHalbeSekundeHinterDemRaster() {
        val boundaries = displayStepBoundaries(target, target - 3 * 3_600_000L)
        boundaries.forEach { boundary ->
            // Rastermarke = boundary − 500; sie muss auf einer vollen Minute
            // vor dem Ziel liegen.
            assertEquals(
                "Grenze $boundary liegt nicht auf Raster + 500 ms",
                0L,
                (target - (boundary - 500L)) % 60_000L,
            )
        }
    }

    @Test fun beimAlarmZeigtDerTextSchonDieNeueStufe() {
        // Der Kern von Teil 3: feuert der Alarm GENAU auf dem Raster, liefert
        // remainingStepShort noch die alte Stufe ("10+ Min" bei 600 000 ms).
        // Mit dem Zuschlag ist der Wert eindeutig die neue Stufe.
        val boundaries = displayStepBoundaries(target, target - 3 * 3_600_000L)
        boundaries.forEach { boundary ->
            val beimAlarm = remainingStepShort(Duration.ofMillis(target - boundary))
            val aufDemRaster = remainingStepShort(Duration.ofMillis(target - boundary + 500L))
            assertNotEquals("Grenze $boundary steht noch auf der alten Stufe", aufDemRaster, beimAlarm)
        }
    }

    @Test fun dieZehnMinutenGrenzeZeigtNeunMinuten() {
        val boundaries = displayStepBoundaries(target, target - 3 * 3_600_000L)
        val zehnMinuten = boundaries.single { target - it in 600_000L - 999L..600_000L + 999L }
        assertEquals("9 Min", remainingStepShort(Duration.ofMillis(target - zehnMinuten)))
    }

    @Test fun dieEinstundenGrenzeZeigtFuenfzigMinuten() {
        val boundaries = displayStepBoundaries(target, target - 3 * 3_600_000L)
        val eineStunde = boundaries.single { target - it in 3_600_000L - 999L..3_600_000L + 999L }
        assertEquals("50+ Min", remainingStepShort(Duration.ofMillis(target - eineStunde)))
    }

    @Test fun dieEinMinutenGrenzeZeigtJetzt() {
        val boundaries = displayStepBoundaries(target, target - 3 * 3_600_000L)
        val eineMinute = boundaries.single { target - it in 60_000L - 999L..60_000L + 999L }
        assertEquals("", remainingStepShort(Duration.ofMillis(target - eineMinute)))
    }

    // --- Die Auswahl der naechsten Grenze ---

    @Test fun dieNaechsteGrenzeIstDieKleinsteInDerZukunft() {
        val now = target - 25 * 60_000L
        val naechste = nextDisplayBoundary(displayStepBoundaries(target, now), now)
        // 20 Minuten vor dem Ziel — die naechstkleinere Zehnminutengrenze.
        assertEquals(target - 20 * 60_000L + 500L, naechste)
    }

    @Test fun vergangeneGrenzenWerdenUebersprungen() {
        val now = target - 30_000L
        assertNull(nextDisplayBoundary(displayStepBoundaries(target, now), now))
    }

    @Test fun eineGrenzeInDerNaechstenSekundeZaehltNichtMehr() {
        // Sonst plant ein gerade abgearbeiteter Alarm sich selbst neu ein.
        val now = target - 60_000L
        val grenzen = displayStepBoundaries(target, now)
        assertTrue("die 1-Minuten-Grenze fehlt", grenzen.contains(now + 500L))
        assertNull(nextDisplayBoundary(grenzen, now))
    }

    @Test fun ohneGrenzenGibtEsKeinenAlarm() {
        assertNull(nextDisplayBoundary(emptyList(), target))
    }

    // --- Die Kostenangabe im Einstellungsblatt ---

    private fun kosten(minuten: Long) = displayStepCount(target, target - minuten * 60_000L)

    @Test fun `displayStepCount zaehlt nur Grenzen, die auch feuern`() {
        // 45 Min: neun Minutengrenzen, aber nur VIER Zehnminutenmarken — die
        // 50er liegt schon hinter uns. Keine volle Stunde.
        assertEquals(9 + 4, kosten(45))
        // Ab einer Stunde sind alle fuenf Zehnminutenmarken dabei.
        assertEquals(9 + 5 + 1, kosten(65))
        // Anderthalb Stunden kosten nicht mehr als eine — es ist dieselbe
        // angefangene zweite Stunde. Genau das unterschlug „19 bis 24".
        assertEquals(9 + 5 + 1, kosten(95))
        assertEquals(9 + 5 + 5, kosten(5 * 60 + 10))
        assertEquals(9 + 5 + 10, kosten(10 * 60 + 10))
    }

    @Test fun `genau auf der Stundengrenze zaehlt die Stunde nicht mehr mit`() {
        // Der Randfall, der die Spanne sonst um eins verschoebe: bei exakt
        // fuenf Stunden liegt die 5-Stunden-Grenze 500 ms voraus und faellt
        // damit unter BOUNDARY_MIN_LEAD_MS. Sie steht in der Liste, feuert
        // aber nie — und wird deshalb auch nicht gezaehlt.
        assertTrue(displayStepBoundaries(target, target - 5 * 3_600_000L).size > kosten(5 * 60))
        assertEquals(9 + 5 + 4, kosten(5 * 60))
    }

    @Test fun `die Spanne im Kostentext stimmt mit der Rechnung ueberein`() {
        // Der Text in `settings_remaining_cost` nennt eine Spanne. Ohne
        // diesen Test driftet er beim ersten Umbau von der Wahrheit weg —
        // genau das war mit „19 bis 24" passiert.
        //
        // Gerechnet wird ueber die Laengen echter Gebetsintervalle: das
        // kuerzeste ist Maghrib->Isha (im deutschen Sommer gut anderthalb
        // Stunden), das laengste Isha->Fajr im Winter (rund zwoelf).
        val spanne = (45..(12 * 60) step 5).map { kosten(it.toLong()) }
        assertEquals("kleinster Wert", 13, spanne.min())
        assertEquals("groesster Wert", 25, spanne.max())

        val text = java.io.File("src/main/res/values/strings.xml")
            .readText()
            .substringAfter("""<string name="settings_remaining_cost">""")
            .substringBefore("</string>")
        assertTrue(
            "Der Kostentext nennt nicht die Spanne ${spanne.min()} bis ${spanne.max()}: $text",
            text.contains("${spanne.min()} bis ${spanne.max()}"),
        )
        assertTrue(
            "Der Kostentext nennt die 14 festen Marken nicht: $text",
            text.contains("14 feste"),
        )
    }
}
