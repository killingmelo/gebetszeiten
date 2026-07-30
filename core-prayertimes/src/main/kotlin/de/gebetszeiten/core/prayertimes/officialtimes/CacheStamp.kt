package de.gebetszeiten.core.prayertimes.officialtimes

import kotlin.math.abs
import kotlin.math.cos

/** ~1 km Toleranz: Cache gilt nur für den Standort, für den er geladen wurde.
 *  Grad-Näherung statt Haversine reicht hier (kleine Distanzen, grobe Schwelle).
 *  Geteilt: Stempel-Guard am Phone UND Übernahme-Vergleich auf der Uhr. */
fun stampMatches(stampLat: Double?, stampLng: Double?, lat: Double, lng: Double): Boolean {
    if (stampLat == null || stampLng == null) return false
    val dLatKm = abs(lat - stampLat) * 111.0
    val dLngKm = abs(lng - stampLng) * 111.0 * cos(Math.toRadians(lat))
    return dLatKm <= 1.0 && dLngKm <= 1.0
}
