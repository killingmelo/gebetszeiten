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

/**
 * Der gespeicherte Notausgang-Schalter der Uhr — einschliesslich der
 * einmaligen Migration aus dem alten `useCalculated` ("immer rechnen").
 * Dasselbe Muster wie `calculationFillsGapsFromPrefs` in
 * `SettingsRepository.kt` (Telefon), reine Funktion aus demselben Grund:
 * sie fasst NUTZERDATEN an, und ohne Robolectric kaeme im DataStore-Flow
 * kein Test an sie heran.
 *
 * Der alte Schalter bedeutete "immer rechnen, amtliche Zeiten ignorieren",
 * der neue bedeutet "rechnen, NUR wo amtliche Zeiten fehlen". Wer ihn an
 * hatte, WILL den Notausgang also weiterhin — und behaelt ihn 1:1
 * (`legacyUseCalculated == true` -> `true`). Wer ihn aus hatte oder ihn nie
 * gesehen hat, bekommt `false`, der Werksstand.
 *
 * Ist [migrated] gesetzt, entscheidet ausschliesslich [stored] — ein
 * liegengebliebener alter Schluessel darf die Wahl nach dem Update nicht
 * mehr ueberschreiben.
 */
internal fun calculationFillsGapsFromPrefs(
    migrated: Boolean,
    stored: Boolean?,
    legacyUseCalculated: Boolean?,
): Boolean {
    if (migrated) return stored ?: false
    return legacyUseCalculated ?: false
}

/** Location chosen directly on the watch (city picker). Falls back to a
 *  default until the user picks a city. */
object WearSettings {

    private val LAT = doublePreferencesKey("lat")
    private val LNG = doublePreferencesKey("lng")
    private val CITY = stringPreferencesKey("city")
    private val SHOW_REMAINING = booleanPreferencesKey("show_remaining")
    private val VIBRATE = booleanPreferencesKey("vibrate")
    private val CALCULATION_FILLS_GAPS = booleanPreferencesKey("calculation_fills_gaps")
    private val CALCULATION_FILLS_GAPS_MIGRATED = booleanPreferencesKey("calculation_fills_gaps_migrated")

    // Nur noch fuer die einmalige Migration da (Task 16): gelesen,
    // uebersetzt, geloescht.
    private val USE_CALCULATED_LEGACY = booleanPreferencesKey("use_calculated")
    private val SHOW_CEMAAT = booleanPreferencesKey("show_cemaat")
    private val DEFAULT = GeoLocation(49.4521, 11.0767) // Nürnberg
    private const val DEFAULT_CITY = "Nürnberg"

    /** Alle Anzeige-relevanten Werte in EINEM DataStore-Read (statt 4–7
     *  einzelnen) — der Hauptscreen liest so nur einmal pro Aktualisierung. */
    data class Snapshot(
        val location: GeoLocation,
        val city: String,
        val showRemaining: Boolean,
        val vibrate: Boolean,
        val calculationFillsGaps: Boolean,
        val showCemaat: Boolean,
    )

    suspend fun snapshot(context: Context): Snapshot {
        val prefs = context.locationStore.data.first()
        val lat = prefs[LAT]
        val lng = prefs[LNG]
        return Snapshot(
            location = if (lat != null && lng != null) GeoLocation(lat, lng) else DEFAULT,
            city = prefs[CITY] ?: DEFAULT_CITY,
            showRemaining = prefs[SHOW_REMAINING] ?: false,
            vibrate = prefs[VIBRATE] ?: false,
            calculationFillsGaps = resolveCalculationFillsGaps(context, prefs),
            showCemaat = prefs[SHOW_CEMAAT] ?: false,
        )
    }

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

    /** Notausgang: die lokale Berechnung springt nur ein, wenn fuer einen Tag
     *  keine amtlichen Zeiten vorliegen (siehe [calculationFillsGapsFromPrefs]
     *  fuer die Migration aus dem alten `useCalculated`). */
    suspend fun calculationFillsGaps(context: Context): Boolean {
        val outer = context.locationStore.data.first()
        return resolveCalculationFillsGaps(context, outer)
    }

    suspend fun saveCalculationFillsGaps(context: Context, value: Boolean) {
        context.locationStore.edit {
            it[CALCULATION_FILLS_GAPS] = value
            // Auch hier gesetzt, nicht nur beim Lesen: wer speichert, hat
            // gewaehlt. Ohne das Flag koennte eine Migration, die noch nicht
            // durch war, die frische Wahl gleich wieder ueberschreiben.
            it[CALCULATION_FILLS_GAPS_MIGRATED] = true
        }
    }

    /**
     * Migrations-bewusster Lesevorgang, dasselbe Muster wie
     * `WearOfficialCache.cacheText`/`migrateWithin`: [outer] ist ein bereits
     * vorhandener Schnappschuss (aus [snapshot] oder [calculationFillsGaps])
     * — ist er schon migriert, reicht ein reiner Lesevorgang ohne
     * Schreibtransaktion (der heisse Pfad, jeder Tile-/Complication-Tick).
     * Nur wenn noch NICHT migriert wurde, oeffnet sich EINE
     * `edit { }`-Transaktion, die Lesen (samt erneuter Migrationspruefung)
     * und Schreiben zusammenhaelt — ein zwischenzeitlich abgeschlossener
     * zweiter Aufruf sieht dort bereits `migrated = true` und schreibt
     * nichts doppelt.
     */
    private suspend fun resolveCalculationFillsGaps(context: Context, outer: Preferences): Boolean {
        val outerMigrated = outer[CALCULATION_FILLS_GAPS_MIGRATED] ?: false
        if (outerMigrated) {
            return calculationFillsGapsFromPrefs(
                migrated = true,
                stored = outer[CALCULATION_FILLS_GAPS],
                legacyUseCalculated = null,
            )
        }
        var result = false
        context.locationStore.edit { prefs ->
            val migrated = prefs[CALCULATION_FILLS_GAPS_MIGRATED] ?: false
            result = calculationFillsGapsFromPrefs(
                migrated = migrated,
                stored = prefs[CALCULATION_FILLS_GAPS],
                legacyUseCalculated = prefs[USE_CALCULATED_LEGACY],
            )
            if (!migrated) {
                prefs[CALCULATION_FILLS_GAPS] = result
                prefs[CALCULATION_FILLS_GAPS_MIGRATED] = true
                prefs.remove(USE_CALCULATED_LEGACY)
            }
        }
        return result
    }

    /** Abgeleitete Sabah-Cemaat-Zeile (Sonnenaufgang − 30 Min) anzeigen. */
    suspend fun showCemaat(context: Context): Boolean =
        context.locationStore.data.first()[SHOW_CEMAAT] ?: false

    suspend fun saveShowCemaat(context: Context, value: Boolean) {
        context.locationStore.edit { it[SHOW_CEMAAT] = value }
    }

    suspend fun save(context: Context, city: String, latitude: Double, longitude: Double) {
        context.locationStore.edit {
            it[CITY] = city
            it[LAT] = latitude
            it[LNG] = longitude
        }
    }
}
