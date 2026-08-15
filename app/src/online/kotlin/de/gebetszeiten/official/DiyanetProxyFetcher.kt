package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.TextNormalize
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
        val id = pickLocationId(httpGet("$base/search?q=$q"), city)
        // Loggen hier, NICHT in pickLocationId: die Auswahlfunktion bleibt
        // damit frei von Android und in JVM-Tests aufrufbar.
        if (id == null) android.util.Log.w("DiyanetFetch", "Namenssuche ohne Treffer fuer '$city'")
        return id
    }

    /** Reine Auswahlfunktion (unit-testbar): Antwortkörper der Namenssuche →
     *  Diyanet-ID. Getrennt von [resolveLocationId], damit die Auswahl ohne
     *  Netzaufruf und ohne Android-Abhängigkeit geprüft werden kann. */
    internal fun pickLocationId(body: String, city: String): Int? {
        val arr = JSONArray(body)
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

    /** Eine Normalisierung für die ganze App. Die frühere private Variante hier
     *  strippte nur NFD-Marken — das punktlose türkische ı (U+0131) ist aber
     *  nicht zerlegbar und überlebte, sodass „Şanlıurfa" das amtliche
     *  „ŞANLIURFA" verfehlte. [TextNormalize] bildet ı/ş/ğ/ç/ö/ü/ß explizit ab. */
    private fun normalize(s: String): String = TextNormalize.normalize(s)

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
