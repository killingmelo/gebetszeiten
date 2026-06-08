package de.gebetszeiten.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.GeoLocation
import kotlinx.coroutines.flow.first

private val Context.locationStore: DataStore<Preferences> by preferencesDataStore(name = "wear_location")

/** Location on the watch, synced from the phone via the Data Layer. Falls back
 *  to a default until the first sync arrives. */
object WearSettings {

    private val LAT = doublePreferencesKey("lat")
    private val LNG = doublePreferencesKey("lng")
    private val DEFAULT = GeoLocation(49.4521, 11.0767) // Nürnberg

    suspend fun location(context: Context): GeoLocation {
        val prefs = context.locationStore.data.first()
        val lat = prefs[LAT]
        val lng = prefs[LNG]
        return if (lat != null && lng != null) GeoLocation(lat, lng) else DEFAULT
    }

    suspend fun save(context: Context, latitude: Double, longitude: Double) {
        context.locationStore.edit {
            it[LAT] = latitude
            it[LNG] = longitude
        }
    }
}
