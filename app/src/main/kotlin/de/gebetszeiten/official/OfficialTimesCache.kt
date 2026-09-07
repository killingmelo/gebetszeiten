package de.gebetszeiten.official

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.officialtimes.CacheEntry
import de.gebetszeiten.core.prayertimes.officialtimes.CacheHeader
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.DueLocation
import de.gebetszeiten.core.prayertimes.officialtimes.RawEntry
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.officialStore: DataStore<Preferences> by preferencesDataStore(name = "official_times")

/**
 * Locally cached official Diyanet times. Populated only by the online flavor;
 * in the offline flavor it simply stays empty, so all lookups fall through to
 * the offline calculation.
 *
 * Gespeichert werden MEHRERE Orte in einem einzigen String-Schluessel
 * (`entries`, Format und Verdraengung siehe
 * [CacheStore]) — wer von Nuernberg nach Regensburg wechselt und zurueck,
 * hat die amtlichen Zeiten fuer Nuernberg sofort wieder da, statt auf einen
 * neuen Abruf zu warten. Die Ruempfe bleiben dabei ungeparste Strings; nur
 * der tatsaechlich gefragte Tag wird gelesen ([ScheduleText.parseDay]).
 */
class OfficialTimesCache(private val context: Context) {

    private val entriesKey = stringPreferencesKey("entries")

    // Schluessel des alten Einzel-Cache. Werden nur noch EINMAL gelesen (zum
    // Migrieren) und beim naechsten Schreibvorgang entfernt.
    private val legacySchedule = stringPreferencesKey("schedule")
    private val legacyStampLat = doublePreferencesKey("stamp_lat")
    private val legacyStampLng = doublePreferencesKey("stamp_lng")
    private val legacyStampId = intPreferencesKey("stamp_diyanet_id")
    private val legacyLastAttempt = longPreferencesKey("last_attempt")
    private val legacyLastError = stringPreferencesKey("last_error")
    private val legacyAttemptLat = doublePreferencesKey("attempt_lat")
    private val legacyAttemptLng = doublePreferencesKey("attempt_lng")

    /** Zeiten nur, wenn es fuer (lat,lng) einen Eintrag gibt — sonst null,
     *  damit nie amtliche Zeiten eines anderen Standorts angezeigt werden.
     *  Heisser Pfad (Minutentakt, Widget, Alarm): [ScheduleText.parseDay]
     *  liest genau den gefragten Tag statt den ganzen Jahresplan. */
    suspend fun get(date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val entry = entryFor(lat, lng) ?: return null
        return ScheduleText.parseDay(entry.body, date)
    }

    /** Kompletter gecachter Zeitplan — leer ohne Eintrag fuer diesen Ort.
     *  Quelle fuer den Wear-Sync, wenn der Cache noch frisch ist und daher
     *  kein Netz-Refresh laeuft. */
    suspend fun snapshot(lat: Double, lng: Double): Map<LocalDate, SixTimes> {
        val entry = entryFor(lat, lng) ?: return emptyMap()
        return ScheduleText.parse(entry.body)
    }

    /** Diyanet-ID des letzten erfolgreichen Abrufs an diesem Ort — steht im
     *  Kopf, der Rumpf wird dafuer nicht angefasst. */
    suspend fun cachedLocationId(lat: Double, lng: Double): Int? =
        entryFor(lat, lng)?.header?.locationId

    /** Alles, was die Statuszeile braucht — in EINEM DataStore-Read und
     *  ohne eine einzige Zeit zu parsen: Zeitplan und Versuchsprotokoll
     *  liegen mit einem Eintrag je Ort ohnehin zusammen im selben Kopf.
     *
     *  [OfficialStatus.stampOk] heisst hier: es gibt einen Eintrag fuer
     *  diesen Ort MIT Zeitplan. Ein Eintrag, der nur einen Fehlversuch
     *  protokolliert (leerer Rumpf, also `lastDate == null`), zaehlt nicht
     *  als Treffer — sonst hielte [needsRefresh] einen Ort fuer versorgt,
     *  fuer den es gar keine Zeiten gibt. */
    suspend fun status(lat: Double, lng: Double): OfficialStatus {
        val header = entryFor(lat, lng)?.header
        return OfficialStatus(
            locationId = header?.locationId,
            coveredUntil = header?.lastDate,
            lastAttemptEpochMs = header?.lastAttemptEpochMs,
            lastError = header?.lastError,
            stampOk = header?.lastDate != null,
        )
    }

