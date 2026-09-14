package de.gebetszeiten.places

import de.gebetszeiten.data.City
import de.gebetszeiten.data.CityLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

    /** Eigene, kleine Kopie statt einer Abhaengigkeit von `:net-diyanet`: die
     *  Diyanet-Abrufer zogen in Aufgabe 2 in ihr eigenes Modul, ihr `httpGet`
     *  ist dort `internal` (modulprivat) und blieb es bewusst - dieser
     *  Geocoder hat mit Diyanet nichts zu tun und soll nicht an dessen Modul
     *  haengen, nur fuer einen schlanken GET mit Timeout. */
    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GebetszeitenApp (Ortssuche, ~1 Abruf/neuer Ort)")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
