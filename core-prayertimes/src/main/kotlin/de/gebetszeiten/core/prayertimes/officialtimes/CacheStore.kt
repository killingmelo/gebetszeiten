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
 *
 * [verification] ist das Prueferzeugnis des Abrufs, der DIESEN Zeitplan
 * gebracht hat. Es liegt hier und nicht neben dem Cache, weil es sonst beim
 * naechsten Oeffnen der Einstellungen weg waere: es gehoert zum Eintrag,
 * genau wie seine Abdeckung. `null` heisst „nicht bekannt" — ein Eintrag aus
 * einer aelteren App-Version, ein reines Versuchsprotokoll ohne Zeitplan,
 * oder ein unlesbar gewordenes Feld.
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
    val verification: Verification? = null,
)

/** Rumpf BEWUSST als ungeparster String — teuer zu parsen ist nur, was
 *  tatsaechlich gebraucht wird (siehe [ScheduleText.parseDay]). */
data class RawEntry(val header: CacheHeader, val body: String)

data class CacheEntry(val header: CacheHeader, val schedule: Map<LocalDate, SixTimes>)

/**
 * Ein Ort, der fuer einen Abruf in Frage kommt, samt der Kopfdaten, die heute
 * fuer ihn vorliegen. Ergebnis von [CacheStore.dueOrder].
 *
 * Bewusst KEIN `entry: RawEntry?`: Abdeckung und Versuchsprotokoll sind zwei
 * verschiedene Dinge und muessen beim Verschmelzen des aktiven Orts in einen
 * Favoriten GETRENNT verschmelzen (Begruendung in [CacheStore.dueOrder]).
 * Steckten sie in EINEM Eintrag, muesste das Verschmelzen beides zusammen
 * wegwerfen — und der Kandidat saehe fuer immer wie „noch nie versucht" aus,
 * also unbremsbar. Mehr als diese Felder braucht auch niemand: der Rumpf des
 * Eintrags ist fuer die Auswahl belanglos.
 */
data class DueLocation(
    val latitude: Double,
    val longitude: Double,
    val pinned: Boolean,
    /** Abdeckung bis; null = fuer diesen Ort gibt es keine Zeiten (kein
     *  Eintrag, oder ein Eintrag mit leerem Zeitplan). */
    val coveredUntil: LocalDate?,
    val lastAttemptEpochMs: Long?,
    val lastError: String?,
) {
    /** Schon versucht, gescheitert, und immer noch GAR KEINE Zeiten — ein Ort,
     *  der nachweislich nicht geht. Er wird weiter versucht, aber erst, wenn
     *  nichts anderes etwas braucht (Sortierschluessel 1 in
     *  [CacheStore.dueOrder]). Ein FRISCH angelegter Favorit ist nicht
     *  hoffnungslos: er hat noch keinen Fehler vorzuweisen. */
    val hopeless: Boolean get() = lastError != null && coveredUntil == null
}

