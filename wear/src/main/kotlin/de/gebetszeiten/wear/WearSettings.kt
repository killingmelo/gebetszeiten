package de.gebetszeiten.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.GeoLocation
import kotlinx.coroutines.flow.first

private val Context.locationStore: DataStore<Preferences> by preferencesDataStore(name = "wear_location")

/** Location chosen directly on the watch (city picker). Falls back to a
 *  default until the user picks a city. */
object WearSettings {

    private val LAT = doublePreferencesKey("lat")
    private val LNG = doublePreferencesKey("lng")
    private val CITY = stringPreferencesKey("city")
    private val DEFAULT = GeoLocation(49.4521, 11.0767) // Nürnberg
    private const val DEFAULT_CITY = "Nürnberg"

    suspend fun location(context: Context): GeoLocation {
        val prefs = context.locationStore.data.first()
        val lat = prefs[LAT]
        val lng = prefs[LNG]
        return if (lat != null && lng != null) GeoLocation(lat, lng) else DEFAULT
    }

    suspend fun city(context: Context): String =
        context.locationStore.data.first()[CITY] ?: DEFAULT_CITY

    suspend fun save(context: Context, city: String, latitude: Double, longitude: Double) {
        context.locationStore.edit {
            it[CITY] = city
            it[LAT] = latitude
            it[LNG] = longitude
        }
    }
}
