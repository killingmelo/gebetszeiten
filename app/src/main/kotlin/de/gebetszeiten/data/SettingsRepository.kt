package de.gebetszeiten.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** Online flavor: use official Diyanet times fetched online (else offline calc). */
    val useOnline: Boolean = false,
    /** Show optional voluntary (nafl) prayer windows. */
    val showNafl: Boolean = false,
    /** Show the Hanafi makruh (karaha) segments. */
    val showKaraha: Boolean = true,
    /** Extra text scaling on top of the system setting (1.0 = none). */
    val fontScale: Float = 1f,
    /** Stronger-contrast colour scheme. */
    val highContrast: Boolean = false,
    /** Prayer names (Prayer.name) that fire a silent reminder notification. */
    val reminders: Set<String> = DEFAULT_REMINDERS,
    /** Minutes before a prayer for an additional heads-up notification (0 = off). */
    val reminderLeadMinutes: Int = 0,
    /** Persistent silent "next prayer" notification (lock-screen visible). */
    val persistentNotification: Boolean = false,
) {
    companion object {
        val DEFAULT_REMINDERS = setOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA")

        // Sensible default until the user picks a location.
        val DEFAULT = AppSettings(
            latitude = 49.4521,
            longitude = 11.0767,
            city = "Nürnberg",
            showCountdown = false,
            useOnline = false,
            showNafl = false,
            showKaraha = true,
            fontScale = 1f,
            highContrast = false,
            reminders = DEFAULT_REMINDERS,
            reminderLeadMinutes = 0,
            persistentNotification = false,
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
        val USE_ONLINE = booleanPreferencesKey("use_online")
        val SHOW_NAFL = booleanPreferencesKey("show_nafl")
        val SHOW_KARAHA = booleanPreferencesKey("show_karaha")
        val FONT_SCALE = doublePreferencesKey("font_scale")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REMINDERS = stringSetPreferencesKey("reminders")
        val REMINDER_LEAD = intPreferencesKey("reminder_lead_minutes")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            latitude = prefs[Keys.LAT] ?: AppSettings.DEFAULT.latitude,
            longitude = prefs[Keys.LNG] ?: AppSettings.DEFAULT.longitude,
            city = prefs[Keys.CITY] ?: AppSettings.DEFAULT.city,
            showCountdown = prefs[Keys.COUNTDOWN] ?: AppSettings.DEFAULT.showCountdown,
            useOnline = prefs[Keys.USE_ONLINE] ?: AppSettings.DEFAULT.useOnline,
            showNafl = prefs[Keys.SHOW_NAFL] ?: AppSettings.DEFAULT.showNafl,
            showKaraha = prefs[Keys.SHOW_KARAHA] ?: AppSettings.DEFAULT.showKaraha,
            fontScale = (prefs[Keys.FONT_SCALE] ?: AppSettings.DEFAULT.fontScale.toDouble()).toFloat(),
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: AppSettings.DEFAULT.highContrast,
            reminders = prefs[Keys.REMINDERS] ?: AppSettings.DEFAULT.reminders,
            reminderLeadMinutes = prefs[Keys.REMINDER_LEAD] ?: AppSettings.DEFAULT.reminderLeadMinutes,
            persistentNotification = prefs[Keys.PERSISTENT_NOTIFICATION]
                ?: AppSettings.DEFAULT.persistentNotification,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAT] = value.latitude
            prefs[Keys.LNG] = value.longitude
            prefs[Keys.CITY] = value.city
            prefs[Keys.COUNTDOWN] = value.showCountdown
            prefs[Keys.USE_ONLINE] = value.useOnline
            prefs[Keys.SHOW_NAFL] = value.showNafl
            prefs[Keys.SHOW_KARAHA] = value.showKaraha
            prefs[Keys.FONT_SCALE] = value.fontScale.toDouble()
            prefs[Keys.HIGH_CONTRAST] = value.highContrast
            prefs[Keys.REMINDERS] = value.reminders
            prefs[Keys.REMINDER_LEAD] = value.reminderLeadMinutes
            prefs[Keys.PERSISTENT_NOTIFICATION] = value.persistentNotification
        }
    }
}