    /** Erfolgreichen Abruf ablegen. Ein vorhandener Eintrag fuer denselben
     *  Ort wird ersetzt, sein Versuchsprotokoll aber uebernommen — frueher
     *  fasste `putAll` die Versuchs-Schluessel ebenfalls nicht an.
     *
     *  [pinnedCoords] wird UEBERGEBEN, nicht hier gelesen: der Cache kennt
     *  nur einen `Context` und soll keine `SettingsRepository` bekommen —
     *  sonst haengen Cache und Einstellungen aneinander und die reine,
     *  testbare Schicht in [CacheStore] bringt nichts mehr. Autoritativ ist
     *  die Favoritenliste in den Einstellungen; der Aufrufer
     *  (`PrayerProvider.refreshOfficial`) hat sie ohnehin zur Hand. */
    suspend fun putAll(
        schedule: Map<LocalDate, SixTimes>,
        lat: Double,
        lng: Double,
        pinnedCoords: List<Pair<Double, Double>>,
        locationId: Int? = null,
    ) {
        if (schedule.isEmpty()) return
        val now = System.currentTimeMillis()
        update { entries ->
            val existing = CacheStore.select(entries, lat, lng)
            val added = CacheEntry(
                header = newHeader(
                    lat = lat,
                    lng = lng,
                    locationId = locationId,
                    updatedEpochMs = now,
                    lastAttemptEpochMs = existing?.header?.lastAttemptEpochMs,
                    lastError = existing?.header?.lastError,
                ),
                schedule = schedule,
            )
            CacheStore.put(entries, added, pinnedCoords = pinnedCoords, maxUnpinned = MAX_ENTRIES)
        }
    }

    /** Zeitstempel und Fehlergrund des letzten Abrufversuchs für (lat,lng).
     *  [error] = null heißt Erfolg. Zeit wird übergeben, damit Tests nicht an
     *  der Systemuhr hängen. Der Parameter heißt absichtlich NICHT `lastError`
     *  — das würde den gleichnamigen Key beschatten.
     *
     *  Gibt es fuer den Ort noch keinen Eintrag, entsteht einer mit leerem
     *  Zeitplan. Er haelt die STATUSZEILE an einem Ort ohne Erfolg am Leben
     *  ("Letzter Abruf" / "Fehler", siehe `officialStatusText`) — genau das,
     *  was frueher die getrennten Versuchs-Stempel `attempt_lat`/`attempt_lng`
     *  leisteten.
     *
     *  Er traegt zugleich die Wiederholungs-Bremse: [needsRefresh] prueft die
     *  Fehlschlag-Sperre VOR `!stampOk`, ein leerer Eintrag bremst also
     *  wirklich. Ohne ihn wuerde ein Ort, an dem noch nie ein Abruf gelang,
     *  bei jedem Ausloeser erneut versucht — und in [CacheStore.dueOrder]
     *  stuende er dabei immer ganz oben und haette alle anderen Orte
     *  ausgehungert.
     *
     *  [pinnedCoords] wie bei [putAll] uebergeben — Begruendung dort. */
    suspend fun recordAttempt(
        error: String?,
        nowEpochMs: Long,
        lat: Double,
        lng: Double,
        pinnedCoords: List<Pair<Double, Double>>,
    ) {
        update { entries ->
            // Index statt Referenzvergleich: dieselbe "nur der erste
            // Treffer"-Semantik wie `CacheStore.select`, aber ohne die
            // Annahme, dass genau dieses Listenelement zurueckkommt. Gaebe
            // `select` je einen kopierten Wert zurueck, taete `recordAttempt`
            // sonst stillschweigend nichts. Die Ortsidentitaet selbst steht
            // nur in `CacheStore` — hier nachgebaut, wuerde sie still
            // abdriften, sobald sie sich dort aendert.
            val index = CacheStore.indexOf(entries, lat, lng)
            if (index >= 0) {
                // Nur den Kopf anfassen — der Zeitplan bleibt unberuehrt.
                entries.mapIndexed { i, entry ->
                    if (i == index) {
                        entry.copy(
                            header = entry.header.copy(lastAttemptEpochMs = nowEpochMs, lastError = error),
                        )
                    } else {
                        entry
                    }
                }
            } else {
                // `updatedEpochMs = nowEpochMs`, damit der frisch angelegte
                // Eintrag unter den leeren Eintraegen der juengste ist:
                // `put` haelt von denen nur den juengsten. Mit 0 flaege der
                // eben angelegte sofort selbst wieder raus, sobald schon ein
                // anderer leerer Eintrag da ist — und die Statuszeile haette
                // an diesem Ort weiterhin kein "Letzter Abruf"/"Fehler" zu
                // zeigen. Einen Jahresplan verdraengt er nicht: leere
                // Eintraege haben in `put` ihre eigene Grenze.
                CacheStore.put(
                    entries,
                    CacheEntry(
                        header = newHeader(
                            lat = lat,
                            lng = lng,
                            locationId = null,
                            updatedEpochMs = nowEpochMs,
                            lastAttemptEpochMs = nowEpochMs,
                            lastError = error,
                        ),
                        schedule = emptyMap(),
                    ),
                    pinnedCoords = pinnedCoords,
                    maxUnpinned = MAX_ENTRIES,
                )
            }
        }
    }

