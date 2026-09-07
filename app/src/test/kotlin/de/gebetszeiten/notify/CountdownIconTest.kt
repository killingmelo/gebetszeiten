package de.gebetszeiten.notify

import de.gebetszeiten.prayer.remainingStepShort
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class CountdownIconTest {

    // --- Grenztabelle: jede Grenze einzeln, mit Richtung ---

    @Test fun vierzehnStundenIstGedeckelt() {
        assertEquals(CountdownGlyph.Hours(9, capped = true), countdownGlyph(Duration.ofHours(14), true))
    }

    @Test fun genauZehnStundenIstGedeckelt() {
        assertEquals(CountdownGlyph.Hours(9, capped = true), countdownGlyph(Duration.ofHours(10), true))
    }

    @Test fun knappUnterZehnStundenIstNichtGedeckelt() {
        assertEquals(
            CountdownGlyph.Hours(9, capped = false),
            countdownGlyph(Duration.ofHours(10).minusMillis(1), true),
        )
    }

    @Test fun genauEineStundeIstEineStunde() {
        assertEquals(CountdownGlyph.Hours(1, capped = false), countdownGlyph(Duration.ofHours(1), true))
    }

    @Test fun knappUnterEinerStundeKipptAufFuenfzigMinuten() {
        assertEquals(
            CountdownGlyph.Minutes(50, approx = true),
            countdownGlyph(Duration.ofHours(1).minusMillis(1), true),
        )
    }

    @Test fun neunundfuenfzigMinutenSindFuenfzig() {
        assertEquals(CountdownGlyph.Minutes(50, approx = true), countdownGlyph(Duration.ofMinutes(59), true))
    }

    @Test fun genauZehnMinutenIstUngefaehr() {
        assertEquals(CountdownGlyph.Minutes(10, approx = true), countdownGlyph(Duration.ofMinutes(10), true))
    }

    @Test fun knappUnterZehnMinutenIstExakt() {
        assertEquals(
            CountdownGlyph.Minutes(9, approx = false),
            countdownGlyph(Duration.ofMinutes(10).minusMillis(1), true),
        )
    }

    @Test fun genauEineMinuteIstExakt() {
        assertEquals(CountdownGlyph.Minutes(1, approx = false), countdownGlyph(Duration.ofMinutes(1), true))
    }

    @Test fun neunundfuenfzigSekundenSindJetzt() {
        assertEquals(CountdownGlyph.Now, countdownGlyph(Duration.ofSeconds(59), true))
    }

    @Test fun nullIstJetzt() {
        assertEquals(CountdownGlyph.Now, countdownGlyph(Duration.ZERO, true))
    }

    @Test fun negativIstJetzt() {
        assertEquals(CountdownGlyph.Now, countdownGlyph(Duration.ofSeconds(-30), true))
    }

    @Test fun deutlichNegativIstJetzt() {
        // toMinutes() ist hier −5, nicht 0 — der Vergleich muss auch das fangen.
        assertEquals(CountdownGlyph.Now, countdownGlyph(Duration.ofMinutes(-5), true))
    }

    @Test fun abgeschaltetIstImmerKeinSymbol() {
        assertEquals(CountdownGlyph.None, countdownGlyph(Duration.ofHours(3), false))
        assertEquals(CountdownGlyph.None, countdownGlyph(Duration.ofMinutes(5), false))
        assertEquals(CountdownGlyph.None, countdownGlyph(Duration.ZERO, false))
    }

    @Test fun keinNaechstesGebetIstKeinSymbol() {
        assertEquals(CountdownGlyph.None, countdownGlyph(null, true))
        assertEquals(CountdownGlyph.None, countdownGlyph(null, false))
    }

    // --- Die Rundung folgt remainingStepShort: abgerundet, nicht gerundet ---

    @Test fun siebenundzwanzigMinutenZeigenZwanzigWieDerText() {
        // Ein Symbol, das mehr Zeit verspricht als der Text daneben, waere
        // schlimmer als kein Symbol.
        assertEquals(CountdownGlyph.Minutes(20, approx = true), countdownGlyph(Duration.ofMinutes(27), true))
        assertEquals("20+ Min", remainingStepShort(Duration.ofMinutes(27)))
    }

    @Test fun einundzwanzigUhrDreissigAlsStundenWirdAbgerundet() {
        assertEquals(
            CountdownGlyph.Hours(3, capped = false),
            countdownGlyph(Duration.ofHours(3).plusMinutes(59), true),
        )
    }

    // --- Der tragende Test: null zusaetzliche Weckvorgaenge ---

    @Test fun `das Symbol wechselt nie, ohne dass sich auch der Stufentext aendert`() {
        // PrayerAlarmScheduler.scheduleDisplayStep weckt genau an den Momenten,
        // an denen sich remainingStepShort aendert. Solange das Symbol nur dort
        // wechselt, kostet es null zusaetzliche Weckvorgaenge.
        //
        // Die Umkehrung gilt ABSICHTLICH nicht und darf nicht geprueft werden:
        // ueber 9 Stunden bleibt das Symbol bei Hours(9, capped = true),
        // waehrend der Text weiterzaehlt ("13+ Std"). Das ist der Deckel, kein
        // Fehler — nicht "reparieren".
        var previousGlyph = countdownGlyph(Duration.ZERO, true)
        var previousText = remainingStepShort(Duration.ZERO)
        for (second in 1..14 * 3600) {
            val remaining = Duration.ofSeconds(second.toLong())
            val glyph = countdownGlyph(remaining, true)
            val text = remainingStepShort(remaining)
            if (glyph != previousGlyph) {
                assertEquals(
                    "Symbolwechsel bei $second s ($previousGlyph -> $glyph) ohne Textwechsel",
                    true,
                    text != previousText,
                )
            }
            previousGlyph = glyph
            previousText = text
        }
    }

    @Test fun ueberNeunStundenBleibtDerDeckelStehenWaehrendDerTextWeiterzaehlt() {
        // Die absichtlich einseitige Richtung des Invariants, als Beispiel
        // festgenagelt.
        assertEquals(CountdownGlyph.Hours(9, capped = true), countdownGlyph(Duration.ofHours(10), true))
        assertEquals(CountdownGlyph.Hours(9, capped = true), countdownGlyph(Duration.ofHours(13), true))
        assertEquals("10+ Std", remainingStepShort(Duration.ofHours(10)))
        assertEquals("13+ Std", remainingStepShort(Duration.ofHours(13)))
    }
}
