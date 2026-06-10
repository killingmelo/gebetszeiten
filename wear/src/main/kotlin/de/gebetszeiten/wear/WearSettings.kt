package de.gebetszeiten.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val SHOW_REMAINING = booleanPreferencesKey("show_remaining")
    private val VIBRATE = booleanPreferencesKey("vibrate")
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

    /** Show remaining time instead of the clock time (app + complication). */
    suspend fun showRemaining(context: Context): Boolean =
        context.locationStore.data.first()[SHOW_REMAINING] ?: false

    suspend fun saveShowRemaining(context: Context, value: Boolean) {
        context.locationStore.edit { it[SHOW_REMAINING] = value }
    }

    /** Gentle wrist vibration at each prayer time (default off). */
    suspend fun vibrate(context: Context): Boolean =
        context.locationStore.data.first()[VIBRATE] ?: false

    suspend fun saveVibrate(context: Context, value: Boolean) {
        context.locationStore.edit { it[VIBRATE] = value }
    }

    suspend fun save(context: Context, city: String, latitude: Double, longitude: Double) {
        context.locationStore.edit {
            it[CITY] = city
            it[LAT] = latitude
            it[LNG] = longitude
        }
    }
}
