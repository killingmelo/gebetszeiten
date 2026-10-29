package de.gebetszeiten.prayer

/**
 * Eine Zeitspanne als sichtbarer Text: „11:45 bis 12:05“. Einziger Weg, eine
 * Spanne zu schreiben, damit nirgends ein Strich zwischen den Uhrzeiten steht
 * (Schreibregel: kein Gedanken- und kein Halbgeviertstrich in sichtbaren Texten).
 */
fun timeRange(start: String, end: String): String = "$start bis $end"
