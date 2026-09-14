package de.gebetszeiten.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.officialtimes.CacheEntry
import de.gebetszeiten.core.prayertimes.officialtimes.CacheHeader
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.RawEntry
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.officialSyncStore: DataStore<Preferences> by preferencesDataStore(name = "wear_official_cache")

/**
 * Alten Ein-Ort-Stand (Vorgaenger von [WearOfficialCache]) in EINEN fertig
 * serialisierten [CacheStore]-Eintrag ueberfuehren, oder `null`, wenn nichts
 * Brauchbares da ist (kein Ortsstempel oder gar kein Altstand).
 *
 * Der Rumpf wird WOERTLICH uebernommen, nicht ueber [ScheduleText] neu
 * geparst und serialisiert — ein Format-Unterschied zwischen dem, was frueher
 * tatsaechlich gespeichert wurde, und dem, was [ScheduleText] heute liest,
 * duerfte beim Update nie eine Tageszeile verschlucken. Nur die Kopfzeile
 * entsteht neu, und zwar ueber [CacheStore] selbst (`serializeRaw`), damit ihr
 * Format nie ein zweites Mal im Code steht. `firstDate`/`lastDate` werden aus
 * dem Rumpf abgeleitet, wo er sich lesen laesst — gelingt das nicht, bleiben
 * sie `null`, der Rumpf selbst geht trotzdem nicht verloren.
 */
fun migrateLegacySchedule(altText: String?, lat: Double?, lng: Double?): String? {
    if (altText.isNullOrEmpty() || lat == null || lng == null) return null

    val dates = ScheduleText.parse(altText).keys
    val header = CacheHeader(
        latitude = lat,
        longitude = lng,
        locationId = null,
        firstDate = dates.minOrNull(),
        lastDate = dates.maxOrNull(),
        updatedEpochMs = System.currentTimeMillis(),
        lastAttemptEpochMs = null,
        lastError = null,
    )
    return CacheStore.serializeRaw(listOf(RawEntry(header, altText.trim())))
}

/**
 * Vom Handy gesyncte amtliche Zeiten — MEHRERE Orte (siehe [CacheStore]),
 * damit ein Ortswechsel auf der Uhr die Zeiten der vorigen Orte nicht
 * wegwirft. `synced_*` bleibt daneben eigenstaendig: zuletzt UEBERNOMMENER
 * Handy-Ort, fuer die Override-Semantik (nur ein NEUER Handy-Ort
 * ueberschreibt die Uhr-Wahl) — das hat mit der Zeiten-Abdeckung nichts zu
 * tun und wird darum nicht ueber [CacheStore] gefuehrt.
 */
object WearOfficialCache {

    private val CACHE_TEXT = stringPreferencesKey("cache_text")
    private val CACHE_MIGRATED = booleanPreferencesKey("cache_migrated")
    private val SYNCED_LAT = doublePreferencesKey("synced_lat")
    private val SYNCED_LNG = doublePreferencesKey("synced_lng")

    // Schluessel des alten Ein-Ort-Caches. Werden nur noch fuer die einmalige
    // Migration gelesen (siehe [cacheText]) und danach entfernt.
    private val LEGACY_SCHEDULE = stringPreferencesKey("schedule")
    private val LEGACY_STAMP_LAT = doublePreferencesKey("stamp_lat")
    private val LEGACY_STAMP_LNG = doublePreferencesKey("stamp_lng")

    /** Zeiten nur, wenn es fuer (lat,lng) einen Eintrag gibt — der Abgleich
     *  laeuft ueber [CacheStore.select] (`stampMatches`, ~1 km Toleranz), wie
     *  ueberall sonst in diesem Cache. Gleiches Verhalten wie vor dem Umbau
     *  auf mehrere Orte: Zeiten nur, wenn der gespeicherte Ort zum gefragten
     *  passt. Heisser Pfad (jeder Tile-/Complication-Tick ueber
     *  `WearPrayer.daily`): [ScheduleText.parseDay] liest nur den gefragten
     *  Tag statt den ganzen Jahresplan, wie im Phone-Pendant
     *  `OfficialTimesCache.get`. */
    suspend fun get(context: Context, date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val entries = CacheStore.split(cacheText(context))
        val entry = CacheStore.select(entries, lat, lng) ?: return null
        return ScheduleText.parseDay(entry.body, date)
    }

