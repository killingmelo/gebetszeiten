package de.gebetszeiten.core.prayertimes.officialtimes

import java.time.LocalDate

/**
 * Wie gut die ausgelieferten Zeiten belegt sind. [NONE] = gar keine Quelle
 * hat geliefert; dann gibt es auch keine Zeiten.
 *
 * [GAP_FILLED] ist bewusst KEINE Abstufung von [VERIFIED]: es sagt nichts
 * ueber den Gewinner, sondern darueber, dass Tage AUSSERHALB seines Fensters
 * aus zwei einander bestaetigenden Kontrollquellen stammen.
 */
enum class VerificationNote {
    VERIFIED, DRIFT, GAP_FILLED, UNVERIFIED_SINGLE, CONFLICT_OVERRIDDEN, CONFLICT_UNRESOLVED, NONE
}

/**
 * Das Prueferzeugnis zu einem Zeitplan — reiner Datentyp, ohne Nutzertext.
 * Die deutschen Worte dazu stehen in `VerificationText` im app-Modul; `core`
 * bleibt frei von Oberflaechen-Sprache.
 *
 * Die Zahlen stammen aus GENAU EINEM Vergleich, naemlich dem, der [note]
 * begruendet (siehe [resolveQuorum]) — nicht gemischt aus mehreren, sonst
 * behauptete die Anzeige eine Abweichung von 14 Minuten am Tag eines
 * anderen, milderen Vergleichs.
 */
data class Verification(
    val note: VerificationNote,
    val chosen: SourceId?,
    /** Die Quellen, die den Gewinner BESTAETIGEN — der Gewinner selbst ist
     *  nie darunter. „bestaetigt durch 1 Quelle" heisst also: eine ANDERE
     *  Quelle sagt dasselbe. */
    val confirmedBy: List<SourceId>,
    val comparedDays: Int,
    val differingDays: Int,
    val maxAbsMinutes: Int,
    val firstDiff: LocalDate?,
    val checkedEpochMs: Long,
)

data class QuorumOutcome(
    val schedule: Map<LocalDate, SixTimes>,
    /** null NUR bei [VerificationNote.NONE] — dann gibt es keine Zeiten, zu
     *  denen eine Standort-ID gehoeren koennte. */
    val locationId: Int?,
    val verification: Verification,
)

/** Zahlen fuer eine Note, die auf KEINEM Vergleich beruht
 *  ([VerificationNote.UNVERIFIED_SINGLE], [VerificationNote.NONE]). Nur
 *  Traeger der Nullen — sein `verdict` beschreibt keinen Vergleich und
 *  entscheidet nichts. */
private val NO_NUMBERS = CrossCheckResult(Verdict.NO_OVERLAP, 0, 0, 0, null)

/** Zwischenergebnis: wer gewinnt, mit welcher Note, mit welchen Zahlen. Der
 *  Zeitplan selbst wird erst danach vereinigt. */
private data class Decision(
    val winner: SourceResult?,
    val note: VerificationNote,
    val numbers: CrossCheckResult,
    val confirmedBy: List<SourceId>,
)

