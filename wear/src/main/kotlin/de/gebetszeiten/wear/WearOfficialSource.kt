package de.gebetszeiten.wear

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
 * Amtliche Diyanet-Zeiten aus den geteilten Offline-Tabellen (assets/official/),
 * identisch zum Phone: nächstgelegener Diyanet-Standort ≤ 25 km, sonst null
 * (→ Aufrufer rechnet selbst). Dünner Context-Adapter um die pure core-Logik.
 */
object WearOfficialSource {

    private const val LOCATIONS_ASSET = "official/locations-de.tsv"

    @Volatile private var locations: List<OfficialLocation>? = null
    @Volatile private var tables: Map<String, Map<LocalDate, SixTimes>> = emptyMap()

    suspend fun get(context: Context, lat: Double, lng: Double, date: LocalDate): SixTimes? {
        val loc = OfficialLocations.nearest(allLocations(context), lat, lng) ?: return null
        return table(context, "official/tables/${loc.tableRef}-${date.year}.tsv")[date]
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
