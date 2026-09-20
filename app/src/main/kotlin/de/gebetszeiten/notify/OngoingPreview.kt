package de.gebetszeiten.notify

import de.gebetszeiten.data.AppSettings
import java.time.Duration

/**
 * Eine Zeile der Vorschau in der Ersteinrichtung: was der Nutzer bekaeme,
 * wenn er diese Restzeit-Stufe waehlt.
 *
 * Gebaut aus [ongoing] — derselben Funktion, aus der die ECHTE
 * Dauerbenachrichtigung entsteht. Das ist der ganze Sinn dieser Datei: eine
 * Vorschau, die ihr Bild selbst zusammenrechnet, verspricht frueher oder
 * spaeter etwas, das die Anzeige nie zeigt.
 */
internal data class OngoingPreviewRow(
    /** Der Wert, den [AppSettings.countdownMode] bekaeme. */
    val mode: String,
    val glyph: CountdownGlyph,
    val texts: OngoingTexts,
    /**
     * Ob zusaetzlich der Systemzaehler mitlaeuft. Nur in
     * [AppSettings.PRECISION_EXACT] — und es ist der EINZIGE sichtbare
     * Unterschied zu den Stufen, der nicht im Titel steht.
     */
    val showsChronometer: Boolean,
)

/**
 * Die drei Stufen in der Reihenfolge, in der die Auswahl sie zeigt.
 *
 * Die Bausteine kommen fertig formatiert herein (aus `stringResource`), damit
 * diese Funktion ohne Context auskommt und im reinen JVM-Test laeuft.
 */
internal fun ongoingPreviewRows(
    remaining: Duration,
    titleWithStep: (step: String) -> String,
    titleWithTime: String,
    timeLine: String,
    activeLine: String? = null,
    karahaText: String? = null,
    city: String? = null,
): List<OngoingPreviewRow> = listOf(
    AppSettings.COUNTDOWN_OFF to false,
    AppSettings.PRECISION_STEPS to false,
    AppSettings.PRECISION_EXACT to true,
).map { (mode, exact) ->
    val a = ongoing(
        remaining = remaining,
        countdown = mode != AppSettings.COUNTDOWN_OFF,
        exact = exact,
        titleWithStep = titleWithStep,
        titleWithTime = titleWithTime,
        timeLine = timeLine,
        activeLine = activeLine,
        karahaText = karahaText,
        city = city,
    )
    OngoingPreviewRow(
        mode = mode,
        glyph = a.glyph,
        texts = a.texts,
        // Nicht `exact`, sondern der Modus, den [ongoing] WIRKLICH gewaehlt
        // hat: in der letzten Minute faellt auch EXACT auf PLAIN zurueck, und
        // dann zeichnet kein Zaehler mehr mit.
        showsChronometer = a.mode == OngoingMode.EXACT,
    )
}

/**
 * Woher die Zahlen der Vorschau kommen.
 *
 * Am liebsten aus der echten naechsten Gebetszeit — dann zeigt die Vorschau,
 * was der Nutzer gleich tatsaechlich sieht. Gibt es keine (kein Ort
 * abgedeckt, Abruf noch nicht durch), tritt ein Beispiel an ihre Stelle, und
 * zwar ausdruecklich als solches beschriftet. Eine Vorschau, die ein Beispiel
 * fuer echt ausgibt, waere dieselbe Sorte Luege wie eine erfundene Gebetszeit.
 */
internal sealed interface PreviewSource {
    data class Real(val name: String, val atMillis: Long) : PreviewSource
    data object Example : PreviewSource
}

internal fun previewSource(nextName: String?, nextMillis: Long?, nowMillis: Long): PreviewSource =
    // Ein Ziel in der Vergangenheit ergaebe eine negative Restzeit und damit
    // ein Symbol, das es nie gibt.
    if (nextName != null && nextMillis != null && nextMillis > nowMillis) {
        PreviewSource.Real(nextName, nextMillis)
    } else {
        PreviewSource.Example
    }

/** Die Restzeit des Beispiels: 41 Minuten — mitten in den Zehnerstufen, weit
 *  genug von jeder Grenze, dass das Bild nicht beim Hinsehen umspringt. */
internal const val PREVIEW_EXAMPLE_MINUTES = 41L
