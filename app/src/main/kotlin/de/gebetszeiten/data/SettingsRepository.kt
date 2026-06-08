package de.gebetszeiten.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Manually configured location and widget options. No GPS, no network. */
data class AppSettings(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val showCountdown: Boolean,
) {
    companion object {
        // Sensible default until the user picks a location.
        val DEFAULT = AppSettings(
            latitude = 49.4521,
            longitude = 11.0767,
            city = "Nürnberg",
            showCountdown = false,
        )
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LAT = doublePreferencesKey("latitude")
        val LNG = doublePreferencesKey("longitude")
        val CITY = stringPreferencesKey("city")
        val COUNTDOWN = booleanPreferencesKey("show_countdown")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            latitude = prefs[Keys.LAT] ?: AppSettings.DEFAULT.latitude,
            longitude = prefs[Keys.LNG] ?: AppSettings.DEFAULT.longitude,
            city = prefs[Keys.CITY] ?: AppSettings.DEFAULT.city,
            showCountdown = prefs[Keys.COUNTDOWN] ?: AppSettings.DEFAULT.showCountdown,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAT] = value.latitude
            prefs[Keys.LNG] = value.longitude
            prefs[Keys.CITY] = value.city
            prefs[Keys.COUNTDOWN] = value.showCountdown
        }
    }
}
