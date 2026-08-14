package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Online-Flavor: weltweiter Diyanet-Standortindex aus dem gebündelten Asset.
 * Löst Koordinaten in eine Diyanet-ID auf — der Weg, über den Orte ohne
 * eigenen Diyanet-Eintrag (z. B. Serdivan) amtliche Zeiten bekommen.
 */
object DiyanetPlaceIndex {

    private const val ASSET = "official/locations-world.tsv"

    @Volatile private var cache: List<DiyanetPlace>? = null

    /** Vorab laden (beim Öffnen der Einstellungen), damit die erste
     *  Badge-Berechnung nicht an der TSV-Parse-Latenz hängt. */
    suspend fun preload(context: Context) {
        places(context)
    }

    suspend fun nearest(context: Context, lat: Double, lng: Double): DiyanetPlace? {
        val all = places(context)
        return withContext(Dispatchers.Default) { DiyanetPlaces.nearest(all, lat, lng) }
    }

    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double =
        DiyanetPlaces.distanceKm(place, lat, lng)

    private suspend fun places(context: Context): List<DiyanetPlace> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            cache ?: load(context).also { cache = it }
        }
    }

    private fun load(context: Context): List<DiyanetPlace> = try {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines {
            parseDiyanetPlaces(it)
        }
    } catch (e: FileNotFoundException) {
        // Fehlendes Asset darf die Ortswahl nicht sprengen — die Kette fällt
        // dann auf Cache/Namenssuche zurück wie vor diesem Umbau.
        android.util.Log.w("DiyanetPlaceIndex", "Index-Asset fehlt", e)
        emptyList()
    }
}
