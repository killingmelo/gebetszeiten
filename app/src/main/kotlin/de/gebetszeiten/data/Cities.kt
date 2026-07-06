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
)

private class CityEntry(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val normName: String,
    val normAscii: String,
    val normAliases: List<String> = emptyList(),
)

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

/**
 * Offline worldwide city database (GeoNames cities ≥15k population, CC BY 4.0),
 * bundled as a TSV asset (name, asciiname, country, lat, lng) sorted by
 * population so common cities rank first. The APK stores it zip-compressed
 * (~665 KB). No network lookup — searching/resolving coordinates is fully local.
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
                return lines.mapNotNull { line ->
                    val c = line.split('\t')
                    if (c.size < 5) return@mapNotNull null
                    val lat = c[3].toDoubleOrNull() ?: return@mapNotNull null
                    val lng = c[4].toDoubleOrNull() ?: return@mapNotNull null
                    val alias = aliases["${c[0]}|${c[2]}"]
                    CityEntry(
                        // Deutscher Anzeigename, falls definiert („Nuremberg" → „Nürnberg").
                        name = alias?.displayName ?: c[0],
                        country = c[2],
                        latitude = lat,
                        longitude = lng,
                        normName = TextNormalize.normalize(c[0]),
                        normAscii = TextNormalize.normalize(c[1]),
                        normAliases = alias?.normAliases ?: emptyList(),
                    )
                }.toList()
            }
        }
    }

    /** Up to [limit] cities matching [query] by name (accent/case insensitive),
     *  ordered by population (the underlying asset order). Prefix matches are
     *  preferred; substring matches are used only when there is no prefix hit. */
    suspend fun search(context: Context, query: String, limit: Int = 20): List<City> {
        val all = entries(context)
        val q = TextNormalize.normalize(query)
        val chosen = when {
            q.isEmpty() -> all.take(limit)
            else -> {
                val prefix = all.filter {
                    it.normName.startsWith(q) || it.normAscii.startsWith(q) ||
                        it.normAliases.any { a -> a.startsWith(q) }
                }.take(limit)
                prefix.ifEmpty {
                    all.filter {
                        it.normName.contains(q) || it.normAscii.contains(q) ||
                            it.normAliases.any { a -> a.contains(q) }
                    }.take(limit)
                }
            }
        }
        return chosen.map { City(it.name, it.country, it.latitude, it.longitude) }
    }
}
