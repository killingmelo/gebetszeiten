package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.data.TextNormalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Amtliche Diyanet-Zeiten aus gebündelten Offline-Tabellen (assets/official/).
 * Verfügbar in beiden Flavors, rein lokal — keine Netzwerknutzung.
 *
 * Aktuell abgedeckt: Nürnberg 2026. Für nicht abgedeckte Städte/Jahre liefert
 * [get] null, sodass der Aufrufer auf die Berechnung zurückfällt.
 */
object BundledOfficialSource {

    /** Normalisierter Stadtname → Asset-Pfade (eine Datei je Jahr). */
    private val TABLES: Map<String, List<String>> = mapOf(
        "nurnberg" to listOf("official/nuernberg-2026.tsv"),
    )

    @Volatile
    private var cache: Map<String, Map<LocalDate, SixTimes>> = emptyMap()

    /** Reine Registry-Auflösung (ohne Context, testbar). */
    fun assetPathsFor(city: String): List<String> =
        TABLES[TextNormalize.normalize(city)] ?: emptyList()

    suspend fun get(context: Context, city: String, date: LocalDate): SixTimes? {
        for (path in assetPathsFor(city)) {
            table(context, path)[date]?.let { return it }
        }
        return null
    }

    suspend fun covers(context: Context, city: String, date: LocalDate): Boolean =
        get(context, city, date) != null

    private suspend fun table(context: Context, path: String): Map<LocalDate, SixTimes> {
        cache[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            cache[path] ?: load(context, path).also { cache = cache + (path to it) }
        }
    }

    private fun load(context: Context, path: String): Map<LocalDate, SixTimes> =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines {
            parseOfficialTimes(it)
        }
}
