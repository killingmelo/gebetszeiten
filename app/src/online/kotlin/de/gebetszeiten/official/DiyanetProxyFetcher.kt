package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime

/**
 * Fetches official Diyanet times from the community proxy
 * (prayertimes.api.abdus.dev), a 1:1 scrape of namazvakitleri.diyanet.gov.tr.
 *
 * For Germany, the exact `diyanetId` of the bundled nearest location is used
 * (id-accurate, consistent with the offline table and footer). Outside the
 * bundled coverage, falls back to resolving the Diyanet location id by city
 * name. Any failure returns an empty map so callers fall back to the offline
 * calculation.
 */
class DiyanetProxyFetcher(private val context: android.content.Context) : OfficialTimesFetcher {

    private val base = "https://prayertimes.api.abdus.dev/api/diyanet"

    override suspend fun fetch(settings: AppSettings): Map<LocalDate, SixTimes> =
        withContext(Dispatchers.IO) {
            try {
                // Deutschland: exakte diyanetId des gebündelten Nearest-Standorts
                // (id-genau, konsistent zu Offline-Tabelle und Footer). Sonst
                // wie bisher Namens-Suche über den Proxy.
                val locationId = BundledOfficialSource
                    .nearestLocation(context, settings.latitude, settings.longitude)?.diyanetId
                    ?: resolveLocationId(settings.city)
                    ?: return@withContext emptyMap()
                parseSchedule(httpGet("$base/prayertimes?location_id=$locationId"))
            } catch (e: Exception) {
                // Bewusst schlucken (Aufrufer fällt auf Bundle/Berechnung zurück),
                // aber loggen — sonst ist ein Abruf-Fehler nicht diagnostizierbar.
                android.util.Log.w("DiyanetFetch", "Online-Abruf fehlgeschlagen", e)
                emptyMap()
            }
        }

    private fun resolveLocationId(city: String): Int? {
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        val arr = JSONArray(httpGet("$base/search?q=$q"))
        if (arr.length() == 0) return null
        // The proxy may return nearby places first (e.g. "Altdorf b. Nürnberg"
        // before "Nürnberg"). Prefer an exact accent/case-insensitive match on
        // the town (region), then on the city field, otherwise the first hit.
        // Region VOR city: türkische Großstädte listen jeden Stadtbezirk mit
        // city="İSTANBUL" — ein City-Match würde sonst den erstbesten Bezirk
        // (Arnavutköy) statt des Zentrums (region="İSTANBUL") liefern.
        val target = normalize(city)
        var cityMatch: Int? = null
        var fallback: Int? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optInt("id").takeIf { it != 0 } ?: continue
            if (fallback == null) fallback = id
            if (normalize(o.optString("region")) == target) return id
            if (cityMatch == null && normalize(o.optString("city")) == target) cityMatch = id
        }
        return cityMatch ?: fallback
    }

    /** Lower-case and strip diacritics so "Nürnberg" matches "NURNBERG". */
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    private fun parseSchedule(body: String): Map<LocalDate, SixTimes> {
        val arr = JSONArray(body)
        val out = LinkedHashMap<LocalDate, SixTimes>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val date = LocalDate.parse(o.getString("date").substring(0, 10))
            out[date] = SixTimes(
                fajr = LocalTime.parse(o.getString("fajr")),
                sunrise = LocalTime.parse(o.getString("sun")),
                dhuhr = LocalTime.parse(o.getString("dhuhr")),
                asr = LocalTime.parse(o.getString("asr")),
                maghrib = LocalTime.parse(o.getString("maghrib")),
                isha = LocalTime.parse(o.getString("isha")),
            )
        }
        return out
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