/**
 * Aus mehreren Quellenabrufen EINEN Zeitplan samt Prueferzeugnis machen.
 *
 * **Zuerst wird dedupliziert:** jeder [SourceId] zaehlt genau einmal. Ohne
 * das bestaetigte derselbe Eintrag, zweimal in der Liste, sich selbst — und
 * [Verification.confirmedBy] enthielte den Gewinner. Unter mehreren
 * Eintraegen derselben Quelle gewinnt einer, der ETWAS GELIEFERT hat; bei
 * gleichem Rang der erste. Ein leerer Fehlereintrag darf einen erfolgreichen
 * Abruf derselben Quelle nicht verdecken — sonst wuerfe die App einen ganzen
 * Jahresplan weg und naehme den 31-Tage-Stand, ohne dass es jemand erfaehrt.
 *
 * `direct` ist der Kandidat mit `source == DIRECT` und NICHT leerem
 * Zeitplan, sonst null: ein gescheiterter Direktabruf ist kein Zeuge. Alle
 * uebrigen Kandidaten mit nicht leerem Zeitplan sind die „Pruefer".
 *
 * **Fall A — der Direktabruf hat geliefert.** Er wird gegen jeden Pruefer
 * verglichen und nach [Verdict] gruppiert:
 *
 * | Lage | Gewinner | Note |
 * |---|---|---|
 * | mindestens ein [Verdict.AGREE] | direct (volles Fenster) | [VerificationNote.VERIFIED] |
 * | kein AGREE, aber mindestens ein [Verdict.MINOR_DRIFT] | direct | [VerificationNote.DRIFT] |
 * | kein AGREE/MINOR_DRIFT, aber >= 2 Pruefer im [Verdict.CONFLICT] mit direct, die untereinander einig sind UND deren Widerspruch je SYSTEMATISCH ist | die Pruefer | [VerificationNote.CONFLICT_OVERRIDDEN] |
 * | mindestens ein CONFLICT, aber die Pruefer sind nicht untereinander einig (oder es ist nur einer, oder der Widerspruch ist nicht systematisch) | direct | [VerificationNote.CONFLICT_UNRESOLVED] |
 * | gar kein beurteilbarer Pruefer (nur [Verdict.NO_OVERLAP]), aber zwei solche Pruefer sind UNTEREINANDER einig | direct, aufgefuellt aus ihnen | [VerificationNote.GAP_FILLED] |
 * | gar kein beurteilbarer Pruefer (keiner antwortete, oder nur [Verdict.NO_OVERLAP] ohne solches Paar) | direct | [VerificationNote.UNVERIFIED_SINGLE] |
 *
 * „Untereinander einig" heisst: der Vergleich zwischen den beiden Pruefern
 * liefert AGREE ODER MINOR_DRIFT — also nicht CONFLICT und nicht
 * NO_OVERLAP. Ohne Schnittmenge belegen zwei Quellen einander nicht.
 *
 * **„Systematisch" heisst:** der Vergleich des Pruefers MIT DIRECT erfuellt
 * BEIDES —
 * - `comparedDays >= minConflictDays`: das Prueffenster ist ueberhaupt
 *   aussagekraeftig; ein Zwei-Tage-Ueberlapp darf kein Jahr kippen;
 * - `differingDays * 2 >= comparedDays`: mindestens die HAELFTE der
 *   verglichenen Tage weicht ab.
 *
 * Gefordert wird das von BEIDEN Pruefern des Paares, auf das sich das
 * Ueberstimmen stuetzt — nicht nur von einem.
 *
 * **Warum zwei einige Fremdquellen unseren Jahres-Parser schlagen, aber nur
 * bei systematischem Widerspruch** (Nutzerentscheidung): Zwei unabhaengig
 * implementierte Quellen, die dasselbe sagen und beide unserem HTML-Parser
 * widersprechen, sind der wahrscheinlichere Zeuge als der Parser allein.
 *
 * Das gilt aber nur fuer die Form „der Parser liegt daneben", und die sieht
 * anders aus als „ein Tag wurde korrigiert". Ein kaputter Parser weicht an
 * ALLEN Tagen ab, eine echte Diyanet-Korrektur an EINEM. Zwei Gruende, die
 * feine Ausloesung teuer machen:
 * - Die beiden Kontrollquellen sind moeglicherweise KORRELIERT: beide
 *   spiegeln denselben Diyanet-Upstream. Ein Fehler dort — oder zwei
 *   veraltete Zwischenspeicher — erschienen dem Quorum als zwei einige
 *   Zeugen.
 * - Der Preis des Ueberstimmens ist Datenverlust, nicht nur eine Anzeige:
 *   die Abdeckung faellt von ~400 auf ~31 Tage. Fuer Orte AUSSERHALB
 *   Deutschlands gibt es keine gebuendelte Reserve; dort sieht der Nutzer
 *   nach dem Fenster eine BERECHNUNG statt amtlicher Zeiten.
 *
 * Ist der Widerspruch nicht systematisch, faellt die Entscheidung auf
 * [VerificationNote.CONFLICT_UNRESOLVED] durch: der Direktabruf behaelt sein
 * volles Fenster, und die Statuszeile meldet den Widerspruch trotzdem. Der
 * Nutzer verliert also keine Abdeckung und erfaehrt dennoch davon.
 *
 * **Fall B — der Direktabruf hat nicht geliefert.** Gewinner ist immer der
 * Pruefer mit mehr Tagen:
 *
 * | Lage | Note |
 * |---|---|
 * | >= 2 Pruefer, untereinander einig OHNE Abweichung (AGREE) | [VerificationNote.VERIFIED] |
 * | >= 2 Pruefer, untereinander einig MIT kleiner Abweichung (MINOR_DRIFT) | [VerificationNote.DRIFT] |
 * | >= 2 Pruefer, uneinig | [VerificationNote.CONFLICT_UNRESOLVED] |
 * | genau 1 Pruefer | [VerificationNote.UNVERIFIED_SINGLE] |
 * | keiner | [VerificationNote.NONE], leerer Zeitplan, `locationId` null |
 *
 * „Einig" umfasst in Fall B auch MINOR_DRIFT — der Gewinner bleibt also der
 * Pruefer mit mehr Tagen, und die driftende Quelle steht in `confirmedBy`.
 * Die NOTE aber kommt aus dem Verdikt desjenigen Vergleichs, der die Zahlen
 * stellt: driftet er, ist die Note [VerificationNote.DRIFT] und nicht
 * VERIFIED. Sonst behauptete die Statuszeile „bestaetigt", waehrend die
 * [Verification] daneben `differingDays = 2` traegt.
 *
 * **Welcher Pruefer gewinnt:** der mit MEHR Tagen; bei Gleichstand der mit
 * dem kleineren `SourceId.ordinal`. Auch die Vergleiche werden in
 * `SourceId`-Reihenfolge gebildet, damit bei gleichen Zahlen immer derselbe
 * Vergleich die Zahlen stellt — das Ergebnis ist vollstaendig
 * deterministisch, unabhaengig davon, in welcher Reihenfolge die Abrufe
 * fertig wurden.
 *
 * **Vereinigung der Tage:** Tage, die dem Gewinner FEHLEN, werden aus
 * Quellen ergaenzt, deren Vergleich mit dem Gewinner AGREE oder
 * MINOR_DRIFT ergab. Bei Ueberschneidung gewinnt immer der Gewinner. Aus
 * CONFLICT-Quellen wird NIE ergaenzt — sonst mischte die App genau die
 * Zeiten hinein, die sie gerade als widersprechend erkannt hat.
 *
 * Das heisst ausdruecklich NICHT, dass jeder eingemischte Tag geprueft
 * waere: ein Spender mit teilweiser Ueberschneidung steuert auch seine
 * RANDtage bei, die ausserhalb des Prueffensters liegen (real ~20 von 51
 * Tagen). Geprueft ist der SPENDER, nicht jeder seiner Tage.
 *
 * **Quellen ohne jede Schnittmenge** (NO_OVERLAP) spenden nur unter einer
 * zusaetzlichen Bedingung: wenn eine ANDERE Quelle, die ebenfalls nicht
 * der Gewinner ist, ihnen zustimmt (siehe [gapFill]). Der Grundsatz bleibt
 * damit derselbe — keine einzelne ungepruefte Quelle liefert Gebetszeiten
 * —, nur der Zeuge ist ein anderer: nicht der Gewinner, sondern die
 * zweite Kontrollquelle.
 *
 * Diese Regel ist nicht theoretisch. Am 11.09.2026 auf dem Geraet
 * gemessen: die Jahresseite von Diyanet lieferte nur noch das FOLGEjahr,
 * die beiden Kontrollquellen 31 Tage ab heute. Ohne sie fiel jeder Tag der
 * Gegenwart weg — die App hielt die amtlichen Zeiten im Speicher und
 * zeigte berechnete an. Ohne Bedingung waere es der gegenteilige Fehler
 * gewesen: eine einzelne, von niemandem gegengepruefte Quelle haette
 * Gebetszeiten bestimmt.
 *
 * Das Fuellen aendert die Note nur dann, wenn sonst nichts zu sagen waere
 * ([VerificationNote.UNVERIFIED_SINGLE] wird zu
 * [VerificationNote.GAP_FILLED]). Hat ein Pruefer den Gewinner wirklich
 * beurteilt, bleibt SEIN Urteil die Nachricht.
 *
 * **„Zustimmend" heisst an zwei Stellen VERSCHIEDENES** — beides ist
 * einzeln vertretbar, darum wird der Unterschied hier benannt statt mit
 * demselben Wort verdeckt:
 * - fuer [Verification.confirmedBy] bei [VerificationNote.VERIFIED] in
 *   Fall A nur AGREE — „bestaetigt durch N Quellen" zaehlt nur Quellen,
 *   die genau dasselbe sagen;
 * - fuer die Vereinigung der Tage und fuer „einig" (in beiden Faellen)
 *   AGREE ODER MINOR_DRIFT.
 *
 * Folge, die man kennen muss: eine um eine Minute driftende Quelle steuert
 * in Fall A Tage zum ausgelieferten Zeitplan bei, wird in „bestaetigt durch
 * N Quellen" aber nicht mitgezaehlt. In Fall B steht sie in `confirmedBy` —
 * dort ist der Gewinner selbst keine besser gepruefte Quelle als sie, und
 * die Note heisst dann DRIFT statt VERIFIED.
 *
 * **Welche Zahlen in [Verification] stehen:** die des Vergleichs, der die
 * Note BEGRUENDET — in Fall A bei VERIFIED der zustimmende Vergleich mit
 * den meisten `comparedDays` und bei DRIFT der Drift-Vergleich mit den
 * meisten `differingDays`; in Fall B der einige Vergleich mit den meisten
 * `comparedDays`, dessen Verdikt zugleich die Note entscheidet; bei beiden
 * Konflikt-Noten der Vergleich mit dem groessten `maxAbsMinutes` (die
 * Anzeige soll den schlimmsten Widerspruch nennen, nicht den mildesten);
 * bei UNVERIFIED_SINGLE und NONE Nullen.
 *
 * [minConflictDays] ist die kleinste Fenstergroesse, ab der ein Widerspruch
 * ueberhaupt systematisch heissen darf. Der Vorgabewert 7 ist nicht
 * irgendeine Zahl ueber [maxDriftDays], sondern genau `2 * maxDriftDays + 1`
 * — der kleinste Wert, fuer den die Haelfte-Bedingung mehr Tage verlangt,
 * als die Drift-Schwelle durchgehen laesst. Bei 6 waeren „3 von 6
 * abweichend" systematisch, obwohl drei abweichende Tage der Zahl nach noch
 * INNERHALB der Drift-Toleranz liegen — dieselbe Lage waere je nach
 * Fenstergroesse einmal „moegliche Korrektur" und einmal „kaputter Parser".
 *
 * Die beiden Vorgabewerte haengen also zusammen, ohne dass der Code sie
 * koppeln koennte (sie sind Parameter zweier verschiedener Funktionen). Ein
 * Test in `SourceQuorumTest` haelt die Beziehung fest: wer [maxDriftDays]
 * anhebt, ohne [minConflictDays] nachzuziehen, bekommt ihn rot.
 *
 * [nowEpochMs] wird durchgereicht, nicht aus der Systemuhr geholt: reine
 * Funktion, ohne Netz und ohne Uhr.
 */
