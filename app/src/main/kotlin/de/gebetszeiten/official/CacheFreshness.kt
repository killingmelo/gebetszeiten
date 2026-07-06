package de.gebetszeiten.official

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos

/** ~1 km Toleranz: Cache gilt nur für den Standort, für den er geladen wurde.
 *  Grad-Näherung statt Haversine reicht hier (kleine Distanzen, grobe Schwelle). */
fun stampMatches(stampLat: Double?, stampLng: Double?, lat: Double, lng: Double): Boolean {
    if (stampLat == null || stampLng == null) return false
    val dLatKm = abs(lat - stampLat) * 111.0
    val dLngKm = abs(lng - stampLng) * 111.0 * cos(Math.toRadians(lat))
    return dLatKm <= 1.0 && dLngKm <= 1.0
}

/** Neu laden, wenn der Cache nicht zum Standort passt oder weniger als
 *  [minFutureDays] Tage Zukunft ab [today] abdeckt (Proxy liefert ~31 Tage). */
fun needsRefresh(
    coveredUntil: LocalDate?,
    today: LocalDate,
    stampOk: Boolean,
    minFutureDays: Long = 7,
): Boolean {
    if (!stampOk) return true
    if (coveredUntil == null) return true
    return coveredUntil.isBefore(today.plusDays(minFutureDays))
}
