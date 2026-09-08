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
        // Die genannte Tageszahl ist `comparedDays`, also die Groesse der
        // Schnittmenge, die den Widerspruch belegt. Das ist die einzige
        // Tageszahl, die in der [Verification] steht; die Abdeckung des
        // uebernommenen Stands kann nach der Vereinigung mit der zweiten
        // Kontrollquelle groesser sein (~31 gegen ~35 Tage). Die Aussage
        // „Abdeckung daher kuerzer" bleibt in jedem Fall richtig — kuerzer
        // als der verworfene Jahresabruf ist beides.
        VerificationNote.CONFLICT_OVERRIDDEN ->
            PREFIX + "Jahresabruf widersprach beiden Kontrollquellen — " +
                "geprüfter ${v.comparedDays}-Tage-Stand übernommen, Abdeckung daher kürzer"

        VerificationNote.CONFLICT_UNRESOLVED ->
            PREFIX + "Quellen uneinig (max. ${v.maxAbsMinutes} Min) — Zeiten unbestätigt"

        VerificationNote.UNVERIFIED_SINGLE ->
            PREFIX + "nicht möglich — nur eine Quelle erreichbar"

        VerificationNote.NONE -> null
    }
}

/**
 * Was schiefging, Quelle fuer Quelle: `"Direktabruf: HTTP 503 · Proxy:
 * Zeitüberschreitung · ezanvakti: HTTP 404"`. Nur die Kandidaten mit
 * `error != null`, in `SourceId`-Reihenfolge; `null`, wenn keiner scheiterte.
 *
 * Damit kann die Statuszeile sagen, WAS schiefging, statt nur dass etwas
 * schiefging.
 */
fun fetchErrorSummary(candidates: List<SourceResult>): String? =
    candidates.filter { it.error != null }
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
