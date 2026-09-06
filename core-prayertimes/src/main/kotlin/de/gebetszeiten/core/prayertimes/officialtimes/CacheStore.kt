package de.gebetszeiten.core.prayertimes.officialtimes

import java.time.LocalDate

/**
 * Kopfzeile eines Cache-Eintrags. Kein `pinned`-Feld absichtlich: welche
 * Orte angeheftet sind, steht in den Einstellungen (Favoritenliste) — ein
 * zweites Mal hier gefuehrt koennte davon abdriften. `put` bekommt die
 * angehefteten Koordinaten stattdessen uebergeben.
 *
 * `lastAttemptFailed` ist bewusst kein eigenes Feld: es ist immer genau
 * `lastError != null`.
 */
data class CacheHeader(
    val latitude: Double,
    val longitude: Double,
    val locationId: Int?,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val updatedEpochMs: Long,
    val lastAttemptEpochMs: Long?,
    val lastError: String?,
)

/** Rumpf BEWUSST als ungeparster String — teuer zu parsen ist nur, was
 *  tatsaechlich gebraucht wird (siehe [ScheduleText.parseDay]). */
data class RawEntry(val header: CacheHeader, val body: String)

data class CacheEntry(val header: CacheHeader, val schedule: Map<LocalDate, SixTimes>)

/**
 * Reine Speicherschicht fuer MEHRERE Orte im Zeiten-Cache. Ein Eintrag =
 * eine Kopfzeile (beginnend mit `#`) gefolgt von ihren Tageszeilen im
 * `ScheduleText`-Format. Tageszeilen beginnen immer mit einer Jahreszahl,
 * koennen also nie mit `#` verwechselt werden.
 *
 * ```
 * #<lat>|<lng>|<locationId|->|<firstDate|->|<lastDate|->|<updatedEpochMs>|<lastAttemptEpochMs|->|<lastError|->
 * 2026-09-06 04:54 06:23 13:02 16:39 19:31 20:53
 * ... weitere Tageszeilen ...
 * #<lat>|<lng>|...
 * ```
 *
 * Verdrahtet nichts — reine Funktionen auf Listen/Strings, kein Android,
 * kein Netz, kein Context.
 */
object CacheStore {

    private const val FIELD_SEP = "|"
    private const val NULL_MARKER = "-"

    /** Billiger Scan: zerlegt in Eintraege, ohne eine einzige Zeit zu
     *  parsen. Der Rumpf wird als Teilstring uebernommen. Tolerant: eine
     *  unlesbare Kopfzeile verwirft NUR ihren Eintrag, nicht die Nachbarn —
     *  ihre Tageszeilen werden mit verworfen (sie gehoeren zu keinem
     *  gueltigen Eintrag mehr). */
    fun split(text: String?): List<RawEntry> {
        if (text.isNullOrEmpty()) return emptyList()

        val result = mutableListOf<RawEntry>()
        var currentHeader: CacheHeader? = null
        var bodyLines = mutableListOf<String>()

        fun flush() {
            val header = currentHeader
            if (header != null) {
                result.add(RawEntry(header, bodyLines.joinToString("\n")))
            }
            bodyLines = mutableListOf()
        }

        for (line in text.lineSequence()) {
            if (line.startsWith("#")) {
                flush()
                currentHeader = parseHeader(line)
            } else {
                // Zeilen ohne gueltige Kopfzeile (vor der allerersten, oder
                // nach einer kaputten) landen zwar in bodyLines, werden aber
                // beim naechsten flush() verworfen, da currentHeader dann
                // null ist — kein gueltiger Eintrag entsteht daraus.
                bodyLines.add(line)
            }
        }
        flush()
        return result
    }

    /** firstDate/lastDate werden aus dem Zeitplan ABGELEITET, nicht vom
     *  Aufrufer uebernommen — so kann der Kopf nie vom Rumpf abweichen. */
    fun serialize(entries: List<CacheEntry>): String =
        serializeRaw(entries.map { toRawEntry(it) })

