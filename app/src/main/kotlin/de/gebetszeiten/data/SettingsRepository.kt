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
    /** Region/Provinz des aktuellen Orts, sofern bekannt (`null` = unbekannt).
     *  Nur zum Unterscheiden gleichnamiger Orte da: ohne dieses Feld verliert
     *  ein Suchtreffer seine Region beim Speichern, und `recentPlaceLabel`
     *  könnte zwei Favoriten „Esenköy" nie auseinanderhalten. */
    val region: String? = null,
    /** Online flavor: use official Diyanet times fetched online (else offline calc).
     *  Im Online-Flavor ab Werk an — amtliche Zeiten sind der Zweck des Flavors;
     *  der Settings-Schalter bleibt das Opt-out. Offline immer false. */
    val useOnline: Boolean = de.gebetszeiten.official.OfficialTimesProvider.isOnline,
    /** Immer die lokale astronomische Berechnung nutzen (amtliche Quellen ignorieren). */
    val useCalculated: Boolean = false,
    /** Show optional voluntary (nafl) prayer windows. */
    val showNafl: Boolean = false,
    /** Show the Hanafi makruh (karaha) segments. */
    val showKaraha: Boolean = true,
    /** Abgeleitete Gemeinschaftsgebetszeit (Sabah-Cemaat) unter Fajr anzeigen. */
    val showCemaat: Boolean = false,
    /** Cemaat-Vorlauf: Minuten vor Sonnenaufgang (übliche Diyanet-Praxis: 30). */
    val cemaatOffsetMinutes: Int = 30,
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
    /** Restzeit-Darstellung fuer ALLE Flaechen: OFF, PRECISION_STEPS
     *  (abgerundete Stufen, die die App selbst weiterstellt) oder
     *  PRECISION_EXACT (Live-Countdown, vom System gezeichnet).
     *
     *  Der eine Regler, der die vier alten (`show_countdown`,
     *  `remaining_precision`, `widget_countdown`, `notification_countdown`)
     *  ersetzt: Hauptbildschirm, Widget, Dauerbenachrichtigung und
     *  Statusleisten-Symbol folgen ihm gemeinsam. Bestandsstaende werden in
     *  [countdownModeFromPrefs] einmalig zusammengefuehrt.
     *
     *  [persistentNotification] bleibt daneben eigenstaendig — es entscheidet,
     *  OB es eine Benachrichtigung gibt, nicht wie sie aussieht. */
    val countdownMode: String = COUNTDOWN_OFF,
    /** Zuletzt gewählte Orte (neuester zuerst), für schnellen Ortswechsel ohne Suche. */
    val recentPlaces: List<City> = emptyList(),
    /** Bewusst gemerkte Orte (in der Reihenfolge des Hinzufügens). Unabhängig
     *  von [recentPlaces]: manuell und dauerhaft statt automatisch und flüchtig. */
    val favorites: List<Favorite> = emptyList(),
) {
    /**
     * True, wenn irgendeine Oberflaeche die Anzeige-Weckkette braucht
     * (`PrayerAlarmScheduler.scheduleDisplayStep`).
     *
     * Frueher hiess das `anyStepsCountdown()` und fragte nur nach
     * `PRECISION_STEPS`. Der Name luegt seit dem Statusleisten-Symbol: die
     * Dauerbenachrichtigung braucht die Kette auch im EXACT-Modus. Dort
     * zeichnet zwar der Systemzaehler den Text, aber das Symbol stellt nur
     * die App weiter — ohne Weckvorgaenge bliebe es zwischen zwei Gebeten
     * auf „1h" stehen.
     *
     * Das Widget bleibt bei `PRECISION_STEPS`: es hat kein Symbol, und
     * seine EXACT-Anzeige kommt ohne die App aus.
     */
    fun needsDisplayStepAlarms(): Boolean =
        widgetNeedsStepAlarms() || notificationNeedsStepAlarms()

    /** Das Widget braucht die Kette nur fuer STEPS — es hat kein Symbol, und
     *  seine EXACT-Anzeige zeichnet der Systemzaehler ohne die App. */
    fun widgetNeedsStepAlarms(): Boolean = countdownMode == PRECISION_STEPS

    /** Die Dauerbenachrichtigung braucht sie in BEIDEN Modi: im EXACT-Modus
     *  zeichnet der Systemzaehler zwar den Text, das Statusleisten-Symbol
     *  stellt aber nur die App weiter.
     *
     *  Getrennt von [needsDisplayStepAlarms], weil der Alarm-Planer nicht nur
     *  wissen muss OB die Kette laeuft, sondern auch WELCHE Ziele Grenzen
     *  bekommen. Stuende die Bedingung dort ein zweites Mal, koennten die
     *  beiden auseinanderlaufen: die Kette liefe, das Ziel der
     *  Benachrichtigung fehlte, `boundaries` bliebe leer, der Alarm wuerde
     *  abbestellt — und das Symbol froere zwischen zwei Gebeten ein, ohne
     *  dass ein Test es merkt.
     *
     *  Seit Task 16 fragen beide Praedikate dieselbe Einstellung
     *  [countdownMode]; die Asymmetrie bleibt trotzdem, denn sie kam nie von
     *  der Einstellung, sondern vom Symbol. */
    fun notificationNeedsStepAlarms(): Boolean =
        persistentNotification && countdownMode != COUNTDOWN_OFF

    /**
     * Ob ein Online-Abrufversuch fuer amtliche Zeiten ueberhaupt etwas
     * bewirken KANN: Online-Schalter an und keine eigene Berechnung
     * erzwungen. [useOnline] ist seit [useOnlineFromPrefs] bereits auf den
     * Flavor geklemmt (Offline-Build: invariant `false`) — hier kommt nur
     * noch die zweite Bedingung dazu.
     *
     * Vorher stand `useOnline && !useCalculated` einzeln in
     * `SettingsSheet.SourceStatusSection` (zweimal: `canFetch` und die
     * Knopf-Sichtbarkeit) und musste in Aufgabe 11 fuer `noTimesNotice` in
     * `HeuteContent` und `MonatScreen` ein drittes/viertes Mal geschrieben
     * werden — genau das Muster, das im offline-Flavor schon einmal ein
     * vergessenes viertes Mal hatte (siehe [useOnlineFromPrefs]). Eine
     * Funktion statt vier Kopien derselben Konjunktion.
     */
    fun canFetchOfficial(): Boolean = useOnline && !useCalculated

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
            region = null,
            useOnline = de.gebetszeiten.official.OfficialTimesProvider.isOnline,
            useCalculated = false,
            showNafl = false,
            showKaraha = true,
            showCemaat = false,
            cemaatOffsetMinutes = 30,
            fontScale = 1f,
            highContrast = false,
            reminders = DEFAULT_REMINDERS,
            reminderLeadMinutes = 0,
            persistentNotification = false,
            reminderStyle = STYLE_SILENT,
            themeMode = THEME_SYSTEM,
            hijriOffsetDays = 0,
            widgetTransparent = false,
            countdownMode = COUNTDOWN_OFF,
            recentPlaces = emptyList(),
            favorites = emptyList(),
        )
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** DataStore kann keinen `null`-Wert ablegen, deshalb wird „unbekannte Region"
 *  als leerer String geschrieben und beim Lesen wieder zu `null`. Ein FEHLENDER
 *  Schlüssel (Bestandsnutzer, keine Migration) liefert `null` als Eingabe und
 *  damit ebenfalls `null` — beides ist derselbe Zustand.
 *
 *  Ausgelagert, weil das Repository selbst einen Android-Context braucht und
 *  ohne Robolectric nicht testbar ist: so hat der Rundlauf des Feldes doch eine
 *  Naht, an der ein reiner JVM-Test greifen kann. */
