package de.gebetszeiten.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A city resolved from the bundled offline database. */
data class City(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    /** Verwaltungsregion (GeoNames admin1, z. B. „Yalova") — zur
     *  Unterscheidung gleichnamiger Kleinorte. Leer bei Altdaten. */
    val region: String? = null,
)

/** Kompakter interner Eintrag: Koordinaten als Float (~1 m Genauigkeit reicht
 *  für die Ortswahl), [normAscii] nur wenn er vom [normName] abweicht —
 *  bei 235k Zeilen zählt jeder eingesparte String. */
internal class CityEntry(
    val name: String,
    val country: String,
    val latitude: Float,
    val longitude: Float,
    val normName: String,
    val normAscii: String?,
    val region: String?,
    val normAliases: List<String> = emptyList(),
) {
    fun toCity() = City(name, country, latitude.toDouble(), longitude.toDouble(), region)
}

internal class CityAliases(
    val normAliases: List<String>,
    /** Deutscher Anzeigename (überschreibt den englischen GeoNames-Namen),
     *  gesetzt durch „1" in Spalte 4 der Alias-Zeile. */
    val displayName: String?,
)

/** Parst `city-aliases.tsv` (alias, name, country[, display]) zu einer Map
 *  "name|country" → Aliasse + optionaler Anzeigename. Pure Funktion, unit-testbar. */
internal fun parseCityAliases(lines: Sequence<String>): Map<String, CityAliases> {
    val aliases = HashMap<String, MutableList<String>>()
    val display = HashMap<String, String>()
    lines.forEach { line ->
        val c = line.split('\t')
        if (c.size < 3 || c[0].isBlank()) return@forEach
        val key = "${c[1]}|${c[2]}"
        aliases.getOrPut(key) { mutableListOf() } += TextNormalize.normalize(c[0])
        if (c.size >= 4 && c[3].trim() == "1") display[key] = c[0]
    }
    return aliases.mapValues { (key, list) -> CityAliases(list, display[key]) }
}

/** Parst `cities.tsv`-Zeilen (name, asciiname, country, lat, lng[, admin1]).
 *  Toleriert das alte 5-Spalten-Format. Aliasse/Anzeigename greifen nur auf dem
 *  ersten (= populationsreichsten) Vorkommen eines "name|country"-Schlüssels —
 *  sonst hieße jedes türkische Dorf „Esenköy" plötzlich gleich. Pure Funktion. */
internal fun parseCities(lines: Sequence<String>, aliases: Map<String, CityAliases>): List<CityEntry> {
    // Interning: ~250 Ländercodes und ~4k Regionsnamen statt 470k Strings.
    val intern = HashMap<String, String>()
    fun interned(s: String): String = intern.getOrPut(s) { s }
    val aliasUsed = HashSet<String>()
    return lines.mapNotNull { line ->
        val c = line.split('\t')
        if (c.size < 5) return@mapNotNull null
        val lat = c[3].toFloatOrNull() ?: return@mapNotNull null
        val lng = c[4].toFloatOrNull() ?: return@mapNotNull null
        val key = "${c[0]}|${c[2]}"
        val alias = aliases[key]?.takeIf { aliasUsed.add(key) }
        val normName = TextNormalize.normalize(c[0])
        val normAscii = TextNormalize.normalize(c[1]).takeIf { it != normName }
        CityEntry(
            // Deutscher Anzeigename, falls definiert („Nuremberg" → „Nürnberg").
            name = alias?.displayName ?: c[0],
            country = interned(c[2]),
            latitude = lat,
            longitude = lng,
            normName = normName,
            normAscii = normAscii,
            region = c.getOrNull(5)?.trim()?.ifEmpty { null }?.let(::interned),
            normAliases = alias?.normAliases ?: emptyList(),
        )
    }.toList()
}

/** Suche über die geparsten Einträge: Präfix-Treffer bevorzugt, Substring nur
 *  als Fallback. Sequence + take = Early-Exit, sobald [limit] voll ist —
 *  häufige Anfragen scannen so nie die ganze Liste. Pure Funktion. */
internal fun searchEntries(entries: List<CityEntry>, query: String, limit: Int): List<City> {
    val q = TextNormalize.normalize(query)
    val chosen = when {
        q.isEmpty() -> entries.take(limit)
        else -> {
            val prefix = entries.asSequence().filter {
                it.normName.startsWith(q) || it.normAscii?.startsWith(q) == true ||
                    it.normAliases.any { a -> a.startsWith(q) }
            }.take(limit).toList()
            prefix.ifEmpty {
                entries.asSequence().filter {
                    it.normName.contains(q) || it.normAscii?.contains(q) == true ||
                        it.normAliases.any { a -> a.contains(q) }
                }.take(limit).toList()
            }
        }
    }
    return chosen.map { it.toCity() }
}

/**
 * Offline worldwide city database (GeoNames cities500, ~235k Orte ab ~500
 * Einwohnern plus Muss-Orte, CC BY 4.0), bundled as a TSV asset
 * (name, asciiname, country, lat, lng, admin1) sorted by population so common
 * cities rank first (~13 MB raw, zip-compressed im APK). No network lookup —
 * searching/resolving coordinates is fully local. Regeneriert via
 * tools/cities/build_cities.py.
 */
object Cities {

    @Volatile
    private var cache: List<CityEntry>? = null

    private suspend fun entries(context: Context): List<CityEntry> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            cache ?: load(context).also { cache = it }
        }
    }

    /** Lädt die Datenbank vorab (z. B. beim Öffnen der Settings), damit die
     *  erste Suche nicht an der einmaligen TSV-Parse-Latenz hängt. */
    suspend fun preload(context: Context) {
        entries(context)
    }

    private fun load(context: Context): List<CityEntry> {
        val aliases = context.assets.open("city-aliases.tsv").use { raw ->
            raw.bufferedReader(Charsets.UTF_8).useLines { parseCityAliases(it) }
        }
        context.assets.open("cities.tsv").use { raw ->
            raw.bufferedReader(Charsets.UTF_8).useLines { lines ->
                return parseCities(lines, aliases)
            }
        }
    }

    /** Up to [limit] cities matching [query] by name (accent/case insensitive),
     *  ordered by population (the underlying asset order). Prefix matches are
     *  preferred; substring matches are used only when there is no prefix hit.
     *  Filtert off-main — 235k Zeilen pro Tastendruck gehören nicht auf den
     *  UI-Thread. */
    suspend fun search(context: Context, query: String, limit: Int = 20): List<City> {
        val all = entries(context)
        return withContext(Dispatchers.Default) { searchEntries(all, query, limit) }
    }
}
