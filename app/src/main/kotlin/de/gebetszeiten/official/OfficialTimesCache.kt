package de.gebetszeiten.official

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.officialStore: DataStore<Preferences> by preferencesDataStore(name = "official_times")

/**
 * Locally cached official Diyanet times. Populated only by the online flavor;
 * in the offline flavor it simply stays empty, so all lookups fall through to
 * the offline calculation. Stored compactly as one line per day.
 */
class OfficialTimesCache(private val context: Context) {

    private val key = stringPreferencesKey("schedule")
    private val stampLat = doublePreferencesKey("stamp_lat")
    private val stampLng = doublePreferencesKey("stamp_lng")
    private val stampId = intPreferencesKey("stamp_diyanet_id")

    /** Zeiten nur, wenn der Cache für (lat,lng) geladen wurde — sonst null,
     *  damit nie amtliche Zeiten eines alten Standorts angezeigt werden. */
    suspend fun get(date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val prefs = context.officialStore.data.first()
        if (!stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)) return null
        return ScheduleText.parse(prefs[key] ?: return null)[date]
    }

    /** Kompletter gecachter Zeitplan — leer bei Stempel-Mismatch oder ohne
     *  Daten. Quelle fuer den Wear-Sync, wenn der Cache noch frisch ist und
     *  daher kein Netz-Refresh laeuft. */
    suspend fun snapshot(lat: Double, lng: Double): Map<LocalDate, SixTimes> {
        val prefs = context.officialStore.data.first()
        if (!stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)) return emptyMap()
        return ScheduleText.parse(prefs[key] ?: return emptyMap())
    }

    /** Stempel-Match + letztes abgedecktes Datum in EINEM DataStore-Read —
     *  Eingabe für den Freshness-Check ([needsRefresh]). */
    suspend fun freshness(lat: Double, lng: Double): Pair<Boolean, LocalDate?> {
        val prefs = context.officialStore.data.first()
        val ok = stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)
        if (!ok) return false to null
        val until = prefs[key]?.let { ScheduleText.parse(it).keys.maxOrNull() }
        return true to until
    }

    /** Diyanet-ID des letzten erfolgreichen Abrufs — nur bei Stempel-Match,
     *  damit nie die ID eines anderen Standorts wiederverwendet wird. */
    suspend fun cachedLocationId(lat: Double, lng: Double): Int? {
        val prefs = context.officialStore.data.first()
        if (!stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)) return null
        return prefs[stampId]
    }

    suspend fun putAll(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, locationId: Int? = null) {
        if (schedule.isEmpty()) return
        val text = ScheduleText.serialize(schedule)
        context.officialStore.edit {
            it[key] = text
            it[stampLat] = lat
            it[stampLng] = lng
            if (locationId != null) it[stampId] = locationId else it.remove(stampId)
        }
    }
}