fun resolveQuorum(
    candidates: List<SourceResult>,
    locationId: Int,
    nowEpochMs: Long,
    maxDriftDays: Int = 3,
    maxDriftMinutes: Int = 2,
    minConflictDays: Int = 7,
): QuorumOutcome {
    // ZUERST deduplizieren: ein `SourceId` zaehlt genau einmal. Steht
    // derselbe Eintrag zweimal in der Liste, wuerde er sich sonst selbst
    // bestaetigen — „bestaetigt durch 1 Quelle", obwohl nur EINE Quelle
    // geantwortet hat — und damit die Zusicherung von
    // [Verification.confirmedBy] brechen. Heute kann das nicht vorkommen;
    // Task 11 setzt die Kandidatenliste aber nebenlaeufig zusammen, und dort
    // waere es eine Falle.
    //
    // Unter mehreren Eintraegen derselben Quelle gewinnt einer, der ETWAS
    // GELIEFERT hat — nicht schlicht der erste. Ein leerer Fehlereintrag, der
    // zufaellig vorne steht, wuerde sonst einen erfolgreichen Abruf derselben
    // Quelle verdecken und die Quelle als gescheitert fuehren. Bei gleichem
    // Rang bleibt die Eingabereihenfolge (`sortedBy` ist stabil).
    val distinct = candidates
        .sortedBy { if (it.schedule.isEmpty()) 1 else 0 }
        .distinctBy { it.source }
    val direct = distinct.firstOrNull { it.source == SourceId.DIRECT && it.schedule.isNotEmpty() }
    // Identitaetsvergleich: „alle ANDEREN Kandidaten". Nach der
    // Deduplizierung gibt es hoechstens einen DIRECT-Eintrag, der Vergleich
    // trifft also genau ihn.
    val checkers = distinct.filter { it !== direct && it.schedule.isNotEmpty() }
        .sortedBy { it.source.ordinal }

    val decision = if (direct != null) {
        decideWithDirect(direct, checkers, maxDriftDays, maxDriftMinutes, minConflictDays)
    } else {
        decideWithoutDirect(checkers, maxDriftDays, maxDriftMinutes)
    }

    val winner = decision.winner
    val gap = winner?.let { gapFill(it, distinct, maxDriftDays, maxDriftMinutes) }
    // Die Note wird NUR ersetzt, wenn sonst nichts zu sagen waere. Hat ein
    // Pruefer den Gewinner tatsaechlich beurteilt, ist SEIN Urteil die
    // Nachricht — das Fuellen eines Randfensters darf es nicht verdecken.
    //
    // EHRLICH GESAGT: mit den heutigen DREI `SourceId`s ist die zweite
    // Bedingung nicht erreichbar und daher von keinem Test gedeckt. Fuellen
    // braucht ZWEI Quellen ohne Schnittmenge zum Gewinner; neben DIRECT gibt
    // es aber nur zwei Pruefer, und sind beide ohne Schnittmenge, ist die
    // Note zwangslaeufig UNVERIFIED_SINGLE. Sie steht hier fuer eine vierte
    // Quelle — dieselbe Vorsorge wie in [firstAgreeingPair], das paarweise
    // sucht, obwohl heute nur ein Paar moeglich ist.
    val gapDecidesNote = gap != null && decision.note == VerificationNote.UNVERIFIED_SINGLE
    val numbers = if (gapDecidesNote) gap.second else decision.numbers
    return QuorumOutcome(
        schedule = winner?.let {
            unionSchedule(it, distinct, gap?.first.orEmpty(), maxDriftDays, maxDriftMinutes)
        } ?: emptyMap(),
        // Nur bei NONE (kein Gewinner) gibt es keine Standort-ID.
        locationId = winner?.let { locationId },
        verification = Verification(
            note = if (gapDecidesNote) VerificationNote.GAP_FILLED else decision.note,
            chosen = winner?.source,
            confirmedBy = decision.confirmedBy,
            comparedDays = numbers.comparedDays,
            differingDays = numbers.differingDays,
            maxAbsMinutes = numbers.maxAbsMinutes,
            firstDiff = numbers.firstDiff,
            checkedEpochMs = nowEpochMs,
        ),
    )
}

