package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.DueLocation
import java.time.LocalDate

/**
 * Welcher Ort bei diesem Ausloeser abgerufen wird — oder keiner.
 *
 * Reine Funktion, absichtlich: das ist die Entscheidung, an der ein
 * Denkfehler ganze Orte aushungern laesst, und sie gehoert deshalb in einen
 * Test und nicht in eine `suspend fun` mit `Context`. Sie steht in DIESER
 * Datei, weil sie nichts anderes tut als [needsRefresh] auf die von
 * [CacheStore.dueOrder] geordneten Kandidaten anzuwenden: die Bremse und
 * ihre Anwendung nebeneinander, ohne eine dritte Datei fuer sechs Zeilen.
 *
 * [due] kommt aus [CacheStore.dueOrder] und ist schon nach Dringlichkeit
 * geordnet — hier wird nur der ERSTE genommen, der die Bremse passiert.
 * Passiert keiner sie, ist nichts zu tun (null). Genau EIN Ort je Ausloeser:
 * das Broadcast-Budget von ~25 s gilt weiter, der naechste ist beim
 * naechsten Gebet dran.
 *
 * [force] (der „Jetzt aktualisieren"-Knopf) liefert immer [activeCoords],
 * ohne [due] zu befragen: der Nutzer meint damit, was er vor sich sieht —
 * nicht einen Favoriten am anderen Ende der Liste — und er muss auch durch
 * eine laufende Sperrfrist kommen.
 */
internal fun chooseTarget(
    due: List<DueLocation>,
    activeCoords: Pair<Double, Double>,
    force: Boolean,
    today: LocalDate,
    nowEpochMs: Long,
): Pair<Double, Double>? {
    if (force) return activeCoords
    return due.firstOrNull { it.isDue(today, nowEpochMs) }?.let { it.latitude to it.longitude }
}

/** Passt [needsRefresh] auf einen Kandidaten an. Alles kommt aus SEINEN
 *  Kopffeldern, nicht aus denen des aktiven Orts; `stampOk` heisst „hat einen
 *  Zeitplan", also `coveredUntil != null`. */
private fun DueLocation.isDue(today: LocalDate, nowEpochMs: Long): Boolean =
    needsRefresh(
        coveredUntil = coveredUntil,
        today = today,
        stampOk = coveredUntil != null,
        lastAttemptEpochMs = lastAttemptEpochMs,
        lastAttemptFailed = lastError != null,
        nowEpochMs = nowEpochMs,
    )

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
 *  (~5-6/Tag) plus App-Start, Einstellungsaenderung und dem Knopf.
 *  [retryAfterFailureMs] faengt nach einem Fehlschlag die SCHNELLEN
 *  Wiederholungen — App-Start, Einstellungsaenderung, mehrere Knopfdruecke in
 *  Folge. Den Abstand zwischen zwei Gebets-Alarmen faengt sie NICHT: der
 *  kuerzeste echte Abstand (Maghrib zu Isha, Fajr zu Guenes) liegt bei ~1 h,
 *  typisch 2-5 h, die halbe Stunde ist da laengst abgelaufen. Was einen
 *  dauerhaft scheiternden Ort davon abhaelt, alle anderen auszuhungern, ist
 *  deshalb nicht diese Sperre, sondern die Sortierung in
 *  `CacheStore.dueOrder` (Schluessel `hopeless` und die Rotation nach
 *  `lastAttemptEpochMs`).
 *
 *  [retryAfterShortResultMs] deckelt zusaetzlich den Fall, dass
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
 *  vertrauenswuerdig — und sie darf deshalb gewinnen: ein Ort ohne Zeiten, an
 *  dem der Abruf gerade gescheitert ist, soll nicht bei jedem App-Start und
 *  jeder Einstellungsaenderung neu versucht werden. Bitte nicht
 *  zurueckdrehen.
 *
 *  Diese Reihenfolge ist aber NICHT der Schutz gegen Aushungerung — dafuer
 *  ist der Abstand zwischen zwei Gebets-Alarmen zu gross (siehe oben). Den
 *  leistet allein die Sortierung in `CacheStore.dueOrder`.
 *
 *  Preis dieser Reihenfolge: wechselt der Nutzer den Ort und der erste Abruf
 *  scheitert, dauert es bis zu [retryAfterFailureMs] bis zum naechsten
 *  Versuch — laeuft in der Zwischenzeit ein Gebets-Alarm, ist der Ort dort
 *  uebersprungen. [force] (der "Jetzt aktualisieren"-Knopf) durchbricht
 *  weiterhin jede Sperre. */
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
