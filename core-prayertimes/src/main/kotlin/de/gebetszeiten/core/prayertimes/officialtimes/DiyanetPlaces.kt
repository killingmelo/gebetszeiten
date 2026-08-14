package de.gebetszeiten.core.prayertimes.officialtimes

import java.util.Locale

/**
 * Ein Diyanet-Standort aus dem weltweiten Index
 * (`app/src/online/assets/official/locations-world.tsv`).
 *
 * Abgrenzung zu [OfficialLocation]: jenes beantwortet „welche gebündelte
 * Jahrestabelle gilt" (Feld `tableRef`, auch offline und auf der Uhr), dieses
 * „welche Diyanet-ID wird abgerufen" plus die Anzeigedaten (Provinz, Land).
 */
data class DiyanetPlace(
    val diyanetId: Int,
    /** Bezirksname wie Diyanet ihn führt, in Großbuchstaben („SAKARYA"). */
    val name: String,
    val province: String,
    /** ISO2, aus der Geokodierung der Pipeline („TR"). */
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
)

/** Türkische Groß-/Kleinschreibung: „İSTANBUL" → „İstanbul", nicht „Istanbul". */
private val TURKISH = Locale.forLanguageTag("tr")

/** Diyanet schreibt alles groß; für die UI lesbar machen. */
fun DiyanetPlace.displayName(): String =
    name.lowercase(TURKISH).replaceFirstChar { it.titlecase(TURKISH) }

/** Parst den Index (`id name province iso2 lat lng`, Tab-getrennt, kein
 *  Header). Defekte Zeilen werden übersprungen — ein Tippfehler im Asset darf
 *  nicht die ganze Ortswahl lahmlegen. */
fun parseDiyanetPlaces(lines: Sequence<String>): List<DiyanetPlace> =
    lines.mapNotNull { line ->
        val c = line.trim().split('\t')
        if (c.size != 6) return@mapNotNull null
        val id = c[0].toIntOrNull() ?: return@mapNotNull null
        val lat = c[4].toDoubleOrNull() ?: return@mapNotNull null
        val lng = c[5].toDoubleOrNull() ?: return@mapNotNull null
        DiyanetPlace(id, c[1], c[2], c[3], lat, lng)
    }.toList()

object DiyanetPlaces {

    /** Nächstgelegener Diyanet-Standort, oder null jenseits von [maxKm].
     *  Die Schwelle verhindert „amtliche" Zeiten eines viel zu fernen Orts. */
    fun nearest(
        places: List<DiyanetPlace>,
        lat: Double,
        lng: Double,
        maxKm: Double = 25.0,
    ): DiyanetPlace? =
        places
            .minByOrNull { distanceKm(it, lat, lng) }
            ?.takeIf { distanceKm(it, lat, lng) <= maxKm }

    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double =
        OfficialLocations.haversineKm(lat, lng, place.latitude, place.longitude)
}