/** Fall A. Reihenfolge der Abfragen = Reihenfolge der Tabellenzeilen im
 *  KDoc von [resolveQuorum]. */
private fun decideWithDirect(
    direct: SourceResult,
    checkers: List<SourceResult>,
    maxDriftDays: Int,
    maxDriftMinutes: Int,
    minConflictDays: Int,
): Decision {
    val checks = checkers.map { it to crossCheck(direct.schedule, it.schedule, maxDriftDays, maxDriftMinutes) }
    fun withVerdict(verdict: Verdict) = checks.filter { it.second.verdict == verdict }

    val agreeing = withVerdict(Verdict.AGREE)
    if (agreeing.isNotEmpty()) {
        return Decision(
            winner = direct,
            note = VerificationNote.VERIFIED,
            numbers = pick(agreeing) { it.comparedDays },
            confirmedBy = sourcesOf(agreeing),
        )
    }

    val drifting = withVerdict(Verdict.MINOR_DRIFT)
    if (drifting.isNotEmpty()) {
        return Decision(
            winner = direct,
            note = VerificationNote.DRIFT,
            numbers = pick(drifting) { it.differingDays },
            confirmedBy = sourcesOf(drifting),
        )
    }

    val conflicting = withVerdict(Verdict.CONFLICT)
    if (conflicting.isNotEmpty()) {
        // Der schlimmste Widerspruch stellt in BEIDEN Konflikt-Faellen die
        // Zahlen — auch wenn er selbst nicht systematisch ist: die Anzeige
        // soll den groessten gemessenen Widerspruch nennen.
        val worst = pick(conflicting) { it.maxAbsMinutes }
        // Ueberstimmen darf nur ein SYSTEMATISCHER Widerspruch, und zwar von
        // BEIDEN Pruefern des Paares — deshalb wird hier gefiltert, bevor das
        // Paar ueberhaupt gesucht wird. Begruendung im KDoc von
        // [resolveQuorum].
        val systematic = conflicting.filter { isSystematic(it.second, minConflictDays) }
        val agreeingPair = firstAgreeingPair(systematic.map { it.first }, maxDriftDays, maxDriftMinutes)
        if (agreeingPair != null) {
            return Decision(
                // Mehr Tage gewinnt; bei Gleichstand der kleinere ordinal.
                winner = bestByDays(agreeingPair),
                note = VerificationNote.CONFLICT_OVERRIDDEN,
                numbers = worst,
                confirmedBy = agreeingPair.map { it.source }.sortedBy { it.ordinal },
            )
        }
        return Decision(direct, VerificationNote.CONFLICT_UNRESOLVED, worst, emptyList())
    }

    // Kein beurteilbarer Pruefer: keiner antwortete, oder alle ohne
    // Schnittmenge.
    return Decision(direct, VerificationNote.UNVERIFIED_SINGLE, NO_NUMBERS, emptyList())
}

