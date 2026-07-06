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
    /** Immer die lokale astronomische Berechnung nutzen (amtliche Quellen ignorieren). */
    val useCalculated: Boolean = false,
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
    /** Reminder style: SILENT (default), VIBRATE, or SOUND. */
    val reminderStyle: String = STYLE_SILENT,
    /** Forced theme: SYSTEM (default), LIGHT, or DARK. */
    val themeMode: String = THEME_SYSTEM,
    /** Hijri date correction in days (moon-sighting differences), −2..+2. */
    val hijriOffsetDays: Int = 0,
    /** Semi-transparent widget background. */
    val widgetTransparent: Boolean = false,
    /** Remaining-time rendering: STEPS (static floor steps, most frugal) or
     *  EXACT (system-rendered live countdown). Legacy global value — feeds the
     *  per-surface defaults below on first read after the update. */
    val remainingPrecision: String = PRECISION_STEPS,
    /** Widget remaining-time mode: OFF, STEPS or EXACT — per-surface. */
    val widgetCountdown: String = COUNTDOWN_OFF,
    /** Lock-screen (persistent notification) remaining-time mode. */
    val notificationCountdown: String = COUNTDOWN_OFF,
) {
    /** True if any surface still needs the STEPS display-alarm chain. */
    fun anyStepsCountdown(): Boolean =
        widgetCountdown == PRECISION_STEPS ||
            (persistentNotification && notificationCountdown == PRECISION_STEPS)

    companion object {
        val DEFAULT_REMINDERS = setOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA")
        const val STYLE_SILENT = "SILENT"
        const val STYLE_VIBRATE = "VIBRATE"
        const val STYLE_SOUND = "SOUND"
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val PRECISION_STEPS = "STEPS"
        const val PRECISION_EXACT = "EXACT"
        const val COUNTDOWN_OFF = "OFF"

        // Sensible default until the user picks a location.
        val DEFAULT = AppSettings(
            latitude = 49.4521,
            longitude = 11.0767,
            city = "Nürnberg",
            showCountdown = false,
            useOnline = false,
            useCalculated = false,
            showNafl = false,
            showKaraha = true,
            fontScale = 1f,
            highContrast = false,
            reminders = DEFAULT_REMINDERS,
            reminderLeadMinutes = 0,
            persistentNotification = false,
            reminderStyle = STYLE_SILENT,
            themeMode = THEME_SYSTEM,
            hijriOffsetDays = 0,
            widgetTransparent = false,
            remainingPrecision = PRECISION_STEPS,
            widgetCountdown = COUNTDOWN_OFF,
            notificationCountdown = COUNTDOWN_OFF,
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
        val USE_CALCULATED = booleanPreferencesKey("use_calculated")
        val SHOW_NAFL = booleanPreferencesKey("show_nafl")
        val SHOW_KARAHA = booleanPreferencesKey("show_karaha")
        val FONT_SCALE = doublePreferencesKey("font_scale")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REMINDERS = stringSetPreferencesKey("reminders")
        val REMINDER_LEAD = intPreferencesKey("reminder_lead_minutes")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val REMINDER_STYLE = stringPreferencesKey("reminder_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
        val WIDGET_TRANSPARENT = booleanPreferencesKey("widget_transparent")
        val REMAINING_PRECISION = stringPreferencesKey("remaining_precision")
        val WIDGET_COUNTDOWN = stringPreferencesKey("widget_countdown")
        val NOTIFICATION_COUNTDOWN = stringPreferencesKey("notification_countdown")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        // Migration: surfaces that predate the per-surface split inherit the
        // old global "Restzeit anzeigen" + precision combination.
        val legacyCountdown = prefs[Keys.COUNTDOWN] ?: AppSettings.DEFAULT.showCountdown
        val legacyMode = if (legacyCountdown) {
            prefs[Keys.REMAINING_PRECISION] ?: AppSettings.DEFAULT.remainingPrecision
        } else {
            AppSettings.COUNTDOWN_OFF
        }
        AppSettings(
            latitude = prefs[Keys.LAT] ?: AppSettings.DEFAULT.latitude,
            longitude = prefs[Keys.LNG] ?: AppSettings.DEFAULT.longitude,
            city = prefs[Keys.CITY] ?: AppSettings.DEFAULT.city,
            showCountdown = prefs[Keys.COUNTDOWN] ?: AppSettings.DEFAULT.showCountdown,
            useOnline = prefs[Keys.USE_ONLINE] ?: AppSettings.DEFAULT.useOnline,
            useCalculated = prefs[Keys.USE_CALCULATED] ?: AppSettings.DEFAULT.useCalculated,
            showNafl = prefs[Keys.SHOW_NAFL] ?: AppSettings.DEFAULT.showNafl,
            showKaraha = prefs[Keys.SHOW_KARAHA] ?: AppSettings.DEFAULT.showKaraha,
            fontScale = (prefs[Keys.FONT_SCALE] ?: AppSettings.DEFAULT.fontScale.toDouble()).toFloat(),
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: AppSettings.DEFAULT.highContrast,
            reminders = prefs[Keys.REMINDERS] ?: AppSettings.DEFAULT.reminders,
            reminderLeadMinutes = prefs[Keys.REMINDER_LEAD] ?: AppSettings.DEFAULT.reminderLeadMinutes,
            persistentNotification = prefs[Keys.PERSISTENT_NOTIFICATION]
                ?: AppSettings.DEFAULT.persistentNotification,
            reminderStyle = prefs[Keys.REMINDER_STYLE] ?: AppSettings.DEFAULT.reminderStyle,
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.DEFAULT.themeMode,
            hijriOffsetDays = prefs[Keys.HIJRI_OFFSET] ?: AppSettings.DEFAULT.hijriOffsetDays,
            widgetTransparent = prefs[Keys.WIDGET_TRANSPARENT] ?: AppSettings.DEFAULT.widgetTransparent,
            remainingPrecision = prefs[Keys.REMAINING_PRECISION] ?: AppSettings.DEFAULT.remainingPrecision,
            widgetCountdown = prefs[Keys.WIDGET_COUNTDOWN] ?: legacyMode,
            notificationCountdown = prefs[Keys.NOTIFICATION_COUNTDOWN] ?: legacyMode,
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
            prefs[Keys.USE_CALCULATED] = value.useCalculated
            prefs[Keys.SHOW_NAFL] = value.showNafl
            prefs[Keys.SHOW_KARAHA] = value.showKaraha
            prefs[Keys.FONT_SCALE] = value.fontScale.toDouble()
            prefs[Keys.HIGH_CONTRAST] = value.highContrast
            prefs[Keys.REMINDERS] = value.reminders
            prefs[Keys.REMINDER_LEAD] = value.reminderLeadMinutes
            prefs[Keys.PERSISTENT_NOTIFICATION] = value.persistentNotification
            prefs[Keys.REMINDER_STYLE] = value.reminderStyle
            prefs[Keys.THEME_MODE] = value.themeMode
            prefs[Keys.HIJRI_OFFSET] = value.hijriOffsetDays
            prefs[Keys.WIDGET_TRANSPARENT] = value.widgetTransparent
            prefs[Keys.REMAINING_PRECISION] = value.remainingPrecision
            prefs[Keys.WIDGET_COUNTDOWN] = value.widgetCountdown
            prefs[Keys.NOTIFICATION_COUNTDOWN] = value.notificationCountdown
        }
    }
}
