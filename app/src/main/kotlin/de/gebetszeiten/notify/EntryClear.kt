package de.gebetszeiten.notify

/**
 * Wie lange das hörbare Eintritts-Banner in der Leiste stehen bleibt, wenn die
 * dauerhafte Anzeige ohnehin läuft: Es hat seinen Job getan (Ton + kurzer
 * Heads-up) und würde sonst die ganze Gebetsperiode lang die dauerhafte Zeile
 * doppeln. 5 Minuten reichen, um es nach dem Entsperren noch zu sehen.
 */
const val ENTRY_ALERT_SHORT_MILLIS: Long = 5 * 60 * 1000L

/**
 * Epoch-Millis, zu dem sich das Eintritts-Banner selbst abräumt. Nur kurzlebig,
 * wenn die dauerhafte Anzeige den Status bereits trägt (dauerhaft AN + hörbarer
 * Stil); sonst lebt es bis zum nächsten Übergang ([transitionMillis]) — dann ist
 * es der einzige Beleg für die Gebetszeit. Auf [transitionMillis] gedeckelt.
 */
fun entryClearAtMillis(
    nowMillis: Long,
    transitionMillis: Long,
    persistent: Boolean,
    audible: Boolean,
    shortMillis: Long = ENTRY_ALERT_SHORT_MILLIS,
): Long =
    if (persistent && audible) minOf(nowMillis + shortMillis, transitionMillis) else transitionMillis
