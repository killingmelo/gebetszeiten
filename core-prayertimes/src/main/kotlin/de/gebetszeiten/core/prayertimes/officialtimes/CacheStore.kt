package de.gebetszeiten.core.prayertimes.officialtimes

import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
 * Ein Ort, der fuer einen Abruf in Frage kommt, samt dem Cache-Eintrag, der
 * heute fuer ihn vorliegt. Ergebnis von [CacheStore.dueOrder].
 */
data class DueLocation(
    val latitude: Double,
    val longitude: Double,
    val pinned: Boolean,
    /** null = fuer diesen Ort gibt es noch gar keinen Eintrag. */
    val entry: RawEntry?,
)

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
     *  etwas schiefging.
     *
     *  Angeheftete Eintraege sind von BEIDEN Grenzen ausgenommen, auch von
     *  der Eins-Grenze fuer leere. Bei zehn Favoriten koennen also bis zu
     *  zehn leere Eintraege koexistieren, dazu der eine nicht angeheftete.
     *  Das ist gewollt: ein leerer Eintrag ist das Versuchsprotokoll seines
     *  Ortes, und an einem Favoriten, an dem noch nie ein Abruf gelang, ist
     *  genau das die einzige Auskunft, die die Statuszeile geben kann. Er
     *  kostet eine Kopfzeile, kein Jahresplan weicht ihm.
     *
     *  [added] selbst wird NIE verdraengt — sonst koennte `put` den eben
     *  hinzugefuegten Eintrag im selben Atemzug wieder wegwerfen (leerer
     *  Eintrag, dessen `updatedEpochMs` hinter dem eines schon vorhandenen
     *  leeren liegt: rueckwaerts gestellte Uhr, oder ein migrierter Eintrag
     *  mit spaeterem Stempel). Er zaehlt aber weiter GEGEN die Grenze —
     *  verdraengt wird dann der aelteste der anderen. */
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
        val evicted = evictionCandidates(withSchedule, keep = maxUnpinned, protected = newEntry) +
            evictionCandidates(empty, keep = 1, protected = newEntry)
        // Aussortiert wird ueber IDENTITAET, derselbe Begriff, mit dem
        // `evictionCandidates` den geschuetzten Eintrag heraushaelt. Ein Set
        // aus `RawEntry` wuerde ueber WERTgleichheit filtern — zwei Begriffe
        // in einer Funktion, und der Unterschied waere genau dann zu spueren,
        // wenn er am wenigsten auffaellt.
        return result.filterNot { entry -> evicted.any { it === entry } }
    }

    /** Die Eintraege aus [group], die ueber [keep] hinausgehen: die
     *  aeltesten nach `updatedEpochMs`. [protected] (der gerade
     *  hinzugefuegte Eintrag) zaehlt gegen [keep], kommt aber selbst nie in
     *  die Verdraengungsliste — Identitaets-, nicht Wertvergleich, denn
     *  verglichen wird ein bestimmtes Listenelement, nicht ein gleicher
     *  Inhalt. */
    private fun evictionCandidates(group: List<RawEntry>, keep: Int, protected: RawEntry): List<RawEntry> {
        val excess = group.size - keep
        if (excess <= 0) return emptyList()
        return group.filterNot { it === protected }
            .sortedBy { it.header.updatedEpochMs }
            .take(excess)
    }

    /**
     * Orte, die einen Abruf brauchen — DAS DRINGLICHSTE ZUERST.
     *
     * Kandidaten sind die Favoriten ([pinnedCoords]) plus der aktive Ort
     * ([activeCoords]), und sonst nichts. Die nicht angehefteten
     * Cache-Eintraege bleiben ausdruecklich draussen: das sind zufaellig
     * besuchte Orte, an denen der Nutzer nicht ist — fuer sie Netz zu
     * verbrauchen hilft niemandem.
     *
     * Kandidaten sind ZUERST alle Favoriten in ihrer Listenreihenfolge; der
     * aktive Ort kommt danach und VERSCHMILZT in den ersten passenden
     * Favoriten (Identitaet ueber `stampMatches`), statt einen zu verdraengen.
     * Er erscheint also genau einmal, mit `pinned = true` und seinen aktiven
     * Koordinaten — die aktiven gewinnen, weil das der Ort ist, an dem der
     * Nutzer gerade steht; auf seinen Eintrag hat das keinen Einfluss, der
     * laeuft ohnehin ueber die ~1-km-Toleranz. Passt er zu KEINEM Favoriten,
     * wird er ein eigener Kandidat mit `pinned = false`.
     *
     * Nur der ERSTE passende Favorit verschmilzt, alle uebrigen bleiben
     * unveraendert erhalten. Das ist der Grund fuer diese Richtung:
     * `stampMatches` ist nicht transitiv, zwei Favoriten koennen 1,8 km
     * auseinanderliegen, waehrend der aktive Ort genau dazwischen zu beiden
     * passt. Wuerde er beide als „schon bekannt" verwerfen, kaeme der zweite
     * Favorit NIE wieder an die Reihe und veraltete stillschweigend.
     *
     * Sortiert wird nach der Restabdeckung in Tagen
     * (`header.lastDate − today`), aufsteigend. Fehlt der Eintrag oder traegt
     * er keinen Zeitplan (`lastDate == null`, also nur ein
     * Versuchsprotokoll), gilt der Ort als unendlich dringend und steht vor
     * allem anderen. Vorn stehen damit: erst Orte ohne jede Zeiten — darunter
     * ein gerade angelegter Favorit, der noch nie geholt wurde —, dann Orte
     * mit ABGELAUFENER Abdeckung (negative Restabdeckung), dann alles uebrige.
     *
     * Bei Gleichstand kommt der AKTIVE Ort zuerst, danach gilt die
     * Reihenfolge von [pinnedCoords]. Der aktive Ort ist der einzige, dessen
     * Zeiten der Nutzer in diesem Moment ansieht: ein Favorit in Istanbul
     * darf warten, der Bildschirm vor ihm nicht. Der Gleichstands-Schluessel
     * ist explizit (nicht bloss der stabilen Sortierung ueberlassen), das
     * Ergebnis also deterministisch.
     *
     * KEINE Sperrfristen — hier wird nur geordnet. Ob ein Ort tatsaechlich
     * abgerufen wird, entscheidet der Aufrufer (`needsRefresh`): er geht die
     * Liste von vorn durch und nimmt den ersten, der die Bremse passiert.
     * Deshalb eine LISTE statt eines einzelnen Eintrags — waere es einer,
     * wuerde ein Ort in Sperrfrist alle anderen blockieren.
     *
     * Zuordnung Ort → Eintrag ueber [select], also der ERSTE Treffer.
     * `stampMatches` ist nicht transitiv: liegen zwei Favoriten dicht
     * beieinander, kann derselbe Eintrag zu beiden passen und wird dann auch
     * beiden zugeordnet. Das ist die konservative Richtung — beide gelten als
     * versorgt, statt dass einer von ihnen grundlos einen Abruf ausloest —,
     * und sie ist begruendet: es ist DERSELBE Eintrag, er passt wirklich zu
     * beiden.
     *
     * Beim VERSCHMELZEN des aktiven Orts in einen Favoriten gilt diese
     * Begruendung gerade nicht: die Koordinaten sind die aktiven, aber der
     * Nachschlag am Favoriten und der am aktiven Ort koennen VERSCHIEDENE
     * Eintraege finden — zwischen den beiden Punkten liegen bis zu 1,81 km.
     * Der verschmolzene Kandidat bekommt deshalb den DRINGLICHEREN der
     * beiden Nachschlaege, wobei `null` gewinnt (siehe [moreUrgent]). Sonst
     * gilt einer der beiden Orte als versorgt, obwohl SEINE Zeiten fehlen —
     * und das ist die eine gefaehrliche Richtung: der Nutzer sieht eine
     * Berechnung statt amtlicher Zeiten, ohne dass die App je versucht
     * haette, sie zu holen. Genau dagegen gibt es diese Funktion.
     *
     * „Hole, wenn EINER der beiden es braucht" kostet keinen zusaetzlichen
     * Abruf: beide Punkte liegen unter 1 km von den Kandidaten-Koordinaten,
     * loesen also zur selben Diyanet-Standort-ID auf, und ein Abruf mit den
     * Kandidaten-Koordinaten legt einen Eintrag ab, der zu BEIDEN passt. Ein
     * Abruf bedient damit beide.
     *
     * Eine Endlosschleife entsteht daraus nicht: mit `entry == null` hat der
     * Kandidat kein Versuchsprotokoll und passiert jede Sperrfrist — aber nur
     * bis zum ERSTEN Versuch. Danach liegt ein Eintrag an den
     * Kandidaten-Koordinaten, und der passt zu beiden Punkten; ab dann greift
     * die Sperre normal. Darauf kann sich der Aufrufer verlassen.
     */
    fun dueOrder(
        entries: List<RawEntry>,
        pinnedCoords: List<Pair<Double, Double>>,
        activeCoords: Pair<Double, Double>?,
        today: LocalDate,
    ): List<DueLocation> {
        // Der Gleichstands-Vorrang haengt NICHT an der Aufbaureihenfolge: die
        // Favoriten kommen zuerst in die Liste, der aktive Ort (Rang 0)
        // danach. `tieRank` haelt den Vorrang fest, damit die Sortierung nicht
        // von der Stabilitaet abhaengt.
        val candidates = mutableListOf<Candidate>()

        // Der Eintrag kommt von aussen herein, statt hier nachgeschlagen zu
        // werden: der verschmolzene Kandidat waehlt zwischen ZWEI
        // Nachschlaegen (siehe unten).
        fun candidateAt(lat: Double, lng: Double, pinned: Boolean, tieRank: Int, entry: RawEntry?) = Candidate(
            location = DueLocation(latitude = lat, longitude = lng, pinned = pinned, entry = entry),
            // Restabdeckung in Tagen; null = keine Zeiten, also unendlich
            // dringend (kein Eintrag, oder ein Eintrag mit leerem Zeitplan).
            remainingDays = entry?.header?.lastDate?.let { ChronoUnit.DAYS.between(today, it) },
            tieRank = tieRank,
        )

        // ZUERST alle Favoriten, in ihrer Listenreihenfolge — so kann keiner
        // von ihnen verlorengehen. Die Entdopplung untereinander ist eine
        // eigene Zusicherung dieser Funktion: derselbe Ort erscheint nie
        // zweimal, unabhaengig davon, was der Aufrufer liefert. Zwei
        // Favoriten innerhalb der Toleranz ergeben also EINEN Kandidaten. Sie
        // ist tragend, nicht ueberfluessig — `pinnedCoords` kommt ungeprueft
        // herein.
        for ((index, coords) in pinnedCoords.withIndex()) {
            val (pLat, pLng) = coords
            val known = candidates.any {
                stampMatches(it.location.latitude, it.location.longitude, pLat, pLng)
            }
            if (known) continue
            val entry = select(entries, pLat, pLng)
            candidates.add(candidateAt(pLat, pLng, pinned = true, tieRank = index + 1, entry = entry))
        }

        // DANN der aktive Ort — er VERSCHMILZT in den ersten passenden
        // Favoriten, statt Favoriten zu verdraengen. Passt er zu keinem, wird
        // er ein eigener, nicht angehefteter Kandidat.
        if (activeCoords != null) {
            val (aLat, aLng) = activeCoords
            val activeEntry = select(entries, aLat, aLng)
            val matchIndex = candidates.indexOfFirst {
                stampMatches(it.location.latitude, it.location.longitude, aLat, aLng)
            }
            if (matchIndex >= 0) {
                // Die Koordinaten sind die aktiven, der Eintrag aber der
                // DRINGLICHERE der beiden Nachschlaege. Der Nachschlag mit
                // den aktiven Koordinaten allein wuerde den Favoriten als
                // versorgt melden, obwohl DESSEN Zeiten fehlen — die beiden
                // Punkte koennen 1,81 km auseinanderliegen.
                candidates[matchIndex] = candidateAt(
                    aLat,
                    aLng,
                    pinned = true,
                    tieRank = 0,
                    entry = moreUrgent(candidates[matchIndex].location.entry, activeEntry),
                )
            } else {
                candidates.add(candidateAt(aLat, aLng, pinned = false, tieRank = 0, entry = activeEntry))
            }
        }

        return candidates
            .sortedWith(compareBy<Candidate, Long?>(nullsFirst()) { it.remainingDays }.thenBy { it.tieRank })
            .map { it.location }
    }

    /** Der DRINGLICHERE von zwei Eintragsnachschlaegen — `null` gewinnt:
     *  kein Eintrag heisst keine Zeiten, also maximal dringend. Sonst
     *  gewinnt die kleinere Restabdeckung; ein Eintrag ohne Zeitplan
     *  (`lastDate == null`) ist nur ein Versuchsprotokoll und zaehlt
     *  ebenfalls als keine Zeiten. Bei Gleichstand bleibt [a]. */
    private fun moreUrgent(a: RawEntry?, b: RawEntry?): RawEntry? {
        if (a == null || b == null) return null
        val aLast = a.header.lastDate ?: return a
        val bLast = b.header.lastDate ?: return b
        return if (bLast.isBefore(aLast)) b else a
    }

    private data class Candidate(val location: DueLocation, val remainingDays: Long?, val tieRank: Int)

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
