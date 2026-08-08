package de.gebetszeiten.data

/** Online-Ortssuche als Fallback, wenn die gebündelte Liste nichts findet.
 *  Implementierung nur im online-Flavor (PlaceSearchProvider) — der
 *  offline-Flavor liefert null und bleibt beweisbar netzwerkfrei. */
interface CityLookup {
    /** Bis zu [limit] Treffer mit Koordinaten; leere Liste bei Fehler/offline. */
    suspend fun search(query: String, limit: Int): List<City>
}
