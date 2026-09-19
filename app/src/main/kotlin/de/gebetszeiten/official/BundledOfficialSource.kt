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
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Amtliche Diyanet-Zeiten aus gebündelten Offline-Tabellen (assets/official/).
 * Lookup per Koordinaten: nächstgelegener deutscher Diyanet-Standort ≤ 25 km.
 * Nicht abgedeckt (Ausland, Tag außerhalb des Fensters) → null → Aufrufer
 * rechnet selbst.
 */
object BundledOfficialSource {

    private const val LOCATIONS_ASSET = "official/locations-de.tsv"

    /** `internal`, nicht `private`: `CoverageAssetTest` liest die
     *  ausgelieferte Datei gegen und soll den Pfad nicht ein zweites Mal
     *  hinschreiben muessen — eine zweite Schreibweise waere ein zweiter
     *  Ort, an dem ein Tippfehler unbemerkt bleibt. */
    internal const val COVERAGE_ASSET = "official/coverage.tsv"

    @Volatile private var locations: List<OfficialLocation>? = null
    @Volatile private var tables: Map<String, Map<LocalDate, SixTimes>> = emptyMap()

    /** Die anderen beiden Caches hier erkennen „schon geladen" am Wert selbst
     *  (Liste/Map ungleich null). Beim Abdeckungsende ist `null` ein
     *  gueltiges Ergebnis — ohne dieses Flag wuerde eine fehlende Datei bei
     *  jedem Aufruf erneut geoeffnet. */
    @Volatile private var coverageRead = false
    @Volatile private var cachedCoverageEnd: LocalDate? = null

    /** Vorab laden (beim Öffnen der Einstellungen), damit die erste
     *  Badge-Berechnung nicht an der TSV-Parse-Latenz hängt — analog
     *  [DiyanetPlaceIndex.preload]. Wärmt nur die Standortliste (die
     *  einzelnen Jahrestabellen sind klein und werden bei Bedarf geladen). */
    suspend fun preload(context: Context) {
        allLocations(context)
    }

    suspend fun get(context: Context, lat: Double, lng: Double, date: LocalDate): SixTimes? =
        nearestCovering(context, lat, lng, date)?.second

    /** Anzeigename des Diyanet-Standorts, dessen amtliche Tabelle (Datum!) greift. */
    suspend fun locationNameFor(context: Context, lat: Double, lng: Double, date: LocalDate): String? =
        nearestCovering(context, lat, lng, date)?.first?.name

    /**
     * Letzter Tag, den die gebündelten Tabellen abdecken — aus
     * `official/coverage.tsv`, geschrieben von `fetch_diyanet.py`, nicht von
     * Hand gepflegt. Gilt für ALLE gebündelten Standorte: die Pipeline
     * emittiert genau einen Lauf, und jeder Standort deckt dessen Fenster
     * lückenlos ab (siehe `OfficialAssetsIntegrityTest`).
     *
     * `null`, wenn die Datei fehlt oder unlesbar ist — eine fehlende Warnung
     * ist besser als eine App, die nicht startet.
     */
    suspend fun coverageEnd(context: Context): LocalDate? {
        if (coverageRead) return cachedCoverageEnd
        return withContext(Dispatchers.IO) {
            if (coverageRead) {
                cachedCoverageEnd
            } else {
                loadCoverageEnd(context).also { cachedCoverageEnd = it; coverageRead = true }
            }
        }
    }

    private fun loadCoverageEnd(context: Context): LocalDate? = try {
        context.assets.open(COVERAGE_ASSET).bufferedReader(Charsets.UTF_8)
            .useLines { parseCoverageEnd(it) }
    } catch (e: IOException) {
        null
    }

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
        // Der `tableRef` traegt seit dem rollierenden Fenster die Lauf-Kennung
        // (`t000-20260919`), das Datum steckt NICHT mehr im Dateinamen: eine
        // Tabelle umspannt jetzt zwei Kalenderjahre. Index und Tabellen stammen
        // dadurch zwangslaeufig aus demselben Pipeline-Lauf — eine liegen
        // gebliebene Tabelle eines aelteren Laufs wird nie referenziert.
        val time = table(context, "official/tables/${loc.tableRef}.tsv")[date] ?: return null
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

/**
 * Zerlegt `coverage.tsv`: eine Zeile, zwei Spalten (erster TAB letzter
 * abgedeckter Tag). Geliefert wird der LETZTE — nur der sagt etwas darueber,
 * wie lange die Reserve noch traegt.
 *
 * Steht ausserhalb von [BundledOfficialSource] und nimmt keinen `Context`,
 * damit das Versprechen „unlesbar ergibt null, kein Absturz" ohne Android
 * pruefbar ist (`CoverageAssetTest`) — analog zu `parseOfficialLocations`
 * und `parseOfficialTimes`, die aus demselben Grund im geteilten Modul
 * liegen. Dorthin gehoert dieser Parser nicht: die Wear-App liest keine
 * Abdeckung.
 */
internal fun parseCoverageEnd(lines: Sequence<String>): LocalDate? {
    val ende = lines.firstOrNull { it.isNotBlank() }
        ?.split('\t')
        ?.getOrNull(1)
        ?.trim()
        ?: return null
    return try {
        LocalDate.parse(ende)
    } catch (e: DateTimeParseException) {
        null
    }
}
