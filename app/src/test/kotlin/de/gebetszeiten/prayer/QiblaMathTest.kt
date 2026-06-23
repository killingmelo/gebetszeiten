package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaMathTest {
    @Test fun bearingDueNorthOfKaabaIsSouth() {
        assertEquals(180.0, QiblaMath.bearing(50.0, 39.8262), 0.5)   // gleicher Meridian, nördlich → Süd
    }
    @Test fun bearingDueSouthOfKaabaIsNorth() {
        val b = QiblaMath.bearing(0.0, 39.8262)                      // gleicher Meridian, südlich → Nord
        assertTrue("expected ~0/360 but was $b", b < 0.5 || b > 359.5)
    }
    @Test fun bearingFromNurembergIsSoutheast() {
        val b = QiblaMath.bearing(49.4521, 11.0767)
        assertTrue("expected 125..140 but was $b", b in 125.0..140.0)
        assertEquals("Südost", QiblaMath.cardinal(b))
    }
    @Test fun distanceFromKaabaIsZero() {
        assertEquals(0.0, QiblaMath.distanceKm(21.4225, 39.8262), 1.0)
    }
    @Test fun distanceFromNurembergIsPlausible() {
        assertTrue(QiblaMath.distanceKm(49.4521, 11.0767) in 3800.0..4200.0)
    }
    @Test fun cardinalMapping() {
        assertEquals("Nord", QiblaMath.cardinal(0.0))
        assertEquals("Ost", QiblaMath.cardinal(90.0))
        assertEquals("Süd", QiblaMath.cardinal(180.0))
        assertEquals("Südost", QiblaMath.cardinal(135.0))
    }
    @Test fun normalizeWraps() {
        assertEquals(350f, QiblaMath.normalizeDegrees(-10f), 0.001f)
        assertEquals(10f, QiblaMath.normalizeDegrees(370f), 0.001f)
    }
}
