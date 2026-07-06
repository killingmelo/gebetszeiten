package de.gebetszeiten.core.prayertimes.officialtimes

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Ein Diyanet-Standort aus dem gebündelten Index (assets/official/locations-de.tsv). */
data class OfficialLocation(
    val diyanetId: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val tableRef: String,
)

/** Parst den Standort-Index (`diyanetId name lat lng tableRef`, Tab-getrennt,
 *  kein Header). Defekte Zeilen werden übersprungen. */
fun parseOfficialLocations(lines: Sequence<String>): List<OfficialLocation> =
    lines.mapNotNull { line ->
        val c = line.trim().split('\t')
        if (c.size != 5) return@mapNotNull null
        val id = c[0].toIntOrNull() ?: return@mapNotNull null
        val lat = c[2].toDoubleOrNull() ?: return@mapNotNull null
        val lng = c[3].toDoubleOrNull() ?: return@mapNotNull null
        OfficialLocation(id, c[1], lat, lng, c[4])
    }.toList()

object OfficialLocations {

    private const val EARTH_KM = 6371.0

    /** Nächstgelegener Standort zu (lat,lng), oder null wenn weiter als [maxKm].
     *  Die Schwelle verhindert „amtliche" Zeiten eines viel zu fernen Ortes. */
    fun nearest(
        locations: List<OfficialLocation>,
        lat: Double,
        lng: Double,
        maxKm: Double = 25.0,
    ): OfficialLocation? =
        locations
            .minByOrNull { haversineKm(lat, lng, it.latitude, it.longitude) }
            ?.takeIf { haversineKm(lat, lng, it.latitude, it.longitude) <= maxKm }

    internal fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_KM * asin(min(1.0, sqrt(a)))
    }
}
