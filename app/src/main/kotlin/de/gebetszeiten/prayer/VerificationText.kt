package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.SourceResult
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote

private const val PREFIX = "Gegenprüfung: "

/**
 * Eine Zeile Klartext zum Prueferzeugnis, zusaetzlich zur bestehenden
 * Statuszeile (siehe [officialStatusText]). Reine Funktion, kein Android,
 * keine Uhr — die deutschen Worte leben hier und nicht in
 * `core-prayertimes`, das ausdruecklich frei von Oberflaechen-Sprache
 * bleibt.
 *
 * `null` bei `null` UND bei [VerificationNote.NONE]: hat gar keine Quelle
 * geliefert, hat die bestehende Statuszeile ihre eigenen Worte fuer „nichts
 * erhalten" (Letzter Abruf/Fehler), und zwei Meldungen fuer dasselbe waeren
 * Ballast.
 *
 * Einzahl und Mehrzahl werden im Code entschieden, nicht einem `%d`-Baustein
 * ueberlassen: „1 Quellen" und „1 von 31 Tagen weichen ab" waeren falsches
 * Deutsch. Betroffen sind die Anzahl der Quellen, die Zahl der verglichenen
 * Tage (Nominativ „Tag"/„Tage") und die Zahl der abweichenden Tage samt dem
 * Verb, das sie regiert („weicht"/„weichen") — dazu der Dativ hinter „von"
 * („von 1 Tag"/„von 31 Tagen").
 */
fun verificationLine(verification: Verification?): String? {
    val v = verification ?: return null
    return when (v.note) {
        VerificationNote.VERIFIED ->
            PREFIX + "bestätigt durch ${sourceCount(v.confirmedBy.size)} " +
                "(${daysNominative(v.comparedDays)} verglichen)"

        VerificationNote.DRIFT ->
            PREFIX + "${v.differingDays} von ${daysDative(v.comparedDays)} " +
                "${differVerb(v.differingDays)} ab, max. ${v.maxAbsMinutes} Min — mögliche Korrektur"

        // „beiden Kontrollquellen" ist keine Annahme, sondern zugesichert:
        // CONFLICT_OVERRIDDEN entsteht nur aus GENAU ZWEI untereinander
        // einigen Pruefern (`resolveQuorum`), `confirmedBy` hat dann zwei
        // Eintraege.
        //
        // KEINE Tageszahl in dieser Zeile: `comparedDays` ist das
        // KONFLIKTFENSTER (die Schnittmenge, die den Widerspruch belegt),
        // nicht die Abdeckung des uebernommenen Stands. Real gemessen: 31
        // verglichene Tage bei 51 ausgelieferten, weil die zweite
        // Kontrollquelle in der Vereinigung Tage ausserhalb des Fensters
        // beisteuert. „Geprüfter 31-Tage-Stand" waere also schlicht falsch.
        // Die [Verification] enthaelt die Vereinigungsgroesse nicht — und
        // soll sie auch nicht enthalten, denn der Satz ist ohne Zahl
        // vollstaendig. Nebenbei erledigt sich damit das falsche Deutsch bei
        // `comparedDays == 1` („1-Tage-Stand").
        VerificationNote.CONFLICT_OVERRIDDEN ->
            PREFIX + "Jahresabruf widersprach beiden Kontrollquellen — " +
                "geprüfter Kontrollstand übernommen, Abdeckung daher kürzer"

        // Ohne gemeinsame Tage steht in der [Verification] `maxAbsMinutes
        // == 0` — „uneinig (max. 0 Min)" wuerde sich selbst widersprechen
        // und als „sie sind sich einig" gelesen. Die Note ist richtig (ohne
        // gemeinsame Tage belegt keine Quelle die andere), der Grund ist nur
        // ein anderer, also sagt die Zeile den Grund. Entstehen kann das in
        // Fall B, wenn zwei Kontrollquellen disjunkte Datumsbereiche haben
        // (Monatswechsel).
        VerificationNote.CONFLICT_UNRESOLVED ->
            if (v.comparedDays == 0) {
                PREFIX + "keine gemeinsamen Tage — Zeiten unbestätigt"
            } else {
                PREFIX + "Quellen uneinig (max. ${v.maxAbsMinutes} Min) — Zeiten unbestätigt"
            }

        VerificationNote.UNVERIFIED_SINGLE ->
            PREFIX + "nicht möglich — nur eine Quelle erreichbar"

        VerificationNote.NONE -> null
    }
}

/**
 * Was schiefging, Quelle fuer Quelle: `"Direktabruf: HTTP 503 · Proxy:
 * Zeitüberschreitung · ezanvakti: HTTP 404"`. In `SourceId`-Reihenfolge;
 * `null`, wenn keiner scheiterte.
 *
 * GESCHEITERT heisst, was `SourceResult` zusichert: leerer Zeitplan UND ein
 * [SourceResult.error]. Wer Zeiten geliefert hat, ist kein Fehlschlag —
 * auch wenn unterwegs etwas schiefging (ein Blatt von zwoelf fehlte etwa);
 * er kann sogar das Quorum gewonnen haben, und ihn dann als Fehler zu
 * melden waere genau die Art Behauptung, die diese Zeile vermeiden soll.
 *
 * Damit kann die Statuszeile sagen, WAS schiefging, statt nur dass etwas
 * schiefging.
 */
fun fetchErrorSummary(candidates: List<SourceResult>): String? =
    candidates.filter { it.schedule.isEmpty() && it.error != null }
        .sortedBy { it.source.ordinal }
        .joinToString(" · ") { "${sourceLabel(it.source)}: ${it.error}" }
        .takeIf { it.isNotEmpty() }

/** Klartextnamen der Quellen — kurz, weil sie in einer Zeile mit ihrem
 *  Fehlergrund stehen. */
private fun sourceLabel(source: SourceId): String = when (source) {
    SourceId.DIRECT -> "Direktabruf"
    SourceId.PROXY_ABDUS -> "Proxy"
    SourceId.EZANVAKTI -> "ezanvakti"
}

private fun sourceCount(count: Int): String = if (count == 1) "1 Quelle" else "$count Quellen"

private fun daysNominative(days: Int): String = if (days == 1) "1 Tag" else "$days Tage"

private fun daysDative(days: Int): String = if (days == 1) "1 Tag" else "$days Tagen"

private fun differVerb(days: Int): String = if (days == 1) "weicht" else "weichen"
