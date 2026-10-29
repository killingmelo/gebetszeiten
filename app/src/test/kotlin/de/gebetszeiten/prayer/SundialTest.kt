package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SundialTest {

    @Test fun noonShadowPointsNorth() {
        val p = Sundial.shadowTip(49.45, -13.0, 0.0)
        assertNotNull(p)
        assertEquals(0.0, p!!.east, 1e-9)
        assertEquals(1.917, p.north, 0.01)
    }

    @Test fun asrSingleShadowNuremberg() {
        val h = Sundial.asrHourAngle(49.45, -13.0, 1)
        assertNotNull(h)
        assertEquals(38.6, h!!, 0.2)
    }

    @Test fun asrDoubleShadowLaterThanSingle() {
        val single = Sundial.asrHourAngle(49.45, -13.0, 1)!!
        val double = Sundial.asrHourAngle(49.45, -13.0, 2)!!
        assertTrue(double > single)
    }

    @Test fun belowHorizonIsNull() {
        // Mitternacht: Stundenwinkel 180 Grad, Sonne weit unter dem Horizont
        assertNull(Sundial.shadowTip(49.45, -13.0, 180.0))
    }

    @Test fun polarNightHasNoAsr() {
        assertNull(Sundial.asrHourAngle(75.0, -23.44, 1))
    }

    @Test fun curvesHaveNoNaN() {
        for (lat in listOf(75.0, 0.0)) {
            val pts = Sundial.dayCurve(lat, -23.44) + Sundial.dayCurve(lat, 23.44) +
                Sundial.asrCurve(lat, 1) + Sundial.asrCurve(lat, 2)
            for (p in pts) {
                assertTrue("NaN/Inf bei lat=$lat: $p", p.east.isFinite() && p.north.isFinite())
            }
        }
    }

    @Test fun declinationOnOctober28() {
        assertEquals(-13.3, Sundial.declination(LocalDate.of(2026, 10, 28)), 1.0)
    }

    @Test fun declinationSolstices() {
        assertEquals(23.44, Sundial.declination(LocalDate.of(2026, 6, 21)), 0.5)
        assertEquals(-23.44, Sundial.declination(LocalDate.of(2026, 12, 21)), 0.5)
    }

    @Test fun asrNeverNaNAroundTheGlobe() {
        for (lat in listOf(-89.0, -66.0, 0.0, 49.45, 66.0, 89.0)) {
            var d = -23.44
            while (d <= 23.44) {
                for (k in 1..2) {
                    val h = Sundial.asrHourAngle(lat, d, k)
                    assertTrue("NaN bei lat=$lat d=$d k=$k", h == null || h.isFinite())
                }
                d += 1.0
            }
        }
    }

    @Test fun dayCurveIsFilteredAndNonEmptyInNuremberg() {
        val c = Sundial.dayCurve(49.45, -13.0)
        assertTrue(c.size > 100)
        assertTrue(c.size <= 401)
    }
}
