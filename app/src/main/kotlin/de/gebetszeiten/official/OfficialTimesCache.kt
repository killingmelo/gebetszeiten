package de.gebetszeiten.official

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

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

    /** Zeiten nur, wenn der Cache für (lat,lng) geladen wurde — sonst null,
     *  damit nie amtliche Zeiten eines alten Standorts angezeigt werden. */
    suspend fun get(date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val prefs = context.officialStore.data.first()
        if (!stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)) return null
        return (prefs[key] ?: return null)
            .lineSequence().mapNotNull { parseLine(it) }.toMap()[date]
    }

    /** Stempel-Match + letztes abgedecktes Datum in EINEM DataStore-Read —
     *  Eingabe für den Freshness-Check ([needsRefresh]). */
    suspend fun freshness(lat: Double, lng: Double): Pair<Boolean, LocalDate?> {
        val prefs = context.officialStore.data.first()
        val ok = stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)
        if (!ok) return false to null
        val until = prefs[key]?.lineSequence()?.mapNotNull { parseLine(it) }?.maxOfOrNull { it.first }
        return true to until
    }

    suspend fun putAll(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double) {
        if (schedule.isEmpty()) return
        val text = schedule.entries
            .sortedBy { it.key }
            .joinToString("\n") { (d, t) ->
                "$d ${t.fajr} ${t.sunrise} ${t.dhuhr} ${t.asr} ${t.maghrib} ${t.isha}"
            }
        context.officialStore.edit {
            it[key] = text
            it[stampLat] = lat
            it[stampLng] = lng
        }
    }

    private fun parseLine(line: String): Pair<LocalDate, SixTimes>? {
        val p = line.trim().split(" ")
        if (p.size != 7) return null
        return try {
            LocalDate.parse(p[0]) to SixTimes(
                fajr = LocalTime.parse(p[1]),
                sunrise = LocalTime.parse(p[2]),
                dhuhr = LocalTime.parse(p[3]),
                asr = LocalTime.parse(p[4]),
                maghrib = LocalTime.parse(p[5]),
                isha = LocalTime.parse(p[6]),
            )
        } catch (e: Exception) {
            null
        }
    }
}