internal fun regionToPref(region: String?): String = region.orEmpty()

internal fun regionFromPref(stored: String?): String? = stored?.ifBlank { null }

/**
 * Der gespeicherte Restzeit-Modus — einschliesslich der einmaligen
 * Zusammenfuehrung der vier alten Regler zu [AppSettings.countdownMode].
 *
 * Reine Funktion aus demselben Grund wie [regionToPref]: sie fasst
 * NUTZERDATEN an, und im DataStore-Flow kaeme ohne Robolectric kein Test an
 * sie heran. Wer heute die genaue Anzeige hat, muss sie danach haben; wer nie
 * eine Restzeit wollte, darf nach dem Update keine bekommen.
 *
 * Regel: der erste nicht-`OFF`-Wert gewinnt, sonst `OFF`. Reihenfolge:
 *  1. `notification_countdown` — die Dauerbenachrichtigung ist die
 *     sichtbarste Flaeche, ihr Modus wurde am ehesten bewusst gewaehlt.
 *  2. `widget_countdown`.
 *  3. `remaining_precision`, **nur wenn `show_countdown` an war**: der Wert
 *     stand ab Werk auf `STEPS` und war damit auch bei jedem gesetzt, der die
 *     Restzeit nie eingeschaltet hat. Allein ausgewertet schaltete er jedem
 *     Bestandsnutzer die Anzeige ein — samt ihrer Weckvorgaenge.
 *
 * Ist [migrated] gesetzt, entscheidet ausschliesslich [stored]: ein
 * liegengebliebener alter Schluessel darf die Wahl nach dem Update nicht mehr
 * ueberschreiben.
 */
