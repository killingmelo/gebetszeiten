package de.gebetszeiten.alarm

/**
 * Die Momente, an denen sich die Stufenanzeige aendert — Text („noch 20+ Min")
 * wie Statusleisten-Symbol.
 *
 * Bewusst ohne `Context` und ohne `AlarmManager`: `scheduleDisplayStep` ist
 * wegen der Android-Naht nicht testbar, die Rechnung darin aber sehr wohl.
 * Herausgezogen nach demselben Muster wie `CacheFreshness.chooseTarget`.
 */

/**
 * Der Zuschlag auf jede berechnete Grenze.
 *
 * Die Grenzen liegen exakt auf dem Raster (`targetMs - 10 * 60_000`). Ein
 * Alarm, der GENAU dort feuert, sieht noch 600 000 ms Restzeit — und
 * `remainingStepShort` liefert dafuer weiterhin „10+ Min", nicht „9 Min".
 * Bisher rettete das nur die Alarm-Latenz: der Alarm kam ein paar
 * Millisekunden spaet, und der Wert stimmte wieder. Auf dem Statusleisten-
 * Symbol waere ein bei `10` eingefrorener Balken gut sichtbar.
 *
 * Eine halbe Sekunde ist fuer den Nutzer unsichtbar und liegt weit ueber
 * jeder Rundungsunschaerfe zwischen Alarmzeit und `Instant.now()`. Gleiche
 * Alarmzahl, gleiche Weckvorgaenge — nur der Wert ist eindeutig.
 */
internal const val BOUNDARY_SETTLE_MS = 500L

/**
 * Ein Alarm feuert erst, wenn er mindestens so weit in der Zukunft liegt.
 * Schuetzt davor, dass ein gerade abgearbeiteter Alarm sich selbst noch
 * einmal einplant.
 */
internal const val BOUNDARY_MIN_LEAD_MS = 1_000L

/**
 * Alle Stufengrenzen eines Ziels, die nach [nowMs] noch kommen koennen.
 *
 * Aufloesung wie `remainingStepShort`: die letzten zehn Minuten einzeln
 * (dort ist die genaue Zahl das Dringlichkeitssignal), davor Zehnminuten-
 * Schritte bis zur vollen Stunde, davor volle Stunden bis hinauf zum Ziel.
 * Die Minuten- und Zehnminutengrenzen entstehen unabhaengig von [nowMs] —
 * gefiltert wird erst in [nextDisplayBoundary]; nur die Stundenschleife
 * braucht [nowMs] als Abbruch, sonst liefe sie unbegrenzt.
 */
internal fun displayStepBoundaries(targetMs: Long, nowMs: Long): List<Long> {
    val boundaries = mutableListOf<Long>()
    for (minute in 1..9) {
        boundaries += targetMs - minute * 60_000L + BOUNDARY_SETTLE_MS
    }
    for (tenMin in 1..5) {
        boundaries += targetMs - tenMin * 10 * 60_000L + BOUNDARY_SETTLE_MS
    }
    var hour = 1L
    while (true) {
        val boundary = targetMs - hour * 3_600_000L + BOUNDARY_SETTLE_MS
        if (boundary <= nowMs) break
        boundaries += boundary
        hour++
    }
    return boundaries
}

/** Die naechste Grenze, die weit genug in der Zukunft liegt — oder `null`,
 *  wenn keine mehr kommt (dann wird der Alarm abbestellt). */
internal fun nextDisplayBoundary(boundaries: List<Long>, nowMs: Long): Long? =
    boundaries.filter { it > nowMs + BOUNDARY_MIN_LEAD_MS }.minOrNull()
