package de.gebetszeiten.places

import de.gebetszeiten.data.CityLookup

/** Online flavor: Open-Meteo-Geocoding als Fallback der Ortssuche. */
object PlaceSearchProvider {
    fun lookup(): CityLookup? = OpenMeteoGeocoder
}
