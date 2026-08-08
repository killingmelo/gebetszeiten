package de.gebetszeiten.places

import de.gebetszeiten.data.CityLookup

/** Offline flavor: keine Online-Ortssuche — die App bleibt beweisbar netzfrei. */
object PlaceSearchProvider {
    fun lookup(): CityLookup? = null
}