/** Fall B. */
private fun decideWithoutDirect(
    checkers: List<SourceResult>,
    maxDriftDays: Int,
    maxDriftMinutes: Int,
): Decision {
    if (checkers.isEmpty()) {
        return Decision(null, VerificationNote.NONE, NO_NUMBERS, emptyList())
    }
    val winner = bestByDays(checkers)
    if (checkers.size == 1) {
        return Decision(winner, VerificationNote.UNVERIFIED_SINGLE, NO_NUMBERS, emptyList())
    }

    // Gemessen wird gegen den GEWINNER, nicht paarweise ueber alle: belegt
    // werden muessen die Zeiten, die tatsaechlich ausgeliefert werden. Das
    // ist keine Vereinfachung, sondern eine Folge der Deduplizierung in
    // [resolveQuorum]: es gibt drei `SourceId`s, einer davon ist DIRECT,
    // also hoechstens zwei Pruefer — gegen den Gewinner zu messen ist damit
    // dasselbe wie paarweise.
    val checks = checkers.filter { it !== winner }
        .map { it to crossCheck(winner.schedule, it.schedule, maxDriftDays, maxDriftMinutes) }
    val concordant = checks.filter {
        it.second.verdict == Verdict.AGREE || it.second.verdict == Verdict.MINOR_DRIFT
    }
    if (concordant.isNotEmpty()) {
        val numbers = pick(concordant) { it.comparedDays }
        return Decision(
            winner = winner,
            // Die Note kommt aus dem VERDIKT desselben Vergleichs, der die
            // Zahlen stellt: driftet er, ist „bestaetigt" eine
            // Ueberbehauptung — die Verification traegt die abweichenden
            // Tage ja mit sich.
            note = when (numbers.verdict) {
                Verdict.MINOR_DRIFT -> VerificationNote.DRIFT
                else -> VerificationNote.VERIFIED
            },
            numbers = numbers,
            confirmedBy = sourcesOf(concordant),
        )
    }
    // Uneinig — auch dann, wenn die Pruefer sich nur nicht ueberschneiden:
    // ohne gemeinsame Tage bestaetigt keiner den anderen.
    return Decision(
        winner = winner,
        note = VerificationNote.CONFLICT_UNRESOLVED,
        numbers = pick(checks) { it.maxAbsMinutes },
        confirmedBy = emptyList(),
    )
}