internal fun countdownModeFromPrefs(
    migrated: Boolean,
    stored: String?,
    notificationCountdown: String?,
    widgetCountdown: String?,
    showCountdown: Boolean?,
    remainingPrecision: String?,
): String {
    if (migrated) return stored ?: AppSettings.COUNTDOWN_OFF
    val legacyGlobal = if (showCountdown == true) remainingPrecision else null
    return listOf(notificationCountdown, widgetCountdown, legacyGlobal)
        .firstOrNull { it != null && it != AppSettings.COUNTDOWN_OFF }
        ?: AppSettings.COUNTDOWN_OFF
}

/**
 * Der gespeicherte `useOnline`-Schalter, geklemmt auf den Flavor.
 *
 * Der Offline-Flavor hat keinen Abrufmechanismus (`OfficialTimesProvider.
 * fetcher` liefert dort `null`). Die Migration weiter unten korrigiert beim
 * Umstieg vom alten auf den geflavorten Build nur ein gespeichertes `false`
 * auf den Flavor-Default — ein gespeichertes `true` (z. B. aus einem Restore
 * ueber Flavor-Grenzen hinweg, oder aus einer Version vor dem Flavor-Split)
 * blieb dort bislang unangetastet stehen. Dann zeigte der Offline-Build
 * „Jetzt aktualisieren" und Abruf-Zeilen fuer einen Abruf, den es dort gar
 * nicht gibt.
 *
 * Diese Funktion klemmt das ERGEBNIS jedes Lesevorgangs auf den Flavor,
 * unabhaengig von der Migration oben — damit bekommen `canFetch`, die
 * Knopf-Sichtbarkeit in [SettingsSheet][de.gebetszeiten.ui.LocationSettings]
 * und `noTimesNotice` (Aufgabe 11) alle dieselbe Antwort, statt dieselbe
 * Konjunktion `useOnline && OfficialTimesProvider.isOnline` an mehreren
 * Stellen zu wiederholen — und irgendwo zu vergessen.
 */
