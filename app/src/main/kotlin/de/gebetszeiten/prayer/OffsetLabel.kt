package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.TimeOffsets

/**
 * Kennzeichnung der Minuten-Verschiebung (05.11.2026): Die App verschiebt nie
 * still. Jede verschobene Zeit trägt ihr Vorzeichen, und die Quellenzeile sagt,
 * dass der Nutzer verschoben hat. Minus als echtes Minuszeichen (U+2212), kein
 * Gedanken- oder Halbgeviertstrich.
 */
fun offsetMark(minutes: Int): String? = TimeOffsets.mark(minutes)

private val ORDER = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

/**
 * „Von dir verschoben: Ẓuhr +5 Min, Maġrib −3 Min“, oder null ohne Verschiebung.
 * [name] liefert den Namen der Zeit (DMG), [template] den Satz mit `%1$s`.
 */
fun offsetSummary(offsets: TimeOffsets, name: (Prayer) -> String, template: String = "Von dir verschoben: %1\$s"): String? {
    if (offsets.isZero) return null
    val parts = ORDER.mapNotNull { p -> offsetMark(offsets.of(p))?.let { "${name(p)} $it Min" } }
    return template.format(parts.joinToString(", "))
}