/**
 * Hat dieser Konflikt die Form „der Parser liegt daneben" statt „ein Tag
 * wurde korrigiert"? Beide Bedingungen muessen gelten, und beide fallen
 * EINSCHLIESSLICH aus: genau [minConflictDays] verglichene Tage genuegen
 * noch, und genau die Haelfte abweichender Tage genuegt auch.
 *
 * `differingDays * 2 >= comparedDays` statt einer Division: ganzzahlig
 * geteilt waere „die Haelfte von 7" gleich 3, und der Test dazu haenge an
 * einer Rundung statt an der Absicht.
 */
private fun isSystematic(result: CrossCheckResult, minConflictDays: Int): Boolean =
    result.comparedDays >= minConflictDays && result.differingDays * 2 >= result.comparedDays

/** Der Vergleich mit dem groessten [key]. Bei Gleichstand der erste — die
 *  Liste ist nach `SourceId` sortiert, das Ergebnis also deterministisch.
 *
 *  Der Rueckfall auf [NO_NUMBERS] ersetzt nur ein `!!`: alle Aufrufer
 *  pruefen vorher auf „nicht leer", und nach der Deduplizierung in
 *  [resolveQuorum] kann [checks] auch nicht durch `it !== winner`
 *  leerlaufen — dieselbe Instanz steht nicht mehr zweimal in der Liste. */
