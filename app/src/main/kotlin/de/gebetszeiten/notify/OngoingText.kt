package de.gebetszeiten.notify

/**
 * Der aufgeklappte Text der Dauerbenachrichtigung (`BigTextStyle`), damit
 * „runterziehen" tatsaechlich mehr zeigt als der eingeklappte Zustand.
 *
 * Reine Funktion: alle Bausteine kommen bereits aufgeloest herein — genau
 * dieselben, die `updateOngoing` heute in die eingeklappte Inhaltszeile
 * schreibt (`ongoing_at`, `ongoing_since`, `ongoing_until`,
 * `KarahaLine.text`). Neue Texte gibt es nicht, und die eingeklappte Zeile
 * bleibt unveraendert.
 *
 * [timeLine] („um 22:48") ist ein PFLICHTargument, kein Nullable: die genaue
 * Uhrzeit des naechsten Gebets muss in BEIDEN Countdown-Modi im Aufgeklappten
 * stehen. Im Stufen-Modus traegt der Titel („Noch 20+ Min bis Isha") sie
 * nicht, und genau die verlangt der Nutzer.
 *
 * Leere Bausteine werden uebersprungen, statt eine leere Zeile zu erzeugen.
 */
internal fun ongoingBigText(
    timeLine: String,
    activeLine: String? = null,
    untilLine: String? = null,
    karahaText: String? = null,
): String = listOfNotNull(timeLine, activeLine, untilLine, karahaText)
    .filter { it.isNotBlank() }
    .joinToString("\n")

/**
 * Der Anzeigemodus der Dauerbenachrichtigung — die EINE Entscheidung, aus der
 * alle drei Texte folgen.
 *
 * - [STEPS]: die Restzeit traegt den Titel („Noch 20+ Min bis Isha"), die
 *   genaue Uhrzeit ist von dort verdraengt.
 * - [EXACT]: der Titel nennt die Uhrzeit („Isha um 22:48"), dazu laeuft der
 *   Systemzaehler in der Kopfzeile.
 * - [PLAIN]: Countdown aus — textlich dasselbe wie [EXACT], nur ohne Zaehler.
 *
 * [PLAIN] faengt ausserdem die letzte Minute des Stufen-Modus ab: dort ist
 * `remainingStepShort` leer, und der Titel faellt schon heute auf den
 * klassischen zurueck.
 */
internal enum class OngoingMode { STEPS, EXACT, PLAIN }

/** Welcher Modus gilt — aus den beiden Schaltern und der Reststufe.
 *  Steht hier und nicht im `NotificationCompat`-Aufbau, damit die
 *  Fallunterscheidung ohne Geraet ausfuehrbar ist. */
internal fun ongoingMode(countdown: Boolean, exact: Boolean, stepShort: String): OngoingMode = when {
    !countdown -> OngoingMode.PLAIN
    exact -> OngoingMode.EXACT
    // Letzte Minute: keine Stufe mehr, also auch kein Stufen-Titel.
    stepShort.isEmpty() -> OngoingMode.PLAIN
    else -> OngoingMode.STEPS
}

/**
 * Alle drei Texte der Dauerbenachrichtigung auf einmal. `updateOngoing`
 * setzt danach nur noch zusammen, was hier entschieden wurde.
 */
internal data class OngoingTexts(
    /** Kopfzeile. */
    val title: String,
    /** Eingeklappte Inhaltszeile; `null` heisst „gar keine". */
    val contentText: String?,
    /** Aufgeklappt (`BigTextStyle`). */
    val bigText: String,
)

/**
 * Die Entscheidung, nicht nur der Wortlaut: aus [mode] und den bereits
 * aufgeloesten Bausteinen folgen Titel, eingeklappte Zeile und
 * aufgeklappter Text.
 *
 * Warum das hier steht und nicht im `NotificationCompat`-Aufbau: die
 * Kernzusage von Task 17 — die genaue Uhrzeit steht in BEIDEN
 * Countdown-Modi im Aufgeklappten — war eine Verzweigung mitten in einer
 * Funktion, die ohne Geraet nicht laufen kann. Sie liess sich lautlos
 * entfernen. Als reine Funktion ist sie ausfuehrbar, und der Test faellt.
 * Dasselbe Muster wie `chooseTarget`, `headersFor`, `displayStepBoundaries`
 * und `statusOf`.
 *
 * [titleWithStep] („Noch 20+ Min bis Isha") wird nur in [OngoingMode.STEPS]
 * gelesen — nur dort gibt es ueberhaupt eine Reststufe, aus der er gebaut
 * werden kann. [titleWithTime] („Isha um 22:48") traegt die anderen beiden
 * Modi.
 *
 * [timeLine] („um 22:48") ist ein PFLICHTargument: in den aufgeklappten Text
 * geht es IMMER ein. In die eingeklappte Zeile dagegen nur im Stufen-Modus,
 * denn sonst stuende die Uhrzeit zweimal direkt untereinander — genau wie
 * heute.
 */