    /** firstDate/lastDate werden von [CacheStore] aus dem Zeitplan
     *  abgeleitet — hier bewusst null, damit sie nie doppelt gefuehrt sind. */
    private fun newHeader(
        lat: Double,
        lng: Double,
        locationId: Int?,
        updatedEpochMs: Long,
        lastAttemptEpochMs: Long?,
        lastError: String?,
    ) = CacheHeader(
        latitude = lat,
        longitude = lng,
        locationId = locationId,
        firstDate = null,
        lastDate = null,
        updatedEpochMs = updatedEpochMs,
        lastAttemptEpochMs = lastAttemptEpochMs,
        lastError = lastError,
    )

    /** Ein Eintrag fuer diesen Ort, oder null.
     *
     *  Fehlt `entries`, ist der gespeicherte Stand noch der alte
     *  Einzel-Cache. Dann wird der migrierte Stand hier EINMALIG
     *  persistiert, statt auf den naechsten Schreibvorgang zu warten: der
     *  kommt nur ueber [putAll] oder [recordAttempt], und `refreshOfficial`
     *  kehrt bei ausreichender Abdeckung vorher zurueck, ohne zu schreiben —
     *  bei einem Bestandsnutzer mit vollem Jahres-Cache also potenziell erst
     *  Monate nach dem Update. Bis dahin kostete JEDER Lesevorgang
     *  `parse` + `serialize` + `split` ueber den ganzen Jahresplan, mehr als
     *  vor dem Umbau. Nach diesem einen Schreibvorgang ist `entries` gesetzt
     *  und der Lesepfad laeuft fuer immer ueber `split` + `parseDay`.
     *
     *  Zwei gleichzeitige Lesungen sind unkritisch: `edit{}` ist
     *  serialisiert, der zweite Durchgang findet `entries` bereits vor, und
     *  `update { it }` ist dann ein reiner No-op-Rewrite desselben Inhalts. */
    private suspend fun entryFor(lat: Double, lng: Double): RawEntry? =
        CacheStore.select(allEntries(), lat, lng)

    /** Alle Eintraege, inklusive der einmaligen Persistierung eines migrierten
     *  Alt-Cache (siehe oben). */
    private suspend fun allEntries(): List<RawEntry> {
        val prefs = context.officialStore.data.first()
        prefs[entriesKey]?.let { return CacheStore.split(it) }
        // Nichts zu migrieren (frische Installation, oder kein Ortsstempel):
        // dann auch nichts schreiben — die Pruefung selbst ist billig, sie
        // faellt ohne Koordinaten sofort durch.
        if (migrateLegacy(prefs).isEmpty()) return emptyList()
        return entriesOf(update { it })
    }

    /** Orte, die fuer einen Abruf in Frage kommen, dringlichstes zuerst
     *  (Reihenfolge und Begruendung: [CacheStore.dueOrder]). Liest den
     *  Speicher EINMAL und parst keine einzige Zeit — die Auswahl braucht nur
     *  Kopfdaten. */
    suspend fun dueOrder(
        pinnedCoords: List<Pair<Double, Double>>,
        activeCoords: Pair<Double, Double>,
        today: LocalDate,
    ): List<DueLocation> = CacheStore.dueOrder(allEntries(), pinnedCoords, activeCoords, today)

    /** Lesen, aendern, schreiben und aufraeumen in EINER DataStore-
     *  Transaktion. Wichtig fuer die Migration: entweder der migrierte Stand
     *  steht mitsamt der Aenderung unter `entries` UND die alten Schluessel
     *  sind weg, oder es hat sich gar nichts geaendert. Ein Abbruch dazwischen
     *  laesst die alten Schluessel unangetastet, die naechste Lesung migriert
     *  einfach erneut. */
    private suspend fun update(transform: (List<RawEntry>) -> List<RawEntry>): Preferences =
        context.officialStore.edit { prefs ->
            prefs[entriesKey] = CacheStore.serializeRaw(transform(entriesOf(prefs)))
            removeLegacyKeys(prefs)
        }

