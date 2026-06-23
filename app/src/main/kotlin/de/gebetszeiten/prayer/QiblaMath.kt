package de.gebetszeiten.prayer

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Qibla-Richtung und -Entfernung zur Kaaba, rein aus Koordinaten (kein GPS). */
object QiblaMath {
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LNG = 39.8262
    private const val EARTH_KM = 6371.0

    /** Initiale Großkreis-Peilung von (lat,lng) zur Kaaba, normalisiert 0..360. */
    fun bearing(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Luftlinie (Haversine) in km zur Kaaba. */
    fun distanceKm(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dp = Math.toRadians(KAABA_LAT - lat)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_KM * asin(min(1.0, sqrt(a)))
    }

    private val DIRS = arrayOf("Nord", "Nordost", "Ost", "Südost", "Süd", "Südwest", "West", "Nordwest")

    /** 8-Wind-Himmelsrichtung eines Winkels. */
    fun cardinal(bearing: Double): String {
        val norm = ((bearing % 360.0) + 360.0) % 360.0
        return DIRS[(norm / 45.0).roundToInt() % 8]
    }

    /** Winkel auf 0..360 normalisieren. */
    fun normalizeDegrees(deg: Float): Float = ((deg % 360f) + 360f) % 360f
}