    /** Gegenstueck zu [split]: schreibt Eintraege zurueck, OHNE die Ruempfe
     *  anzufassen. [put] liefert [RawEntry]s — ein Umweg ueber [CacheEntry]
     *  wuerde bei JEDEM Schreibvorgang jeden gespeicherten Jahresplan neu
     *  parsen und serialisieren, obwohl sich nur ein Kopffeld aendert. */
    fun serializeRaw(entries: List<RawEntry>): String =
        entries.joinToString("\n") { entry ->
            val headerLine = formatHeader(entry.header)
            if (entry.body.isEmpty()) headerLine else "$headerLine\n${entry.body}"
        }

    /** Position des Eintrags fuer diese Koordinaten, oder -1. EINZIGE
     *  Stelle, an der die Ortsidentitaet ausgewertet wird — [select] und
     *  Aufrufer, die den Eintrag an Ort und Stelle aendern wollen
     *  (`OfficialTimesCache.recordAttempt`), gehen beide hierueber, damit
     *  sie nie auseinanderdriften. */
    fun indexOf(entries: List<RawEntry>, lat: Double, lng: Double): Int =
        entries.indexOfFirst { stampMatches(it.header.latitude, it.header.longitude, lat, lng) }

    /** Eintrag fuer diese Koordinaten (ueber `stampMatches`), oder null. */
    fun select(entries: List<RawEntry>, lat: Double, lng: Double): RawEntry? =
        entries.getOrNull(indexOf(entries, lat, lng))

    /** [added] einfuegen oder den passenden Eintrag ersetzen (Identitaet
     *  ueber `stampMatches` — eine Ortsverschiebung um ~1 km aktualisiert
     *  den bestehenden Eintrag, statt einen Platz zu verbrauchen).
     *  Angeheftete Eintraege werden NIE verdraengt.
     *
     *  Nicht angeheftete Eintraege werden GETRENNT begrenzt, je nachdem ob
     *  sie einen Zeitplan tragen:
     *  - MIT Zeitplan: hoechstens [maxUnpinned], aeltester `updatedEpochMs`
     *    zuerst raus.
     *  - OHNE Zeitplan (`lastDate == null`, also nur ein Versuchsprotokoll):
     *    hoechstens EINER, und zwar der juengste.
     *
     *  Der Cache haelt damit hoechstens `maxUnpinned + 1` nicht angeheftete
     *  Eintraege, von denen hoechstens einer leer ist. Beide Gruppen um
     *  denselben Platz konkurrieren zu lassen, geht in beide Richtungen
     *  schief: entweder wirft ein einziger Fehlversuch an einem sechsten Ort
     *  einen echten Jahresplan hinaus, oder — verdraengt man leere Eintraege
     *  zuerst — faellt der eben protokollierte Fehlversuch bei vollem Cache
     *  im selben Atemzug wieder raus und die Statuszeile verschweigt den
     *  Fehler. Ein leerer Eintrag kostet eine Kopfzeile statt eines
     *  Jahresplans, er braucht keinen der [maxUnpinned] Plaetze. Mehr als
     *  einen braucht niemand: relevant ist immer der Ort, an dem gerade
     *  etwas schiefging. */
    fun put(
        entries: List<RawEntry>,
        added: CacheEntry,
        pinnedCoords: List<Pair<Double, Double>>,
        maxUnpinned: Int = 5,
    ): List<RawEntry> {
        val newEntry = toRawEntry(added)

        val withoutMatch = entries.filterNot {
            stampMatches(it.header.latitude, it.header.longitude, newEntry.header.latitude, newEntry.header.longitude)
        }
        val result = withoutMatch + newEntry

        fun isPinned(entry: RawEntry) = pinnedCoords.any { (pLat, pLng) ->
            stampMatches(entry.header.latitude, entry.header.longitude, pLat, pLng)
        }

        val (withSchedule, empty) = result.filterNot { isPinned(it) }
            .partition { it.header.lastDate != null }
        // Aufsteigend nach Alter sortiert und die juengsten behalten: was
        // uebrig bleibt, ist zu viel. `dropLast` auf einer zu kurzen Liste
        // ist leer — dann faellt nichts.
        val evicted = (
            withSchedule.sortedBy { it.header.updatedEpochMs }.dropLast(maxUnpinned) +
                empty.sortedBy { it.header.updatedEpochMs }.dropLast(1)
            ).toSet()
        return result.filterNot { it in evicted }
    }

