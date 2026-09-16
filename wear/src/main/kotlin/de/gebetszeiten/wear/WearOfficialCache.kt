package de.gebetszeiten.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.officialtimes.CacheEntry
import de.gebetszeiten.core.prayertimes.officialtimes.CacheHeader
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.DueLocation
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

    /**
     * Wendet einen vom Handy gesyncten [payload] an — AUSSER ein frischerer
     * eigener Stand am SYNC-ORT unterlaeuft ihn (siehe
     * [SyncDecision.shouldApply]). Lesen (welcher eigene Stand liegt am
     * Sync-Ort schon da?), Entscheiden und Schreiben laufen in EINER
     * Transaktion — sonst koennte ein zwischen Lesen und Schreiben
     * abgeschlossener eigener Abruf ([put]/`refreshWearOfficial`) hier blind
     * ueberschrieben werden. Dasselbe Muster wie [migrateWithin] (Task 5,
     * Fix-Runde 1, Important 1) — dort schon einmal derselbe Fehler: ein
     * AUSSERHALB der Transaktion gelesener Schnappschuss kann gegen einen
     * INNERHALB frisch geschriebenen Stand verlieren.
     *
     * Rueckgabe: wurde der Sync tatsaechlich angewendet? `false` heisst: der
     * Payload liess sich nicht parsen, ODER ein frischerer eigener Stand hat
     * ihn ausgestochen — in BEIDEN Faellen wurde NICHTS geschrieben, auch
     * [adoptedLocation] nicht. Der Aufrufer ([WearSyncApplier]) darf dann
     * Vibrations-Kette/Tile/Complication unangetastet lassen.
     */
    suspend fun applySync(context: Context, payload: SyncDecision.Payload, adoptedLocation: Boolean): Boolean {
        val schedule = SyncDecision.parse(payload) ?: return false
        var applied = false
        context.officialSyncStore.edit { prefs ->
            val entries = CacheStore.split(migrateWithin(prefs))
            val ownUpdated = SyncDecision.ownUpdatedEpochMs(entries, payload.lat, payload.lng)
            if (!SyncDecision.shouldApply(payload, ownUpdated)) return@edit
            val header = CacheHeader(
                latitude = payload.lat,
                longitude = payload.lng,
                locationId = null,
                firstDate = null,
                lastDate = null,
                updatedEpochMs = System.currentTimeMillis(),
                lastAttemptEpochMs = null,
                lastError = null,
            )
            val updated = CacheStore.put(entries, CacheEntry(header, schedule), pinnedCoords = emptyList())
            prefs[CACHE_TEXT] = CacheStore.serializeRaw(updated)
            if (adoptedLocation) {
                prefs[SYNCED_LAT] = payload.lat
                prefs[SYNCED_LNG] = payload.lng
            }
            applied = true
        }
        return applied
    }

    /** Einen bereits geparsten Zeitplan fuer einen Ort ablegen (ueber
     *  [CacheStore.put], also mit derselben Verdraengung wie der Phone-Cache).
     *  Aufrufer: [refreshWearOfficial] (Aufgabe 6). */
    suspend fun put(context: Context, schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, locationId: Int?) {
        writeEntry(context, schedule, lat, lng, locationId)
    }

    /** Diyanet-ID des letzten erfolgreichen Abrufs an diesem Ort — Pendant zu
     *  `OfficialTimesCache.cachedLocationId` am Telefon. Wird der
     *  Netzschicht als `preferredLocationId` mitgegeben, damit ein Folge-
     *  Refresh nicht wieder ueber die Namenssuche gehen muss. */
    suspend fun cachedLocationId(context: Context, lat: Double, lng: Double): Int? =
        CacheStore.select(CacheStore.split(cacheText(context)), lat, lng)?.header?.locationId

    /** Der EINE Uhr-Ort als [DueLocation] (oder eine leere Liste, wenn er
     *  gar nicht bekannt ist) — Pendant zu `OfficialTimesCache.dueOrder`,
     *  aber OHNE Favoritenliste (`pinnedCoords = emptyList()`): die Uhr
     *  fuehrt keine eigene Favoritenrotation, es gibt nur diesen einen Ort.
     *  Trotzdem ueber [CacheStore.dueOrder] statt handgestrickt, damit
     *  [chooseTarget] dieselbe, bereits getestete Logik anwendet wie am
     *  Telefon. */
    suspend fun dueOrder(context: Context, activeCoords: Pair<Double, Double>, today: LocalDate): List<DueLocation> =
        CacheStore.dueOrder(
            entries = CacheStore.split(cacheText(context)),
            pinnedCoords = emptyList(),
            activeCoords = activeCoords,
            today = today,
        )

    /** Abrufversuch protokollieren — Pendant zu
     *  `OfficialTimesCache.recordAttempt`, ohne Favoriten. Lesen (samt
     *  Migrationsentscheidung) und Schreiben in EINER Transaktion, wie
     *  [writeEntry] (Begruendung dort: Task 5, Fix-Runde 1, Important 1). */
    suspend fun recordAttempt(context: Context, error: String?, nowEpochMs: Long, lat: Double, lng: Double) {
        context.officialSyncStore.edit { prefs ->
            val entries = CacheStore.split(migrateWithin(prefs))
            val index = CacheStore.indexOf(entries, lat, lng)
            val updated = if (index >= 0) {
                entries.mapIndexed { i, entry ->
                    if (i == index) {
                        entry.copy(header = entry.header.copy(lastAttemptEpochMs = nowEpochMs, lastError = error))
                    } else {
                        entry
                    }
                }
            } else {
                CacheStore.put(
                    entries,
                    CacheEntry(
                        header = CacheHeader(
                            latitude = lat,
                            longitude = lng,
                            locationId = null,
                            firstDate = null,
                            lastDate = null,
                            updatedEpochMs = nowEpochMs,
                            lastAttemptEpochMs = nowEpochMs,
                            lastError = error,
                        ),
                        schedule = emptyMap(),
                    ),
                    pinnedCoords = emptyList(),
                )
            }
            prefs[CACHE_TEXT] = CacheStore.serializeRaw(updated)
        }
    }

    /** Liest den aktuellen Text, mergt den neuen Eintrag ein und schreibt —
     *  ALLES in EINER Transaktion. Lesen (samt der Migrationsentscheidung,
     *  siehe [migrateWithin]) und Schreiben duerfen hier nicht auseinander-
     *  fallen: sonst koennte ein Schnappschuss von VOR der Transaktion einen
     *  zwischenzeitlich (durch einen parallelen `get`/`store`/`put`) frisch
     *  geschriebenen Stand bedingungslos ueberschreiben (Task 5, Fix-Runde 1,
     *  Important 1). */
    private suspend fun writeEntry(
        context: Context,
        schedule: Map<LocalDate, SixTimes>,
        lat: Double,
        lng: Double,
        locationId: Int?,
    ) {
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
        context.officialSyncStore.edit { prefs ->
            val entries = CacheStore.split(migrateWithin(prefs))
            // Die Uhr fuehrt keine eigene Favoritenliste — pinnedCoords
            // bleibt leer, [CacheStore.put] begrenzt dann alle Orte gleich
            // (Standard maxUnpinned = 5).
            val updated = CacheStore.put(entries, CacheEntry(header, schedule), pinnedCoords = emptyList())
            prefs[CACHE_TEXT] = CacheStore.serializeRaw(updated)
        }
    }

    /**
     * Der aktuelle Mehrort-Text. Regelfall (schon migriert) ist ein reiner
     * Lesevorgang OHNE Transaktion — [get] ist der heisse Pfad (jeder
     * Tile-/Complication-Tick), eine `edit{}`-Transaktion je Aufruf waere
     * unnoetiger Schreibaufwand. Nur wenn die Migration (Merker
     * [CACHE_MIGRATED]) noch nicht gelaufen ist, oeffnet sich EINE
     * Transaktion, die [migrateWithin] Lesen und Schreiben zusammenhalten
     * laesst — der aeussere Schnappschuss hier oben entscheidet nur, ob
     * diese Transaktion ueberhaupt noetig ist, er wird innerhalb nie
     * verwendet.
     */
    private suspend fun cacheText(context: Context): String? {
        val prefs = context.officialSyncStore.data.first()
        if (prefs[CACHE_MIGRATED] == true) return prefs[CACHE_TEXT]

        var result: String? = null
        context.officialSyncStore.edit { result = migrateWithin(it) }
        return result
    }

    /**
     * Fuehrt die einmalige Migration des alten Ein-Ort-Stands durch, FALLS
     * noch nicht geschehen — Muster: `USE_ONLINE_MIGRATED` in
     * `SettingsRepository`. Liefert den (ggf. migrierten) aktuellen Text.
     *
     * Bewusst eine reine Funktion auf [MutablePreferences], die der Aufrufer
     * INNERHALB einer laufenden `officialSyncStore.edit { }`-Transaktion
     * aufruft (siehe [cacheText], [writeEntry]): Entscheidung ("migriert?")
     * und Schreibvorgang duerfen nie an zwei verschiedene Zeitpunkte
     * auseinanderfallen, sonst gewinnt ein aeusserer, veralteter
     * Schnappschuss gegen einen zwischenzeitlich frisch eingetroffenen Sync
     * (Task 5, Fix-Runde 1, Important 1). Wird sie ein zweites Mal
     * aufgerufen (weil ZWEI Aufrufer beim Merker gleichzeitig `false` sahen),
     * ist der zweite Durchlauf ein No-op: `prefs[CACHE_MIGRATED]` ist dann
     * innerhalb SEINER Transaktion schon `true`, DataStore serialisiert die
     * beiden `edit{}`-Aufrufe.
     *
     * Schreibt den migrierten Stand nur, wenn seitdem noch NICHTS Echtes im
     * neuen Format abgelegt wurde — sonst wuerde ein frischer Sync von einem
     * veralteten Altstand ueberschrieben.
     */
    private fun migrateWithin(prefs: MutablePreferences): String? {
        if (prefs[CACHE_MIGRATED] == true) return prefs[CACHE_TEXT]

        val migrated = migrateLegacySchedule(prefs[LEGACY_SCHEDULE], prefs[LEGACY_STAMP_LAT], prefs[LEGACY_STAMP_LNG])
        val existing = prefs[CACHE_TEXT]
        val result = existing ?: migrated
        if (existing == null && migrated != null) prefs[CACHE_TEXT] = migrated
        prefs[CACHE_MIGRATED] = true
        prefs.remove(LEGACY_SCHEDULE)
        prefs.remove(LEGACY_STAMP_LAT)
        prefs.remove(LEGACY_STAMP_LNG)
        return result
    }
}
