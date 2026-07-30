package de.gebetszeiten.official

import java.time.LocalDate

/** Neu laden, wenn der Cache nicht zum Standort passt oder weniger als
 *  [minFutureDays] Tage Zukunft ab [today] abdeckt (Jahresseite direkt liefert
 *  ein ganzes Jahr, Fallback-Proxy nur 31 Tage). */
fun needsRefresh(
    coveredUntil: LocalDate?,
    today: LocalDate,
    stampOk: Boolean,
    minFutureDays: Long = 7,
): Boolean {
    if (!stampOk) return true
    if (coveredUntil == null) return true
    return coveredUntil.isBefore(today.plusDays(minFutureDays))
}
