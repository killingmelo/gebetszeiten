package de.gebetszeiten.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.officialSyncStore: DataStore<Preferences> by preferencesDataStore(name = "wear_official_cache")

/**
 * Vom Handy gesyncte amtliche Zeiten. Stempel (stamp_*) = Ort, für den
 * die Zeiten gelten; synced_* = zuletzt ÜBERNOMMENER Handy-Ort (für die
 * Override-Semantik: nur ein NEUER Handy-Ort überschreibt die Uhr-Wahl).
 */
object WearOfficialCache {

    private val SCHEDULE = stringPreferencesKey("schedule")
    private val STAMP_LAT = doublePreferencesKey("stamp_lat")
    private val STAMP_LNG = doublePreferencesKey("stamp_lng")
    private val SYNCED_LAT = doublePreferencesKey("synced_lat")
    private val SYNCED_LNG = doublePreferencesKey("synced_lng")

    /** Zeiten nur, wenn der Sync-Stempel zur aktuellen Uhr-Ortswahl passt —
     *  nach einem Uhr-Override greift automatisch wieder Bundle/Berechnung. */
    suspend fun get(context: Context, date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val prefs = context.officialSyncStore.data.first()
        if (!stampMatches(prefs[STAMP_LAT], prefs[STAMP_LNG], lat, lng)) return null
        return ScheduleText.parse(prefs[SCHEDULE] ?: return null)[date]
    }

    suspend fun syncedLocation(context: Context): Pair<Double, Double>? {
        val prefs = context.officialSyncStore.data.first()
        val lat = prefs[SYNCED_LAT] ?: return null
        val lng = prefs[SYNCED_LNG] ?: return null
        return lat to lng
    }

    suspend fun store(context: Context, scheduleText: String, lat: Double, lng: Double, adoptedLocation: Boolean) {
        context.officialSyncStore.edit {
            it[SCHEDULE] = scheduleText
            it[STAMP_LAT] = lat
            it[STAMP_LNG] = lng
            if (adoptedLocation) {
                it[SYNCED_LAT] = lat
                it[SYNCED_LNG] = lng
            }
        }
    }
}
