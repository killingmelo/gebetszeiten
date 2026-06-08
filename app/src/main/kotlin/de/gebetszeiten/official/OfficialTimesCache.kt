package de.gebetszeiten.official

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    suspend fun get(date: LocalDate): SixTimes? = all()[date]

    suspend fun all(): Map<LocalDate, SixTimes> {
        val raw = context.officialStore.data.first()[key] ?: return emptyMap()
        return raw.lineSequence().mapNotNull { parseLine(it) }.toMap()
    }

    suspend fun putAll(schedule: Map<LocalDate, SixTimes>) {
        if (schedule.isEmpty()) return
        val text = schedule.entries
            .sortedBy { it.key }
            .joinToString("\n") { (d, t) ->
                "$d ${t.fajr} ${t.sunrise} ${t.dhuhr} ${t.asr} ${t.maghrib} ${t.isha}"
            }
        context.officialStore.edit { it[key] = text }
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