private fun pick(
    checks: List<Pair<SourceResult, CrossCheckResult>>,
    key: (CrossCheckResult) -> Int,
): CrossCheckResult = checks.map { it.second }.maxByOrNull(key) ?: NO_NUMBERS

private fun sourcesOf(checks: List<Pair<SourceResult, CrossCheckResult>>): List<SourceId> =
    checks.map { it.first.source }.sortedBy { it.ordinal }

/** Mehr Tage gewinnt; bei Gleichstand der kleinere `SourceId.ordinal`
 *  (Determinismus). [sources] ist immer nicht leer. */
private fun bestByDays(sources: List<SourceResult>): SourceResult =
    sources.reduce { best, next ->
        val better = next.schedule.size > best.schedule.size ||
            (next.schedule.size == best.schedule.size && next.source.ordinal < best.source.ordinal)
        if (better) next else best
    }

/** Das erste Paar aus [sources] (in `SourceId`-Reihenfolge), das
 *  untereinander einig ist — AGREE oder MINOR_DRIFT. null, wenn es keines
 *  gibt. Bei genau zwei Fremdquellen ist das der einzige moegliche Fall;
 *  paarweise gesucht wird trotzdem, damit eine dritte Kontrollquelle die
 *  Funktion nicht stillschweigend entwertet. */