/**
 * Reine Speicherschicht fuer MEHRERE Orte im Zeiten-Cache. Ein Eintrag =
 * eine Kopfzeile (beginnend mit `#`) gefolgt von ihren Tageszeilen im
 * `ScheduleText`-Format. Tageszeilen beginnen immer mit einer Jahreszahl,
 * koennen also nie mit `#` verwechselt werden.
 *
 * ```
 * #<lat>|<lng>|<locationId|->|<firstDate|->|<lastDate|->|<updatedEpochMs>|<lastAttemptEpochMs|->|<lastError|->|<verification|->
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

    /** Trennzeichen INNERHALB des Verification-Feldes, und das Trennzeichen
     *  seiner Quellenliste. Beide sind sicher, weil kein einziger Wert dort
     *  Freitext ist: Aufzaehlungswerte (`[A-Z_]`), nicht negative ganze
     *  Zahlen, ein ISO-Datum (`[0-9-]`) und der Null-Marker `-`. Weder `~`
     *  noch `,` kann in einem davon vorkommen; `-` waere als Trennzeichen
     *  untauglich, es steckt in jedem Datum. Der einzige Freitext im ganzen
     *  Kopf ist `lastError`, und der hat sein eigenes Feld samt Bereinigung
     *  (siehe [formatHeader]). */
    private const val SUB_SEP = "~"
    private const val LIST_SEP = ","

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

    /**
     * Die Koepfe zu MEHREREN Orten auf einmal — fuer die Favoritenliste im
     * Einstellungsblatt (`OfficialTimesCache.statusesFor`). Ein einziger
     * [split] speist alle Nachschlaege, statt den ~270-KB-String je Favorit
     * neu zu zerlegen.
     *
     * **Die Reihenfolge ist die von [coords], nicht die des Speichers**, und
     * ein Ort ohne Eintrag ergibt `null` AN SEINER STELLE, keine Luecke: der
     * Aufrufer zeichnet je Favorit eine Zeile und paart ueber den Index.
     * Verruecken sich die beiden Listen gegeneinander, stuende Istanbuls
     * Stand unter Nuernbergs Namen — amtlich aussehende Zeiten am falschen
     * Ort, genau die Fehlerklasse, die dieser Cache verhindern soll. Deshalb
     * liegt die Zuordnung hier, in der reinen Schicht, und nicht im Adapter.
     *
     * Rueckgabe sind [CacheHeader], nicht ein fertiger Anzeigestatus: was
     * eine Statuszeile braucht, ist ein Begriff der App, und `core` soll ihn
     * nicht kennen. Der Nachschlag je Ort geht ueber [select], hat also
     * dieselbe Ortsidentitaet wie jeder andere Zugriff.
     */
    fun headersFor(entries: List<RawEntry>, coords: List<Pair<Double, Double>>): List<CacheHeader?> =
        coords.map { (lat, lng) -> select(entries, lat, lng)?.header }

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
     * **Sortierschluessel, aufsteigend, dringlichstes zuerst:**
     *
     * 1. `hopeless` (0/1, siehe [DueLocation.hopeless]) — ein Ort, der
     *    NACHWEISLICH nicht geht, darf keinen verdraengen, der gehen koennte.
     * 2. Restabdeckung in Tagen (`coveredUntil − today`), `null` zuerst.
     *    Fehlt der Eintrag oder traegt er keinen Zeitplan, gilt der Ort als
     *    unendlich dringend. Vorn stehen damit: erst Orte ohne jede Zeiten —
     *    darunter ein gerade angelegter Favorit —, dann Orte mit
     *    ABGELAUFENER Abdeckung, dann alles uebrige.
     * 3. der AKTIVE Ort (0) vor Favoriten (1). Er ist der einzige, dessen
     *    Zeiten der Nutzer in diesem Moment ansieht: ein Favorit in Istanbul
     *    darf warten, der Bildschirm vor ihm nicht.
     * 4. `lastAttemptEpochMs` aufsteigend, `null` zuerst — ROTATION: am
     *    laengsten nicht versucht zuerst.
     * 5. Reihenfolge in [pinnedCoords] — Determinismus.
     *
     * **Schluessel 1 und 4 sind der Aushungerungsschutz, nicht die
     * Sperrfrist.** Die Fehlschlag-Sperre in `needsRefresh` deckelt nur
     * schnelle Wiederholungen (halbe Stunde); der kuerzeste echte
     * Ausloeser-Abstand — Maghrib zu Isha, Fajr zu Guenes — liegt bei ~1 h,
     * typisch 2-5 h, die Sperre ist bei jedem Gebets-Alarm also laengst
     * abgelaufen. Ohne Schluessel 1 stuende ein dauerhaft scheiternder Ort
     * (Favorit weiter als 25 km vom naechsten Diyanet-Standort, Netz weg)
     * durch Schluessel 2 auf Platz 1 und frasse JEDEN Ausloeser — auch den
     * des aktiven Orts, der dann selbst ohne amtliche Zeiten dastaende.
     * Schluessel 4 faengt die zweite Haelfte: sind ALLE Kandidaten
     * hoffnungslos (kein Netz, zehn Favoriten), rotiert die Auswahl, statt am
     * ersten zu haengen.
     *
     * Ein hoffnungsloser Ort wird also weiter versucht — aber erst, wenn
     * nichts anderes etwas braucht. Alle Gleichstands-Schluessel sind
     * explizit (nicht der stabilen Sortierung ueberlassen), das Ergebnis also
     * deterministisch.
     *
     * KEINE Sperrfristen — hier wird nur geordnet. Ob ein Ort tatsaechlich
     * abgerufen wird, entscheidet der Aufrufer (`chooseTarget` ueber
     * `needsRefresh`): er geht die Liste von vorn durch und nimmt den ersten,
     * der die Bremse passiert. Deshalb eine LISTE statt eines einzelnen
     * Eintrags — waere es einer, wuerde ein Ort in Sperrfrist alle anderen
     * blockieren.
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
     * Die beiden Nachschlaege verschmelzen deshalb FELDWEISE, und zwar
     * Abdeckung und Versuchsprotokoll GETRENNT (siehe [dueLocationOf]):
     *
     * - `coveredUntil` = die KLEINERE der beiden, `null` gewinnt. Sonst gilt
     *   einer der beiden Orte als versorgt, obwohl SEINE Zeiten fehlen — die
     *   eine gefaehrliche Richtung: der Nutzer sieht eine Berechnung statt
     *   amtlicher Zeiten, ohne dass die App je versucht haette, sie zu holen.
     * - Versuchsprotokoll = das des JUENGEREN Versuchs, `lastAttemptEpochMs`
     *   und `lastError` aus demselben Kopf. Ein Nachschlag, der null ist,
     *   traegt keines bei. Wuerde das Protokoll mit der Abdeckung zusammen
     *   weggeworfen, saehe der verschmolzene Kandidat fuer immer wie „noch nie
     *   versucht" aus: er passierte jede Sperrfrist, und `recordAttempt` legte
     *   den Versuch am gefundenen Eintrag ab, nicht an den
     *   Kandidaten-Koordinaten — ein Versuch je Ausloeser, unbegrenzt.
     *
     * „Hole, wenn EINER der beiden es braucht" kostet keinen zusaetzlichen
     * Abruf: beide Punkte liegen unter 1 km von den Kandidaten-Koordinaten,
     * loesen also zur selben Diyanet-Standort-ID auf, und ein Abruf mit den
     * Kandidaten-Koordinaten legt einen Eintrag ab, der zu BEIDEN passt. Ein
     * Abruf bedient damit beide.
     *
     * Er verdraengt danach niemanden mehr — aber NICHT, weil nach dem ersten
     * Versuch ein Eintrag an den Kandidaten-Koordinaten laege: bei einem
     * FEHLVERSUCH tut er das gerade nicht, `recordAttempt` findet ueber
     * `indexOf` den vorhandenen Eintrag und aendert diesen an SEINEN
     * Koordinaten. Was bremst, ist das getrennt verschmolzene Protokoll: der
     * Kandidat erbt den Fehlversuch, bleibt ohne Abdeckung, ist damit
     * `hopeless` und sortiert nach hinten.
     *
     * Ist er der EINZIGE Kandidat, wird er trotzdem bei jedem Ausloeser
     * versucht — hinten sortieren hilft nicht, wenn niemand vor ihm steht.
     * Das ist das allgemeine `hopeless`-Verhalten und bewusst so: ~6
     * aussichtslose Versuche am Tag sind der Preis dafuer, einen Ort nicht
     * dauerhaft aufzugeben. Gedeckelt ist die Zahl nicht.
     *
     * Gelingt der Abruf, legt `putAll`
     * den Eintrag tatsaechlich an den Kandidaten-Koordinaten ab und er passt
     * zu beiden Punkten — der Fall heilt sich also selbst.
     */
    fun dueOrder(
        entries: List<RawEntry>,
        pinnedCoords: List<Pair<Double, Double>>,
        activeCoords: Pair<Double, Double>?,
        today: LocalDate,
    ): List<DueLocation> {
        // Der Gleichstands-Vorrang haengt NICHT an der Aufbaureihenfolge: die
        // Favoriten kommen zuerst in die Liste, der aktive Ort danach.
        // `activeRank`/`pinnedRank` halten den Vorrang fest, damit die
        // Sortierung nicht von der Stabilitaet abhaengt.
        val candidates = mutableListOf<Candidate>()

        // Die Nachschlaege kommen von aussen herein, statt hier gemacht zu
        // werden: der verschmolzene Kandidat verschmilzt ZWEI von ihnen
        // (siehe unten).
        fun candidateAt(
            lat: Double,
            lng: Double,
            pinned: Boolean,
            activeRank: Int,
            pinnedRank: Int,
            lookups: List<RawEntry?>,
        ): Candidate {
            val location = dueLocationOf(lat, lng, pinned, lookups)
            return Candidate(
                location = location,
                // Restabdeckung in Tagen; null = keine Zeiten, also unendlich
                // dringend (kein Eintrag, oder ein Eintrag mit leerem
                // Zeitplan).
                remainingDays = location.coveredUntil?.let { ChronoUnit.DAYS.between(today, it) },
                activeRank = activeRank,
                pinnedRank = pinnedRank,
            )
        }

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
            candidates.add(
                candidateAt(
                    pLat,
                    pLng,
                    pinned = true,
                    activeRank = 1,
                    pinnedRank = index,
                    lookups = listOf(select(entries, pLat, pLng)),
                ),
            )
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
                // Die Koordinaten sind die aktiven, die Kopffelder aber
                // feldweise aus BEIDEN Nachschlaegen. Der Nachschlag mit den
                // aktiven Koordinaten allein wuerde den Favoriten als
                // versorgt melden, obwohl DESSEN Zeiten fehlen — die beiden
                // Punkte koennen 1,81 km auseinanderliegen.
                val merged = candidates[matchIndex]
                candidates[matchIndex] = candidateAt(
                    aLat,
                    aLng,
                    pinned = true,
                    activeRank = 0,
                    pinnedRank = merged.pinnedRank,
                    lookups = listOf(
                        select(entries, merged.location.latitude, merged.location.longitude),
                        activeEntry,
                    ),
                )
            } else {
                candidates.add(
                    candidateAt(
                        aLat,
                        aLng,
                        pinned = false,
                        activeRank = 0,
                        // Hinter allen Favoriten — der Rang wird hier nie
                        // entscheidend, `activeRank` hat vorher schon
                        // entschieden. Ein fester Wert haelt ihn trotzdem
                        // deterministisch.
                        pinnedRank = pinnedCoords.size,
                        lookups = listOf(activeEntry),
                    ),
                )
            }
        }

        return candidates
            .sortedWith(
                compareBy<Candidate> { if (it.location.hopeless) 1 else 0 }
                    .thenBy(nullsFirst<Long>()) { it.remainingDays }
                    // Schluessel 3 gilt NUR fuer nicht hoffnungslose
                    // Kandidaten. Innerhalb der hoffnungslosen Gruppe wird
                    // rotiert (Schluessel 4) — sonst haette der Vorrang des
                    // aktiven Orts denselben Aushungerungs-Fehler mit
                    // vertauschten Rollen: ein dauerhaft nicht aufloesbarer
                    // aktiver Ort belegte jeden Ausloeser, und Favoriten, die
                    // inzwischen abrufbar waeren, kaemen NIE wieder dran (33
                    // von 33 Ausloesern gemessen). Der Bildschirm gewinnt also
                    // gegen alles, was gehen KOENNTE, aber nicht gegen andere,
                    // die nachweislich genauso nicht gehen.
                    .thenBy { if (it.location.hopeless) 0 else it.activeRank }
                    .thenBy(nullsFirst<Long>()) { it.location.lastAttemptEpochMs }
                    .thenBy { it.pinnedRank },
            )
            .map { it.location }
    }

    /** Die Kopffelder eines Kandidaten aus einem oder ZWEI
     *  Eintragsnachschlaegen. Abdeckung und Versuchsprotokoll verschmelzen
     *  GETRENNT — Begruendung und Regeln in [dueOrder]. */
    private fun dueLocationOf(
        lat: Double,
        lng: Double,
        pinned: Boolean,
        lookups: List<RawEntry?>,
    ): DueLocation {
        // Abdeckung: die kleinere, `null` gewinnt. Ein fehlender Nachschlag
        // und ein Eintrag mit leerem Zeitplan sind hier dasselbe — beide
        // heissen „fuer diesen Punkt gibt es keine Zeiten".
        val coveredUntil = if (lookups.any { it?.header?.lastDate == null }) {
            null
        } else {
            lookups.mapNotNull { it?.header?.lastDate }.minOrNull()
        }
        // Protokoll: der JUENGERE Versuch, und `lastError` aus DEMSELBEN Kopf.
        // Gemischt wuerde die Statuszeile spaeter einen Fehler zu einem
        // Zeitpunkt behaupten, an dem er nicht auftrat. Ein Kopf ohne
        // Versuchsstempel verliert (`nullsFirst`), ein fehlender Nachschlag
        // traegt gar nichts bei; bei Gleichstand bleibt der erste.
        val log = lookups.filterNotNull()
            .map { it.header }
            .maxWithOrNull(compareBy<CacheHeader, Long?>(nullsFirst()) { it.lastAttemptEpochMs })
        return DueLocation(
            latitude = lat,
            longitude = lng,
            pinned = pinned,
            coveredUntil = coveredUntil,
            lastAttemptEpochMs = log?.lastAttemptEpochMs,
            lastError = log?.lastError,
        )
    }

    private data class Candidate(
        val location: DueLocation,
        val remainingDays: Long?,
        val activeRank: Int,
        val pinnedRank: Int,
    )

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
     *  verwerfen). Genau davon lebt das neunte Feld (`verification`): ein
     *  Cache aus einer aelteren App-Version hat es nicht und liest sich
     *  trotzdem weiter, und ein neu geschriebener Cache bleibt fuer eine
     *  aeltere App-Version lesbar. Nur die ersten sechs Felder (bis
     *  `updatedEpochMs`) sind Pflicht. */
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
                // Eigenes try/catch (in [parseVerification]): ein unlesbares
                // Prueferzeugnis darf NUR dieses Feld verlieren, nicht den
                // ganzen Eintrag — dieselbe Toleranz wie ueberall sonst in
                // dieser Datei. Stuende der Aufruf ungeschuetzt hier, naehme
                // das aeussere catch den Zeitplan gleich mit.
                verification = parts.getOrNull(8)?.orNullMarker()?.let { parseVerification(it) },
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Das Prueferzeugnis in EIN Feld, Unterfelder mit [SUB_SEP], die
     *  Quellenliste mit [LIST_SEP]. Eine leere Liste ergibt ein leeres
     *  Unterfeld — nicht den Null-Marker: „keine bestaetigende Quelle" ist
     *  ein Wert, kein fehlendes Feld. */
    private fun formatVerification(v: Verification): String = listOf(
        v.note.name,
        v.chosen?.name ?: NULL_MARKER,
        v.confirmedBy.joinToString(LIST_SEP) { it.name },
        v.comparedDays.toString(),
        v.differingDays.toString(),
        v.maxAbsMinutes.toString(),
        v.firstDiff?.toString() ?: NULL_MARKER,
        v.checkedEpochMs.toString(),
    ).joinToString(SUB_SEP)

    /** Gegenstueck zu [formatVerification]. `null` bei allem, was sich nicht
     *  lesen laesst — der Aufrufer behaelt dann den Rest des Eintrags.
     *  Vorwaertskompatibel wie der Kopf selbst: zusaetzliche Unterfelder am
     *  Ende werden ignoriert. */
    private fun parseVerification(field: String): Verification? {
        val parts = field.split(SUB_SEP)
        if (parts.size < 8) return null
        return try {
            Verification(
                note = VerificationNote.valueOf(parts[0]),
                chosen = parts[1].orNullMarker()?.let { SourceId.valueOf(it) },
                confirmedBy = parts[2].split(LIST_SEP)
                    .filter { it.isNotEmpty() }
                    .map { SourceId.valueOf(it) },
                comparedDays = parts[3].toInt(),
                differingDays = parts[4].toInt(),
                maxAbsMinutes = parts[5].toInt(),
                firstDiff = parts[6].orNullMarker()?.let { LocalDate.parse(it) },
                checkedEpochMs = parts[7].toLong(),
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
        // Zeilenumbruch wuerde eine zweite "Kopfzeile" vortaeuschen. Diese
        // Bereinigung ist TRAGEND, nicht theoretisch: seit Task 12 steht in
        // `lastError` die Fehlerzusammenfassung des Abrufs, und deren
        // Bausteine fallen auf `e.message` zurueck — fremder Text aus einer
        // Netzbibliothek, bis 80 Zeichen je Quelle. Was dort steht, wissen
        // wir nicht; dass es die Zeile nicht sprengt, sichert erst diese
        // Ersetzung.
        val error = header.lastError?.replace(FIELD_SEP, " ")?.replace("\n", " ")?.replace("\r", " ") ?: NULL_MARKER
        val verification = header.verification?.let { formatVerification(it) } ?: NULL_MARKER
        return "#${header.latitude}$FIELD_SEP${header.longitude}$FIELD_SEP$locationId$FIELD_SEP" +
            "$firstDate$FIELD_SEP$lastDate$FIELD_SEP${header.updatedEpochMs}$FIELD_SEP$lastAttempt$FIELD_SEP" +
            "$error$FIELD_SEP$verification"
    }

    private fun String.orNullMarker(): String? = takeIf { it != NULL_MARKER }
}
