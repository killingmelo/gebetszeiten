package de.gebetszeiten.official

import java.time.LocalDate

/** Neu laden, wenn der Cache nicht zum Standort passt oder weniger als
 *  [minFutureDays] Tage Zukunft ab [today] abdeckt (Jahresseite direkt liefert
 *  ein ganzes Jahr, Fallback-Proxy nur 31 Tage).
 *
 *  Wiederholungs-Bremse: `refreshOfficial` haengt an jedem Gebets-Alarm
 *  (~5-6/Tag) plus App-Start, Einstellungsaenderung und dem Knopf. Scheitert
 *  der Abruf dauerhaft (kein Netz, Ort nicht aufloesbar), wuerde ohne Bremse
 *  unbegrenzt oft wiederholt. [retryAfterFailureMs] deckelt das nach einem
 *  Fehlschlag; [retryAfterShortResultMs] deckelt zusaetzlich den Fall, dass
 *  ein Abruf zwar ERFOLGREICH war, die Abdeckung aber unter der Schwelle
 *  bleibt (z. B. nur die 31-Tage-Fallback-Quelle antwortet) — sonst wuerde
 *  bei jedem Ausloeser neu abgerufen, ein Sturm aus einem erfolgreichen
 *  Abruf heraus, den eine reine Fehlschlag-Sperre nicht faengt. [force] (der
 *  "Jetzt aktualisieren"-Knopf) und ein Standortwechsel (`!stampOk`)
 *  durchbrechen beide Sperren immer. */
fun needsRefresh(
    coveredUntil: LocalDate?,
    today: LocalDate,
    stampOk: Boolean,
    lastAttemptEpochMs: Long?,
    lastAttemptFailed: Boolean,
    nowEpochMs: Long,
    minFutureDays: Long = 7,
    retryAfterFailureMs: Long = 30 * 60 * 1000,
    retryAfterShortResultMs: Long = 12 * 60 * 60 * 1000,
    force: Boolean = false,
): Boolean {
    if (force) return true
    if (!stampOk) return true
    if (lastAttemptEpochMs != null) {
        val elapsed = nowEpochMs - lastAttemptEpochMs
        if (lastAttemptFailed && elapsed < retryAfterFailureMs) return false
        val coverageThin = coveredUntil == null || coveredUntil.isBefore(today.plusDays(minFutureDays))
        if (!lastAttemptFailed && coverageThin && elapsed < retryAfterShortResultMs) return false
    }
    if (coveredUntil == null) return true
    return coveredUntil.isBefore(today.plusDays(minFutureDays))
}
