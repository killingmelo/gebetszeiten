package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

/**
 * Amtliche Diyanet-Zeiten aus gebündelten Offline-Tabellen (assets/official/).
 * Lookup per Koordinaten: nächstgelegener deutscher Diyanet-Standort ≤ 25 km.
 * Nicht abgedeckt (Ausland, fehlendes Jahr) → null → Aufrufer rechnet selbst.
 */
object BundledOfficialSource {

    private const val LOCATIONS_ASSET = "official/locations-de.tsv"

    @Volatile private var locations: List<OfficialLocation>? = null
    @Volatile private var tables: Map<String, Map<LocalDate, SixTimes>> = emptyMap()

    suspend fun get(context: Context, lat: Double, lng: Double, date: LocalDate): SixTimes? =
        nearestCovering(context, lat, lng, date)?.second

    /** Anzeigename des Diyanet-Standorts, dessen amtliche Tabelle (Datum!) greift. */
    suspend fun locationNameFor(context: Context, lat: Double, lng: Double, date: LocalDate): String? =
        nearestCovering(context, lat, lng, date)?.first?.name

    /** Nächstgelegener gebündelter Diyanet-Standort ≤ 25 km, oder null.
     *  Auch vom Online-Fetcher genutzt (liefert die exakte diyanetId). */
    suspend fun nearestLocation(context: Context, lat: Double, lng: Double): OfficialLocation? =
        OfficialLocations.nearest(allLocations(context), lat, lng)

    private suspend fun nearestCovering(
        context: Context,
        lat: Double,
        lng: Double,
        date: LocalDate,
    ): Pair<OfficialLocation, SixTimes>? {
        val loc = nearestLocation(context, lat, lng) ?: return null
        val time = table(context, "official/tables/${loc.tableRef}-${date.year}.tsv")[date] ?: return null
        return loc to time
    }

    private suspend fun allLocations(context: Context): List<OfficialLocation> {
        locations?.let { return it }
        return withContext(Dispatchers.IO) {
            locations ?: load(context).also { locations = it }
        }
    }

    private fun load(context: Context): List<OfficialLocation> = try {
        context.assets.open(LOCATIONS_ASSET).bufferedReader(Charsets.UTF_8).useLines {
            parseOfficialLocations(it)
        }
    } catch (e: FileNotFoundException) {
        emptyList()
    }

    private suspend fun table(context: Context, path: String): Map<LocalDate, SixTimes> {
        tables[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            tables[path] ?: loadTable(context, path).also { tables = tables + (path to it) }
        }
    }

    private fun loadTable(context: Context, path: String): Map<LocalDate, SixTimes> = try {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { parseOfficialTimes(it) }
    } catch (e: FileNotFoundException) {
        emptyMap()
    }
}