    /** Alle Eintraege aus dem Speicher. Fehlt `entries`, stammt der Stand
     *  aus einer aelteren Version: dann wird der alte Einzel-Cache im
     *  Arbeitsspeicher migriert. Persistiert wird das entweder gleich beim
     *  ersten Lesen (siehe [entryFor]) oder vom umschliessenden [update].
     *  Der Umweg ueber serialize/split erzeugt die [RawEntry]s
     *  ausschliesslich ueber die getestete [CacheStore]-API. */
    private fun entriesOf(prefs: Preferences): List<RawEntry> {
        prefs[entriesKey]?.let { return CacheStore.split(it) }
        val migrated = migrateLegacy(prefs)
        if (migrated.isEmpty()) return emptyList()
        return CacheStore.split(CacheStore.serialize(migrated))
    }

    private fun migrateLegacy(prefs: Preferences): List<CacheEntry> {
        val lat = prefs[legacyStampLat]
        val lng = prefs[legacyStampLng]
        // Der alte Versuchs-Stempel (attempt_lat/attempt_lng) konnte auf einen
        // ANDEREN Ort zeigen als der Erfolgs-Stempel. Zeigt er woandershin,
        // gehoert das Versuchsprotokoll nicht in diesen Eintrag und wird
        // verworfen — sonst erschiene der Fehler eines fremden Ortes hier.
        // Der Zeitplan, das eigentlich Wertvolle, bleibt davon unberuehrt.
        val attemptBelongsHere = lat != null && lng != null &&
            stampMatches(prefs[legacyAttemptLat], prefs[legacyAttemptLng], lat, lng)
        return CacheStore.migrateLegacy(
            schedule = prefs[legacySchedule],
            lat = lat,
            lng = lng,
            locationId = prefs[legacyStampId],
            lastAttemptEpochMs = if (attemptBelongsHere) prefs[legacyLastAttempt] else null,
            lastError = if (attemptBelongsHere) prefs[legacyLastError] else null,
            nowEpochMs = System.currentTimeMillis(),
        )
    }

    private fun removeLegacyKeys(prefs: MutablePreferences) {
        prefs.remove(legacySchedule)
        prefs.remove(legacyStampLat)
        prefs.remove(legacyStampLng)
        prefs.remove(legacyStampId)
        prefs.remove(legacyLastAttempt)
        prefs.remove(legacyLastError)
        prefs.remove(legacyAttemptLat)
        prefs.remove(legacyAttemptLng)
    }

    private companion object {
        /** Fuenf NICHT angeheftete Orte mit Zeitplan. Dazu kommt hoechstens
         *  ein nicht angehefteter leerer Eintrag (reines Versuchsprotokoll),
         *  den [CacheStore.put] getrennt begrenzt — und, ohne Grenze, die
         *  angehefteten Orte: ein Favorit wird NIE verdraengt.
         *
         *  Speichergroesse, bewusst akzeptiert: 10 Favoriten + 5 nicht
         *  angeheftete + 1 leerer Eintrag sind ~16 Eintraege a ~17 KB, also
         *  ~270 KB in einem einzigen Preferences-String. DataStore haelt den
         *  im Speicher und schreibt ihn bei jedem `edit{}` komplett neu; bei
         *  ~6 Schreibvorgaengen am Tag ist das belanglos. Der Lesepfad parst
         *  weiterhin nur den gefragten Tag ([ScheduleText.parseDay]), nicht
         *  den ganzen String. */
        const val MAX_ENTRIES = 5
    }
}

/** Momentaufnahme für die Statuszeile eines EINZELNEN Orts (Aufrufer:
 *  `SettingsSheet`).
 *
 *  Die Auffrischung geht NICHT mehr hierueber: `refreshOfficial` waehlt seinen
 *  Ort ueber [OfficialTimesCache.dueOrder] und liest die Bremsen-Eingaben
 *  direkt aus dem Kopf des jeweiligen Kandidaten — es braucht ja Angaben zu
 *  mehreren Orten, nicht nur zum aktiven.
 *
 *  [stampOk] hat deshalb keinen Produktionsaufrufer mehr und ist nur noch
 *  Vertragsdokumentation („hat dieser Ort einen Zeitplan?"); der Default
 *  `true` haelt die bestehenden Aufrufe in der Statuszeile gueltig, die ihn
 *  ohnehin ignorieren. */
data class OfficialStatus(
    val locationId: Int?,
    val coveredUntil: LocalDate?,
    val lastAttemptEpochMs: Long?,
    val lastError: String?,
    val stampOk: Boolean = true,
)
