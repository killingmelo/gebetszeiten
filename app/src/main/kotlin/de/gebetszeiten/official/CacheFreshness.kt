package de.gebetszeiten.official

import java.time.LocalDate

/** Neu laden, wenn es fuer den Ort keine Zeiten gibt (`!stampOk`) oder weniger
 *  als [minFutureDays] Tage Zukunft ab [today] abgedeckt sind.
 *
 *  **Schwelle 60 Tage.** Die Diyanet-Jahresseite ist ein rollierendes
 *  ~16-Monats-Fenster (am 06.09.2026 live geprueft: 403 Zeilen bis
 *  31.12.2027) — ein Abruf je Ort deckt also ueber ein Jahr ab, und ein Abruf
 *  je Ort und Jahr bleibt ein Abruf je Ort und Jahr. Die hohe Schwelle kostet
 *  damit praktisch nichts, kauft aber zwei Monate Puffer: die App darf zwei
 *  Monate erfolglos bleiben, bevor der Nutzer es merkt. Genau das ist das
 *  Vertrauen, um das es bei den Favoriten geht.
 *
 *  Wiederholungs-Bremse: `refreshOfficial` haengt an jedem Gebets-Alarm
 *  (~5-6/Tag) plus App-Start, Einstellungsaenderung und dem Knopf. Scheitert
 *  der Abruf dauerhaft (kein Netz, Ort nicht aufloesbar), wuerde ohne Bremse
 *  unbegrenzt oft wiederholt. [retryAfterFailureMs] deckelt das nach einem
 *  Fehlschlag; [retryAfterShortResultMs] deckelt zusaetzlich den Fall, dass
 *  ein Abruf zwar ERFOLGREICH war, die Abdeckung aber unter der Schwelle
 *  bleibt (z. B. nur die 31-Tage-Fallback-Quelle antwortet) — sonst wuerde
 *  bei jedem Ausloeser neu abgerufen, ein Sturm aus einem erfolgreichen
 *  Abruf heraus, den eine reine Fehlschlag-Sperre nicht faengt. Mit der
 *  60-Tage-Schwelle wird diese zweite Sperre erst richtig tragend: antwortet
 *  nur eine 31-Tage-Quelle, bleibt die Abdeckung dauerhaft unter der
 *  Schwelle.
 *
 *  **Regel-Reihenfolge, und warum die Fehlschlag-Sperre `!stampOk` schlaegt:**
 *  Vor der Umstellung auf einen Cache-Eintrag je Ort war das Versuchsprotokoll
 *  GLOBAL — `lastAttemptEpochMs` konnte von einem ganz anderen Ort stammen,
 *  und eine Sperre haette den gerade gewaehlten Ort wegen eines FREMDEN
 *  Fehlschlags blockiert. Genau deshalb musste `!stampOk` damals gewinnen.
 *  Heute gehoert das Protokoll zum Eintrag DIESES Ortes, die Sperre ist also
 *  vertrauenswuerdig — und sie MUSS gewinnen, seit es mehrere Orte gibt: ein
 *  Favorit, an dem noch nie ein Abruf gelang (Ort nicht aufloesbar, dauerhaft
 *  kein Netz), stuende in `CacheStore.dueOrder` immer ganz oben, wuerde bei
 *  jedem Ausloeser gewaehlt, jedes Mal scheitern und alle anderen Orte
 *  aushungern. Bitte nicht zurueckdrehen.
 *
 *  Preis dieser Reihenfolge: wechselt der Nutzer den Ort und der erste Abruf
 *  scheitert, dauert es bis zu [retryAfterFailureMs] bis zum naechsten
 *  Versuch statt bis zum naechsten Gebets-Alarm. [force] (der "Jetzt
 *  aktualisieren"-Knopf) durchbricht weiterhin jede Sperre. */
fun needsRefresh(
    coveredUntil: LocalDate?,
    today: LocalDate,
    stampOk: Boolean,
    lastAttemptEpochMs: Long?,
    lastAttemptFailed: Boolean,
    nowEpochMs: Long,
    minFutureDays: Long = 60,
    retryAfterFailureMs: Long = 30 * 60 * 1000,
    retryAfterShortResultMs: Long = 12 * 60 * 60 * 1000,
    force: Boolean = false,
): Boolean {
    if (force) return true
    val elapsed = lastAttemptEpochMs?.let { nowEpochMs - it }
    if (elapsed != null && lastAttemptFailed && elapsed < retryAfterFailureMs) return false
    if (!stampOk) return true
    val coverageThin = coveredUntil == null || coveredUntil.isBefore(today.plusDays(minFutureDays))
    if (elapsed != null && !lastAttemptFailed && coverageThin && elapsed < retryAfterShortResultMs) return false
    return coverageThin
}
