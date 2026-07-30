package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime

/**
 * Community-Proxy-Quelle (prayertimes.api.abdus.dev), 1:1-Scrape von
 * namazvakitleri.diyanet.gov.tr — liefert ein 31-Tage-Fenster für eine
 * bekannte Diyanet-ID, dazu eine Namenssuche zum Auflösen der ID.
 */
class DiyanetProxyFetcher {

    private val base = "https://prayertimes.api.abdus.dev/api/diyanet"

    /** 31-Tage-Fenster für eine bekannte Diyanet-ID — Fallback-Quelle. */
    suspend fun fetchById(locationId: Int): Map<LocalDate, SixTimes> =
        withContext(Dispatchers.IO) {
            parseSchedule(httpGet("$base/prayertimes?location_id=$locationId"))
        }

    fun resolveLocationId(city: String): Int? {
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

}