private fun firstAgreeingPair(
    sources: List<SourceResult>,
    maxDriftDays: Int,
    maxDriftMinutes: Int,
): List<SourceResult>? {
    val ordered = sources.sortedBy { it.source.ordinal }
    for (i in ordered.indices) {
        for (j in i + 1 until ordered.size) {
            val verdict = crossCheck(
                ordered[i].schedule,
                ordered[j].schedule,
                maxDriftDays,
                maxDriftMinutes,
            ).verdict
            if (verdict == Verdict.AGREE || verdict == Verdict.MINOR_DRIFT) {
                return listOf(ordered[i], ordered[j])
            }
        }
    }
    return null
}

/**
 * Quellen, die den Gewinner NIRGENDS beruehren, sich dafuer aber
 * UNTEREINANDER bestaetigen — samt dem Vergleich, der das belegt.
 *
 * Am 11.09.2026 auf dem Geraet gemessen: die Jahresseite von Diyanet
 * lieferte fuer jeden geprueften Standort nur noch das Folgejahr
 * (01.01.-31.12.2027), die beiden Kontrollquellen 31 Tage ab heute. Ohne
 * diesen Weg fiel damit JEDER Tag der Gegenwart weg, obwohl zwei amtliche
 * Quellen ihn minutengleich lieferten — die App hielt die richtigen Zeiten
 * im Speicher und zeigte berechnete an.
 *
 * `null`, solange weniger als zwei solche Quellen da sind: eine einzelne
 * ungepruefte Quelle darf keine Gebetszeiten beisteuern. Das ist derselbe
 * Grundsatz wie sonst, nur auf ein Fenster angewandt, das der Gewinner
 * ueberhaupt nicht abdeckt.
 */
private fun gapFill(
    winner: SourceResult,
    candidates: List<SourceResult>,
    maxDriftDays: Int,
    maxDriftMinutes: Int,
): Pair<List<SourceResult>, CrossCheckResult>? {
    val strangers = candidates
        .filter { it !== winner && it.schedule.isNotEmpty() }
        .filter {
            crossCheck(winner.schedule, it.schedule, maxDriftDays, maxDriftMinutes)
                .verdict == Verdict.NO_OVERLAP
        }
        .sortedBy { it.source.ordinal }
    val pair = firstAgreeingPair(strangers, maxDriftDays, maxDriftMinutes) ?: return null
    return pair to crossCheck(pair[0].schedule, pair[1].schedule, maxDriftDays, maxDriftMinutes)
}

/** Der Zeitplan des Gewinners, aufgefuellt aus zustimmenden Quellen und aus
 *  [gapDonors]. Regeln und Begruendung in [resolveQuorum]. */
private fun unionSchedule(
    winner: SourceResult,
    candidates: List<SourceResult>,
    gapDonors: List<SourceResult>,
    maxDriftDays: Int,
    maxDriftMinutes: Int,
): Map<LocalDate, SixTimes> {
    val merged = LinkedHashMap<LocalDate, SixTimes>()
    // Spender in `SourceId`-Reihenfolge: ueberschneiden sich zwei Spender an
    // einem Tag, den der Gewinner nicht hat, gewinnt der spaetere — fest,
    // nicht zufaellig.
    for (donor in candidates.sortedBy { it.source.ordinal }) {
        if (donor === winner || donor.schedule.isEmpty()) continue
        val verdict = crossCheck(winner.schedule, donor.schedule, maxDriftDays, maxDriftMinutes).verdict
        val concordant = verdict == Verdict.AGREE || verdict == Verdict.MINOR_DRIFT
        // Identitaetsvergleich wie ueberall hier: [gapDonors] stammt aus
        // derselben Liste.
        if (!concordant && gapDonors.none { it === donor }) continue
        merged.putAll(donor.schedule)
    }
    // Der Gewinner ZULETZT: bei Ueberschneidung gewinnt immer er.
    merged.putAll(winner.schedule)
    return merged
}
