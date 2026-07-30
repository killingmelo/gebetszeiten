package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheStampTest {

    @Test
    fun `gleicher Punkt passt, fehlender Stempel nicht`() {
        assertTrue(stampMatches(49.4521, 11.0767, 49.4521, 11.0767))
        assertFalse(stampMatches(null, 11.0767, 49.4521, 11.0767))
        assertFalse(stampMatches(49.4521, null, 49.4521, 11.0767))
    }

    @Test
    fun `1 km Toleranz - knapp drunter passt, deutlich drueber nicht`() {
        // 0.008 Grad Breite ~ 0.9 km; 0.02 Grad ~ 2.2 km.
        assertTrue(stampMatches(49.4521, 11.0767, 49.4601, 11.0767))
        assertFalse(stampMatches(49.4521, 11.0767, 49.4721, 11.0767))
    }

    /** Uebernommen aus dem bisherigen app-CacheFreshnessTest, damit die
     *  Abdeckung beim Verschieben nach core nicht verloren geht. */
    @Test
    fun `500 m passt, Nachbarstadt nicht`() {
        // ~500 m noerdlich von Nuernberg-Zentrum.
        assertTrue(stampMatches(49.4521, 11.0767, 49.4566, 11.0767))
        // Nuernberg vs. Fuerth (~7 km, Abweichung in Breite UND Laenge).
        assertFalse(stampMatches(49.4521, 11.0767, 49.4759, 10.9886))
        assertFalse(stampMatches(null, null, 49.4521, 11.0767))
    }
}
