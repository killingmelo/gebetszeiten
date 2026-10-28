package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.prayer.Sundial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Ausschnitt der Sonnenuhr und Lage der Beschriftung „2× Schatten“ für Orte
 * von Kapstadt bis Nordskandinavien: Stabfuß sichtbar, Platte nicht zu flach
 * oder zu hoch, Beschriftung innerhalb der Platte, frei von der k=1-Kurve und
 * ohne die eigene k=2-Kurve zu überdecken. Platten von 280 bis 380 dp Breite,
 * auch bei Schriftskalierung 1,4.
 */
class SundialLayoutTest {

    private val breiten = listOf(-45.0, -33.9, -6.2, 0.0, 21.4, 41.0, 49.45, 55.7, 60.0, 65.0)

    private fun frameFor(lat: Double): DialFrame {
        val noon = listOf(-23.44, 0.0, 23.44).mapNotNull { Sundial.shadowTip(lat, it, 0.0) }
        return dialFrame(Sundial.asrCurve(lat, 1), Sundial.asrCurve(lat, 2), noon)
    }

    @Test
    fun ausschnittZeigtStabfussUndBleibtImSeitenverhaeltnis() {
        for (lat in breiten) {
            val f = frameFor(lat)
            assertTrue("$lat: Fuß nicht sichtbar", f.west < 0 && f.east > 0 && f.south < 0 && f.north > 0)
            val aspect = f.width / f.height
            assertTrue("$lat: Seitenverhältnis $aspect", aspect in 0.89..1.61)
            assertTrue("$lat: höchstens 7 Stablängen nach Norden", f.north <= 7.0)
        }
    }

    @Test
    fun nuernbergZeigtVorAllemDenNordenDesFusses() {
        val f = frameFor(49.45)
        assertTrue("Süden ${f.south}", f.south > -1.2)
        assertTrue("Norden ${f.north}", f.north > 4.4)
    }

    @Test
    fun beschriftungLiegtInDerPlatteUndBeruehrtDieEinfacheKurveNicht() {
        for (plattePx in listOf(280.0, 312.0, 380.0)) for (scale in listOf(1.0, 1.4)) {
            // „2× Schatten“ in labelSmall mit 3 dp Rand: etwa 74 × 22 dp
            val wPx = 74.0 * scale
            val hPx = 22.0 * scale
            for (lat in breiten) {
                val f = frameFor(lat)
                val s = plattePx / f.width
                val gap = 6.0 / s
                val k1 = Sundial.asrCurve(lat, 1)
                val k2 = Sundial.asrCurve(lat, 2)
                val r = placeAsr2Label(f, k1, k2, wPx / s, hPx / s, gap)
                assertTrue("$lat/$scale: außerhalb der Platte $r", r.inside(f))
                val d = distanceRectPolyline(r, k1)
                assertTrue("$lat/$scale/$plattePx: Abstand zur k=1-Kurve ${d * s} dp", d >= gap * 0.999)
                val d2 = distanceRectPolyline(r, k2)
                assertTrue("$lat/$scale/$plattePx: überdeckt die k=2-Kurve", d2 >= gap / 2 * 0.999)
            }
        }
    }

    @Test
    fun stundenwinkelAusWeltzeitUndLaenge() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 12:00 Weltzeit auf Länge 0 ist wahrer Mittag (ohne Zeitgleichung)
        assertEquals(0.0, hourAngleDeg(ZonedDateTime.of(2026, 10, 28, 13, 0, 0, 0, berlin), 0.0), 1e-9)
        // eine Stunde später 15°
        assertEquals(15.0, hourAngleDeg(ZonedDateTime.of(2026, 10, 28, 14, 0, 0, 0, berlin), 0.0), 1e-9)
        // weit im Osten am Abend: zurück in (−180, 180]
        val h = hourAngleDeg(ZonedDateTime.of(2026, 10, 28, 20, 0, 0, 0, ZoneId.of("UTC")), 140.0)
        assertEquals(-100.0, h, 1e-9)
    }
}