    suspend fun syncedLocation(context: Context): Pair<Double, Double>? {
        val prefs = context.officialSyncStore.data.first()
        val lat = prefs[SYNCED_LAT] ?: return null
        val lng = prefs[SYNCED_LNG] ?: return null
        return lat to lng
    }

    /** Kompatibilitaetspfad fuer [WearSyncApplier], das (noch) einen fertig
     *  serialisierten Rumpftext fuer EINEN Ort liefert. Aufgabe 6 stellt den
     *  Sync auf [put] mit einem bereits geparsten Zeitplan um. */
    suspend fun store(context: Context, scheduleText: String, lat: Double, lng: Double, adoptedLocation: Boolean) {
        writeEntry(context, ScheduleText.parse(scheduleText), lat, lng, locationId = null)
        if (adoptedLocation) {
            context.officialSyncStore.edit {
                it[SYNCED_LAT] = lat
                it[SYNCED_LNG] = lng
            }
        }
    }

    /** Einen bereits geparsten Zeitplan fuer einen Ort ablegen (ueber
     *  [CacheStore.put], also mit derselben Verdraengung wie der Phone-Cache).
     *  Noch ohne Aufrufer — Aufgabe 6 verdrahtet die Uhr-Ortswahl darauf. */
    suspend fun put(context: Context, schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, locationId: Int?) {
        writeEntry(context, schedule, lat, lng, locationId)
    }

    private suspend fun writeEntry(
        context: Context,
        schedule: Map<LocalDate, SixTimes>,
        lat: Double,
        lng: Double,
        locationId: Int?,
    ) {
        val entries = CacheStore.split(cacheText(context))
        val header = CacheHeader(
            latitude = lat,
            longitude = lng,
            locationId = locationId,
            firstDate = null,
            lastDate = null,
            updatedEpochMs = System.currentTimeMillis(),
            lastAttemptEpochMs = null,
            lastError = null,
        )
        // Die Uhr fuehrt keine eigene Favoritenliste — pinnedCoords bleibt
        // leer, [CacheStore.put] begrenzt dann alle Orte gleich (Standard
        // maxUnpinned = 5).
        val updated = CacheStore.put(entries, CacheEntry(header, schedule), pinnedCoords = emptyList())
        context.officialSyncStore.edit { it[CACHE_TEXT] = CacheStore.serializeRaw(updated) }
    }

    /**
     * Der aktuelle Mehrort-Text. Holt beim ALLERERSTEN Lesen einmalig den
     * alten Ein-Ort-Stand herueber (Merker [CACHE_MIGRATED], Muster:
     * `USE_ONLINE_MIGRATED` in `SettingsRepository`) und persistiert ihn
     * gleich mit — nur, wenn seitdem noch NICHTS Echtes im neuen Format
     * abgelegt wurde: [store]/[put] koennen vor dem ersten [get] gelaufen
     * sein (ein frischer Sync direkt nach dem Update), und der duerfte von
     * einem veralteten Altstand nicht ueberschrieben werden. Der Merker wird
     * in jedem Fall gesetzt — die alten Schluessel sind danach in KEINEM Fall
     * mehr von Belang.
     */
    private suspend fun cacheText(context: Context): String? {
        val prefs = context.officialSyncStore.data.first()
        if (prefs[CACHE_MIGRATED] == true) return prefs[CACHE_TEXT]

        val migrated = migrateLegacySchedule(prefs[LEGACY_SCHEDULE], prefs[LEGACY_STAMP_LAT], prefs[LEGACY_STAMP_LNG])
        var result = prefs[CACHE_TEXT]
        context.officialSyncStore.edit { edit ->
            if (result == null && migrated != null) {
                edit[CACHE_TEXT] = migrated
                result = migrated
            }
            edit[CACHE_MIGRATED] = true
            edit.remove(LEGACY_SCHEDULE)
            edit.remove(LEGACY_STAMP_LAT)
            edit.remove(LEGACY_STAMP_LNG)
        }
        return result
    }
}