    /** Alten Einzel-Cache in einen Eintrag ueberfuehren. Gibt eine leere
     *  Liste zurueck, wenn nichts Brauchbares da ist: kein Ortsstempel,
     *  oder weder Zeitplan noch Versuchsdaten. `updatedEpochMs` bekommt
     *  [nowEpochMs], da der alte Einzel-Cache keinen eigenen "zuletzt
     *  erfolgreich aktualisiert"-Zeitstempel kennt. */
    fun migrateLegacy(
        schedule: String?,
        lat: Double?,
        lng: Double?,
        locationId: Int?,
        lastAttemptEpochMs: Long?,
        lastError: String?,
        nowEpochMs: Long,
    ): List<CacheEntry> {
        if (lat == null || lng == null) return emptyList()

        val parsedSchedule = schedule?.let { ScheduleText.parse(it) } ?: emptyMap()
        if (parsedSchedule.isEmpty() && lastAttemptEpochMs == null && lastError == null) return emptyList()

        val header = CacheHeader(
            latitude = lat,
            longitude = lng,
            locationId = locationId,
            firstDate = parsedSchedule.keys.minOrNull(),
            lastDate = parsedSchedule.keys.maxOrNull(),
            updatedEpochMs = nowEpochMs,
            lastAttemptEpochMs = lastAttemptEpochMs,
            lastError = lastError,
        )
        return listOf(CacheEntry(header, parsedSchedule))
    }

    private fun toRawEntry(entry: CacheEntry): RawEntry {
        val dates = entry.schedule.keys
        val header = entry.header.copy(
            firstDate = dates.minOrNull(),
            lastDate = dates.maxOrNull(),
        )
        return RawEntry(header, ScheduleText.serialize(entry.schedule))
    }

    /** Vorwaertskompatibel: zusaetzliche Felder am Ende werden ignoriert,
     *  fehlende hintere Felder bekommen Standardwerte (statt die Zeile zu
     *  verwerfen) — ein spaeterer Task haengt ein weiteres Kopffeld an, und
     *  das darf bestehende Caches beim Upgrade nicht zerstoeren. Nur die
     *  ersten sechs Felder (bis `updatedEpochMs`) sind Pflicht. */
    private fun parseHeader(line: String): CacheHeader? {
        if (!line.startsWith("#")) return null
        val parts = line.substring(1).split(FIELD_SEP)
        if (parts.size < 6) return null
        return try {
            CacheHeader(
                latitude = parts[0].toDouble(),
                longitude = parts[1].toDouble(),
                locationId = parts[2].orNullMarker()?.toInt(),
                firstDate = parts[3].orNullMarker()?.let { LocalDate.parse(it) },
                lastDate = parts[4].orNullMarker()?.let { LocalDate.parse(it) },
                updatedEpochMs = parts[5].toLong(),
                lastAttemptEpochMs = parts.getOrNull(6)?.orNullMarker()?.toLong(),
                lastError = parts.getOrNull(7)?.orNullMarker(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun formatHeader(header: CacheHeader): String {
        val locationId = header.locationId?.toString() ?: NULL_MARKER
        val firstDate = header.firstDate?.toString() ?: NULL_MARKER
        val lastDate = header.lastDate?.toString() ?: NULL_MARKER
        val lastAttempt = header.lastAttemptEpochMs?.toString() ?: NULL_MARKER
        // `|` darf im Fehlertext nicht vorkommen (Trennzeichen), ein
        // Zeilenumbruch wuerde eine zweite "Kopfzeile" vortaeuschen.
        // Fehlertexte sind unsere eigenen Literale — der Verlust ist
        // theoretisch.
        val error = header.lastError?.replace(FIELD_SEP, " ")?.replace("\n", " ")?.replace("\r", " ") ?: NULL_MARKER
        return "#${header.latitude}$FIELD_SEP${header.longitude}$FIELD_SEP$locationId$FIELD_SEP" +
            "$firstDate$FIELD_SEP$lastDate$FIELD_SEP${header.updatedEpochMs}$FIELD_SEP$lastAttempt$FIELD_SEP$error"
    }

    private fun String.orNullMarker(): String? = takeIf { it != NULL_MARKER }
}