internal fun ongoingTexts(
    mode: OngoingMode,
    titleWithStep: String,
    titleWithTime: String,
    timeLine: String,
    activeLine: String? = null,
    untilLine: String? = null,
    karahaText: String? = null,
): OngoingTexts {
    val steps = mode == OngoingMode.STEPS
    // Wort fuer Wort die eingeklappte Zeile von vorher — einschliesslich
    // dessen, dass sie leere Bausteine NICHT wegfiltert (der aufgeklappte
    // Text tut es). Beobachtbar ist der Unterschied nicht: die vier
    // Bausteine kommen aus Ressourcen-Formaten und sind nie leer.
    val collapsed = listOfNotNull(timeLine.takeIf { steps }, activeLine, untilLine, karahaText)
    return OngoingTexts(
        title = if (steps) titleWithStep else titleWithTime,
        contentText = if (collapsed.isEmpty()) null else collapsed.joinToString(" · "),
        bigText = ongoingBigText(timeLine, activeLine, untilLine, karahaText),
    )
}

/**
 * Symbol, Modus und alle drei Texte der Dauerbenachrichtigung — aus EINEM
 * Aufruf.
 *
 * Warum es diese Klammer ueber [ongoingMode], [ongoingTexts] und
 * [countdownGlyph] gibt: `updateOngoing` hat die drei frueher selbst
 * zusammengerechnet. Solange es die einzige Stelle war, die das tut, war das
 * unauffaellig. Sobald eine ZWEITE Stelle dieselbe Anzeige zeigen will — die
 * Vorschau im Onboarding, die dem Nutzer zeigt, was er bekommt —, waere das
 * Nachbauen dieser Rechnung eine zweite Wahrheit: die Vorschau koennte ein
 * Symbol versprechen, das die echte Anzeige nie zeigt.
 *
 * [titleWithStep] ist eine Funktion und kein fertiger Text, weil die
 * Reststufe („20+ Min") erst hier entsteht. Der Aufrufer formatiert sie in
 * seinen Ressourcen-String, ohne die Stufenregel zu kennen.
 */
internal data class Ongoing(
    val mode: OngoingMode,
    val glyph: CountdownGlyph,
    val texts: OngoingTexts,
    /**
     * Kurztext für das Live-Update-Feld der Statusleiste (Android 16,
     * `setShortCriticalText`), nur die Zahl wie auf dem Symbol, siehe
     * [chipText]. Ohne Restzeit gar nichts.
     * Gebraucht, weil OnePlus/OPPO das kleine Symbol durch das App-Symbol
     * ersetzen (`oplus_smallicon_use_app_icon`, am Gerät belegt 05.11.2026)
     * und die Zahl dort sonst nie ankommt.
     */
    val chip: String? = null,
)

/**
 * Nur die Zahl, wie auf dem Statusleisten-Symbol: „50“ (Minuten, auf zehn
 * abgerundet), „7“, „2h“. Wunsch des Nutzers (05.11.2026): kein „Min“ und
 * kein „+“ im Feld. Stunden tragen das „h“, sonst wären „2“ Stunden und
 * „2“ Minuten nicht zu unterscheiden.
 */
internal fun chipText(glyph: CountdownGlyph): String? = when (glyph) {
    is CountdownGlyph.Hours -> "${glyph.hours}h"
    is CountdownGlyph.Minutes -> "${glyph.minutes}"
    CountdownGlyph.Now, CountdownGlyph.None -> null
}

internal fun ongoing(
    remaining: java.time.Duration,
    countdown: Boolean,
    exact: Boolean,
    titleWithStep: (step: String) -> String,
    titleWithTime: String,
    timeLine: String,
    activeLine: String? = null,
    untilLine: String? = null,
    karahaText: String? = null,
): Ongoing {
    // Im EXACT-Modus gibt es keine Stufe: den Text zeichnet dort der
    // Systemzaehler. Das Symbol dagegen folgt in BEIDEN Modi derselben
    // Stufenregel — deshalb steht `countdownGlyph` ausserhalb dieser
    // Bedingung. Genau darauf stuetzt sich die Vorschau.
    val stepShort = if (countdown && !exact) de.gebetszeiten.prayer.remainingStepShort(remaining) else ""
    val mode = ongoingMode(countdown, exact, stepShort)
    return Ongoing(
        mode = mode,
        glyph = countdownGlyph(remaining, countdown),
        // Auch im EXACT-Modus: das Symbol folgt dort derselben Stufenregel und
        // wird von der Anzeige-Weckkette weitergestellt, also auch das Feld.
        chip = chipText(countdownGlyph(remaining, countdown)),
        texts = ongoingTexts(
            mode = mode,
            titleWithStep = titleWithStep(stepShort),
            titleWithTime = titleWithTime,
            timeLine = timeLine,
            activeLine = activeLine,
            untilLine = untilLine,
            karahaText = karahaText,
        ),
    )
}
