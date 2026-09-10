package de.gebetszeiten.notify

import androidx.annotation.DrawableRes
import de.gebetszeiten.R
import java.time.Duration

/**
 * Welches Restzeit-Symbol die Statusleiste tragen soll — statt des statischen
 * Monds die verbleibenden Stunden bzw. Minuten.
 *
 * Die Entscheidung selbst (`countdownGlyph`) kommt ohne jeden Android-Bezug
 * aus: so ist sie in einem reinen JVM-Test pruefbar (das Projekt hat kein
 * Robolectric). Die Zuordnung auf `R.drawable` steht getrennt davon in
 * `countdownIconRes` weiter unten; die Verdrahtung in die Benachrichtigung
 * liegt bei den Aufrufern.
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

/**
 * Das Symbol zum Glyph — die Grafiken erzeugt
 * `tools/notification-icons/build_icons.py`.
 *
 * Ausgeschrieben als `when` auf `R.drawable.*`, und das ist keine
 * Geschmacksfrage: `Resources.getIdentifier()` waere kuerzer, aber das Projekt
 * baut mit `isShrinkResources = true`. Eine nur ueber einen Namensstring
 * adressierte Grafik gilt dem Shrinker als unbenutzt, fliegt aus dem
 * Release-Paket, und die Statusleiste bliebe leer — im Debug-Build waere davon
 * nichts zu sehen. Jede Zeile hier ist der Nachweis, dass die Datei gebraucht
 * wird.
 *
 * `capped` und `approx` spielen fuer die Auswahl keine Rolle: `Hours(9)` sieht
 * gedeckelt wie ungedeckelt gleich aus, und `Minutes(10)` kann es nur
 * gerundet geben (unter zehn Minuten zaehlt `countdownGlyph` einzeln). Die
 * Unterscheidung bleibt trotzdem im Glyph — sie beschreibt die Absicht, nicht
 * das Bild.
 *
 * `Now` und `None` liefern beide den bestehenden Mond. Zwei Zustaende, ein
 * Bild: „gleich ist jetzt" und „Anzeige aus" sind verschieden, auch wenn sie
 * heute gleich aussehen.
 */
@DrawableRes
fun countdownIconRes(glyph: CountdownGlyph): Int = when (glyph) {
    is CountdownGlyph.Hours -> when (glyph.hours) {
        1 -> R.drawable.ic_countdown_1h
        2 -> R.drawable.ic_countdown_2h
        3 -> R.drawable.ic_countdown_3h
        4 -> R.drawable.ic_countdown_4h
        5 -> R.drawable.ic_countdown_5h
        6 -> R.drawable.ic_countdown_6h
        7 -> R.drawable.ic_countdown_7h
        8 -> R.drawable.ic_countdown_8h
        9 -> R.drawable.ic_countdown_9h
        // Unerreichbar: countdownGlyph deckelt bei 9 und faellt unter 1 h auf
        // Minutes. Lieber der Mond als eine Ausnahme in der Statusleiste.
        else -> R.drawable.ic_notification
    }

    is CountdownGlyph.Minutes -> when (glyph.minutes) {
        1 -> R.drawable.ic_countdown_1
        2 -> R.drawable.ic_countdown_2
        3 -> R.drawable.ic_countdown_3
        4 -> R.drawable.ic_countdown_4
        5 -> R.drawable.ic_countdown_5
        6 -> R.drawable.ic_countdown_6
        7 -> R.drawable.ic_countdown_7
        8 -> R.drawable.ic_countdown_8
        9 -> R.drawable.ic_countdown_9
        10 -> R.drawable.ic_countdown_10
        20 -> R.drawable.ic_countdown_20
        30 -> R.drawable.ic_countdown_30
        40 -> R.drawable.ic_countdown_40
        50 -> R.drawable.ic_countdown_50
        else -> R.drawable.ic_notification
    }

    CountdownGlyph.Now -> R.drawable.ic_notification
    CountdownGlyph.None -> R.drawable.ic_notification
}