internal fun useOnlineFromPrefs(migratedUseOnline: Boolean, isOnlineFlavor: Boolean): Boolean =
    migratedUseOnline && isOnlineFlavor

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LAT = doublePreferencesKey("latitude")
        val LNG = doublePreferencesKey("longitude")
        val CITY = stringPreferencesKey("city")
        val REGION = stringPreferencesKey("region")
        val COUNTDOWN = booleanPreferencesKey("show_countdown")
        val USE_ONLINE = booleanPreferencesKey("use_online")
        val USE_ONLINE_MIGRATED = booleanPreferencesKey("use_online_migrated")
        val USE_CALCULATED = booleanPreferencesKey("use_calculated")
        val SHOW_NAFL = booleanPreferencesKey("show_nafl")
        val SHOW_KARAHA = booleanPreferencesKey("show_karaha")
        val SHOW_CEMAAT = booleanPreferencesKey("show_cemaat")
        val CEMAAT_OFFSET = intPreferencesKey("cemaat_offset_minutes")
        val FONT_SCALE = doublePreferencesKey("font_scale")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REMINDERS = stringSetPreferencesKey("reminders")
        val REMINDER_LEAD = intPreferencesKey("reminder_lead_minutes")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val REMINDER_STYLE = stringPreferencesKey("reminder_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
        val WIDGET_TRANSPARENT = booleanPreferencesKey("widget_transparent")
        val COUNTDOWN_MODE = stringPreferencesKey("countdown_mode")
        val COUNTDOWN_MIGRATED = booleanPreferencesKey("countdown_migrated")

        // Nur noch fuer die einmalige Zusammenfuehrung da (Task 16): gelesen,
        // uebersetzt, geloescht. COUNTDOWN gehoert auch dazu.
        val REMAINING_PRECISION = stringPreferencesKey("remaining_precision")
        val WIDGET_COUNTDOWN = stringPreferencesKey("widget_countdown")
        val NOTIFICATION_COUNTDOWN = stringPreferencesKey("notification_countdown")
        val RECENT_PLACES = stringPreferencesKey("recent_places")
        val FAVORITES = stringPreferencesKey("favorites")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        // Migration: aus den vier alten Reglern wird einer. Die Rechnung
        // steht als reine Funktion oben — hier gaebe es keinen Test dafuer.
        val countdownMigrated = prefs[Keys.COUNTDOWN_MIGRATED] ?: false
        val countdownMode = countdownModeFromPrefs(
            migrated = countdownMigrated,
            stored = prefs[Keys.COUNTDOWN_MODE],
            notificationCountdown = prefs[Keys.NOTIFICATION_COUNTDOWN],
            widgetCountdown = prefs[Keys.WIDGET_COUNTDOWN],
            showCountdown = prefs[Keys.COUNTDOWN],
            remainingPrecision = prefs[Keys.REMAINING_PRECISION],
        )
        if (!countdownMigrated) {
            context.dataStore.edit { migrated ->
                migrated[Keys.COUNTDOWN_MODE] = countdownMode
                migrated[Keys.COUNTDOWN_MIGRATED] = true
                // Weg damit, sonst aufersteht der alte Stand beim naechsten
                // Update, das aus Versehen wieder danach fragt.
                migrated.remove(Keys.COUNTDOWN)
                migrated.remove(Keys.REMAINING_PRECISION)
                migrated.remove(Keys.WIDGET_COUNTDOWN)
                migrated.remove(Keys.NOTIFICATION_COUNTDOWN)
            }
        }
        // Migration: vor dem Flavor-Wechsel wurde use_online im Offline-Build
        // blind mitgespeichert (Schalter war nie sichtbar, siehe
        // OfficialTimesProvider.isOnline-Gate). Bestandsnutzer mit
        // use_online=false bekommen dadurch einmalig den Flavor-Default statt
        // des versehentlich gespeicherten false — danach bleibt ein bewusst
        // ausgeschalteter Schalter ausgeschaltet.
        val useOnlineMigrated = prefs[Keys.USE_ONLINE_MIGRATED] ?: false
        val storedUseOnline = prefs[Keys.USE_ONLINE]
        val migratedUseOnline = if (!useOnlineMigrated && storedUseOnline == false) {
            AppSettings.DEFAULT.useOnline
        } else {
            storedUseOnline ?: AppSettings.DEFAULT.useOnline
        }
        if (!useOnlineMigrated) {
            context.dataStore.edit { migrated ->
                migrated[Keys.USE_ONLINE] = migratedUseOnline
                migrated[Keys.USE_ONLINE_MIGRATED] = true
            }
        }
        // Klemmung am LESEN (siehe useOnlineFromPrefs) — unabhaengig von der
        // Migration oben, damit auch ein bereits migriertes, gespeichertes
        // `true` im Offline-Flavor nicht mehr durchsickert.
        val useOnline = useOnlineFromPrefs(
            migratedUseOnline = migratedUseOnline,
            isOnlineFlavor = de.gebetszeiten.official.OfficialTimesProvider.isOnline,
        )
        AppSettings(
            latitude = prefs[Keys.LAT] ?: AppSettings.DEFAULT.latitude,
            longitude = prefs[Keys.LNG] ?: AppSettings.DEFAULT.longitude,
            city = prefs[Keys.CITY] ?: AppSettings.DEFAULT.city,
            region = regionFromPref(prefs[Keys.REGION]),
            useOnline = useOnline,
            useCalculated = prefs[Keys.USE_CALCULATED] ?: AppSettings.DEFAULT.useCalculated,
            showNafl = prefs[Keys.SHOW_NAFL] ?: AppSettings.DEFAULT.showNafl,
            showKaraha = prefs[Keys.SHOW_KARAHA] ?: AppSettings.DEFAULT.showKaraha,
            showCemaat = prefs[Keys.SHOW_CEMAAT] ?: AppSettings.DEFAULT.showCemaat,
            cemaatOffsetMinutes = prefs[Keys.CEMAAT_OFFSET] ?: AppSettings.DEFAULT.cemaatOffsetMinutes,
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
            countdownMode = countdownMode,
            recentPlaces = parseRecentPlaces(prefs[Keys.RECENT_PLACES]),
            // Kein Vorgängerschlüssel, keine Migration: fehlt der Schlüssel,
            // ist die Liste leer — der richtige Startzustand.
            favorites = parseFavorites(prefs[Keys.FAVORITES]),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAT] = value.latitude
            prefs[Keys.LNG] = value.longitude
            prefs[Keys.CITY] = value.city
            prefs[Keys.REGION] = regionToPref(value.region)
            prefs[Keys.USE_ONLINE] = value.useOnline
            prefs[Keys.USE_CALCULATED] = value.useCalculated
            prefs[Keys.SHOW_NAFL] = value.showNafl
            prefs[Keys.SHOW_KARAHA] = value.showKaraha
            prefs[Keys.SHOW_CEMAAT] = value.showCemaat
            prefs[Keys.CEMAAT_OFFSET] = value.cemaatOffsetMinutes
            prefs[Keys.FONT_SCALE] = value.fontScale.toDouble()
            prefs[Keys.HIGH_CONTRAST] = value.highContrast
            prefs[Keys.REMINDERS] = value.reminders
            prefs[Keys.REMINDER_LEAD] = value.reminderLeadMinutes
            prefs[Keys.PERSISTENT_NOTIFICATION] = value.persistentNotification
            prefs[Keys.REMINDER_STYLE] = value.reminderStyle
            prefs[Keys.THEME_MODE] = value.themeMode
            prefs[Keys.HIJRI_OFFSET] = value.hijriOffsetDays
            prefs[Keys.WIDGET_TRANSPARENT] = value.widgetTransparent
            prefs[Keys.COUNTDOWN_MODE] = value.countdownMode
            // Auch hier gesetzt, nicht nur beim Lesen: wer speichert, hat
            // gewaehlt. Ohne das Flag koennte eine Migration, die noch nicht
            // durch war, die frische Wahl gleich wieder ueberschreiben.
            prefs[Keys.COUNTDOWN_MIGRATED] = true
            prefs[Keys.RECENT_PLACES] = serializeRecentPlaces(value.recentPlaces)
            prefs[Keys.FAVORITES] = serializeFavorites(value.favorites)
        }
    }
}
