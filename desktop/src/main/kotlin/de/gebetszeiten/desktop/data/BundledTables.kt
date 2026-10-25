package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

/**
 * Amtliche Diyanet-Zeiten aus den mitgelieferten Tabellen (`official/`),
 * dieselben Dateien wie in der Handy-App. [open] liefert eine Ressource oder
 * null; fehlt sie, gibt es keine Zeiten (null), keinen Absturz.
 * Nicht abgedeckt (Ausland, Tag ausserhalb des Fensters) -> null.
 */
class BundledTables(private val open: (String) -> InputStream?) {

    private var locations: List<OfficialLocation>? = null
    private val tables = HashMap<String, Map<LocalDate, SixTimes>>()

    @Synchronized
    fun get(lat: Double, lng: Double, date: LocalDate): SixTimes? {
        val loc = OfficialLocations.nearest(allLocations(), lat, lng) ?: return null
        // Der tableRef traegt die Lauf-Kennung, das Datum steckt nicht im Namen.
        return table("official/tables/${loc.tableRef}.tsv")[date]
    }

    private fun allLocations(): List<OfficialLocation> =
        locations ?: read(LOCATIONS) { parseOfficialLocations(it) }.orEmpty().also { locations = it }

    private fun table(path: String): Map<LocalDate, SixTimes> =
        tables.getOrPut(path) { read(path) { parseOfficialTimes(it) }.orEmpty() }

    private fun <T> read(path: String, parse: (Sequence<String>) -> T): T? = try {
        open(path)?.bufferedReader(Charsets.UTF_8)?.useLines { parse(it) }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val LOCATIONS = "official/locations-de.tsv"
    }
}
