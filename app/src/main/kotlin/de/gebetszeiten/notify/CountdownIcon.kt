package de.gebetszeiten.notify

import java.time.Duration

/**
 * Welches Restzeit-Symbol die Statusleiste tragen soll — statt des statischen
 * Monds die verbleibenden Stunden bzw. Minuten.
 *
 * Bewusst OHNE jeden Android-Import: so ist die Auswahl in einem reinen
 * JVM-Test pruefbar (das Projekt hat kein Robolectric). Die Zuordnung auf
 * `R.drawable` und die Verdrahtung in die Benachrichtigung liegen bei den
 * Aufrufern.
 */
sealed interface CountdownGlyph {
    /** Volle Stunden. `capped` = ab 10 h wird auf `9h` gedeckelt. */
    data class Hours(val hours: Int, val capped: Boolean) : CountdownGlyph

    /**
     * Minuten. `approx` = auf 10 abgerundet (`10` heisst „mindestens 10"),
     * sonst exakt (`9`…`1`).
     */
    data class Minutes(val minutes: Int, val approx: Boolean) : CountdownGlyph

    /** Letzte Minute (auch bereits verstrichen) — Anzeige bleibt der Mond. */
    data object Now : CountdownGlyph

    /** Abgeschaltet oder kein naechstes Gebet bekannt — der bestehende Mond. */
    data object None : CountdownGlyph
}

/**
 * `capped` und `approx` tragen keine Anzeigelogik; sie machen die Absicht im
 * Ergebnis sichtbar, damit ein Aufrufer, der das Symbol beschriften will,
 * nicht raten muss, ob `10` „genau 10" oder „mindestens 10" heisst.
 *
 * Die Rundung ist dieselbe wie in `remainingStepShort` — ABGERUNDET, nicht
 * gerundet. 27 Minuten ergeben `20`, nicht `30`: ein Symbol, das mehr Zeit
 * verspricht als der Text daneben, waere schlimmer als kein Symbol. Dadurch
 * wechselt das Symbol nur an Momenten, an denen sich auch der Stufentext
 * aendert, und kostet null zusaetzliche Weckvorgaenge
 * (`PrayerAlarmScheduler.scheduleDisplayStep`).
 */
fun countdownGlyph(remaining: Duration?, enabled: Boolean): CountdownGlyph {
    if (!enabled || remaining == null) return CountdownGlyph.None
    val minutes = remaining.toMinutes()
    // Deckt auch negative Dauern ab: toMinutes() ist dort <= 0.
    if (minutes < 1) return CountdownGlyph.Now
    val hours = remaining.toHours()
    return when {
        // Genau 10 h ist bereits gedeckelt, 10 h − 1 ms nicht mehr: hier tut
        // ein > statt >= still das Falsche.
        hours >= 10 -> CountdownGlyph.Hours(9, capped = true)
        hours >= 1 -> CountdownGlyph.Hours(hours.toInt(), capped = false)
        minutes >= 10 -> CountdownGlyph.Minutes(((minutes / 10) * 10).toInt(), approx = true)
        else -> CountdownGlyph.Minutes(minutes.toInt(), approx = false)
    }
}
