package de.gebetszeiten.places

import de.gebetszeiten.data.City
import de.gebetszeiten.data.CityLookup
import de.gebetszeiten.official.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Open-Meteo Geocoding (GeoNames-basiert, frei, kein API-Key): findet auch
 *  Kleinstorte, die der gebündelten cities500-Liste fehlen. Wird nur gefragt,
 *  wenn die lokale Suche leer ausgeht. Fehler → leere Liste (UI zeigt dann
 *  den normalen „Keine Treffer"-Zustand). */
object OpenMeteoGeocoder : CityLookup {

    private const val BASE = "https://geocoding-api.open-meteo.com/v1/search"

    override suspend fun search(query: String, limit: Int): List<City> =
        withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                parseGeocodingResponse(httpGet("$BASE?name=$q&count=$limit&language=de"))
            }.getOrDefault(emptyList())
        }

    /** Pure Parse-Funktion (unit-testbar): Open-Meteo `results[]` → [City]. */
    internal fun parseGeocodingResponse(body: String): List<City> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val lat = o.optDouble("latitude")
                val lng = o.optDouble("longitude")
                if (name.isEmpty() || lat.isNaN() || lng.isNaN()) continue
                add(
                    City(
                        name = name,
                        country = o.optString("country_code").trim().uppercase(),
                        latitude = lat,
                        longitude = lng,
                        region = o.optString("admin1").trim().ifEmpty { null },
                    ),
                )
            }
        }
    }
}
