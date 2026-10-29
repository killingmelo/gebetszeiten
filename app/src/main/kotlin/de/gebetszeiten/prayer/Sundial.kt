package de.gebetszeiten.prayer

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Punkt auf der Sonnenuhr-Platte in Stablängen; der Stabfuß liegt bei (0, 0). */
data class Pt(val east: Double, val north: Double)

/**
 * Geometrie einer horizontalen Sonnenuhr (reine Mathematik, ohne Android).
 * Schattenspitze eines Stabes der Länge 1 für Breite, Deklination und Stundenwinkel.
 */
object Sundial {

    private const val MAX_DECLINATION = 23.44
    private const val MIN_ALTITUDE_DEG = 3.0

    private fun rad(deg: Double) = deg * PI / 180.0

    /** sin(Sonnenhöhe) */
    private fun sinAltitude(latDeg: Double, declDeg: Double, hourAngleDeg: Double): Double {
        val phi = rad(latDeg)
        val d = rad(declDeg)
        return sin(phi) * sin(d) + cos(phi) * cos(d) * cos(rad(hourAngleDeg))
    }

    /** Schattenspitze (east, north) in Stablängen, null wenn die Sonne unter 3° Höhe steht. */
    fun shadowTip(latDeg: Double, declDeg: Double, hourAngleDeg: Double): Pt? {
        val phi = rad(latDeg)
        val d = rad(declDeg)
        val h = rad(hourAngleDeg)
        val up = sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h)
        if (up < sin(rad(MIN_ALTITUDE_DEG))) return null
        val north = cos(phi) * sin(d) - sin(phi) * cos(d) * cos(h)
        val east = -cos(d) * sin(h)
        return Pt(-east / up, -north / up)
    }

    /**
     * Stundenwinkel (Grad) des ʿAṣr-Beginns: kleinstes H > 0 mit
     * cot(Höhe) = k + cot(Mittagshöhe). k = 1 einfacher, k = 2 doppelter Schatten.
     * null, wenn die Sonne mittags nicht über dem Horizont steht oder das Ziel
     * vor Sonnenuntergang nie erreicht wird (z. B. Polartag).
     */
    fun asrHourAngle(latDeg: Double, declDeg: Double, k: Int): Double? {
        // 90° − φ + δ, für beide Halbkugeln über den Betrag
        val noonAltDeg = 90.0 - abs(latDeg - declDeg)
        if (noonAltDeg <= 0.0) return null
        val target = k + 1.0 / tan(rad(noonAltDeg))
        if (!target.isFinite()) return null

        // true, wenn der Schatten bei H mindestens so lang ist wie das Ziel (oder die Sonne untergegangen ist)
        fun reached(h: Double): Boolean {
            val s = sinAltitude(latDeg, declDeg, h)
            if (s <= 0.0) return true
            val cot = kotlin.math.sqrt(1.0 - s * s) / s
            return cot >= target
        }

        var lo = 0.0
        var hi = 120.0
        if (!reached(hi)) return null
        for (i in 0 until 60) {
            val m = (lo + hi) / 2.0
            if (reached(m)) hi = m else lo = m
        }
        val result = (lo + hi) / 2.0
        return if (result.isFinite()) result else null
    }

    /** Sonnendeklination in Grad, Näherung nach Cooper. */
    fun declination(date: LocalDate): Double =
        MAX_DECLINATION * sin(rad(360.0 * (284 + date.dayOfYear) / 365.0))

    /** Tageskurve der Schattenspitze: Stundenwinkel −100..100 in 0,5°-Schritten, ohne Punkte unter 3° Höhe. */
    fun dayCurve(latDeg: Double, declDeg: Double): List<Pt> =
        (0..400).mapNotNull { i -> shadowTip(latDeg, declDeg, -100.0 + 0.5 * i) }

    /** ʿAṣr-Kurve: Schattenspitze zum ʿAṣr-Beginn für Deklinationen −23,44..23,44 in 0,5°-Schritten. */
    fun asrCurve(latDeg: Double, k: Int): List<Pt> {
        val decls = ArrayList<Double>()
        var i = 0
        while (-MAX_DECLINATION + 0.5 * i <= MAX_DECLINATION) {
            decls.add(-MAX_DECLINATION + 0.5 * i)
            i++
        }
        if (decls.last() < MAX_DECLINATION) decls.add(MAX_DECLINATION)
        return decls.mapNotNull { d ->
            val h = asrHourAngle(latDeg, d, k) ?: return@mapNotNull null
            shadowTip(latDeg, d, h)
        }
    }
}
