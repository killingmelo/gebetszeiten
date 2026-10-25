package de.gebetszeiten.core.prayertimes.officialtimes

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
 * schiefging. Liegt in `core`, damit Handy-App und Desktop denselben Text
 * schreiben.
 */
fun fetchErrorSummary(candidates: List<SourceResult>): String? =
    candidates.filter { it.schedule.isEmpty() && it.error != null }
        .sortedBy { it.source.ordinal }
        .joinToString(" · ") { "${sourceLabel(it.source)}: ${it.error}" }
        .takeIf { it.isNotEmpty() }

/**
 * Der Fehlertext fuer einen Abruf, der KEINE Zeiten gebracht hat — das, was
 * der Aufrufer in diesem Fall als `lastError` ablegt.
 *
 * Hat mindestens eine Quelle geworfen, steht deren Zusammenfassung dort
 * ([fetchErrorSummary]). Ist sie `null`, hat KEINE Quelle geworfen — dann hat
 * schlicht keine etwas geliefert (kein Diyanet-Standort aufloesbar, oder leere
 * Antworten), und dafuer ist das Literal die richtige Auskunft.
 */
fun emptyResultError(errorSummary: String?): String =
    errorSummary ?: "Keine amtlichen Zeiten erhalten (Standort oder Netz)"

/** Klartextnamen der Quellen — kurz, weil sie in einer Zeile mit ihrem
 *  Fehlergrund stehen. */
private fun sourceLabel(source: SourceId): String = when (source) {
    SourceId.DIRECT -> "Direktabruf"
    SourceId.PROXY_ABDUS -> "Proxy"
    SourceId.EZANVAKTI -> "ezanvakti"
}
