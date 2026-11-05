package de.gebetszeiten.widget

/** Welche Nebenzeilen das kleine Widget zeigt. */
internal data class CompactLines(val karaha: Boolean, val next: Boolean)

/** Zeilenhöhe je sp bei Schriftgröße 1,0 (Roboto samt Zeilenabstand), am
 *  Emulator nachgemessen: drei Zeilen 24/15/13 sp belegen rund 66 dp. */
private const val LINE_PER_SP = 1.25f

/** Innenabstand oben und unten zusammen (CompactContent: je 8 dp). */
private const val PADDING_DP = 16f

/**
 * Was ins kleine Widget passt, ohne dass die letzte Zeile abgeschnitten wird.
 * Titel und Restzeit stehen immer; danach hat die Karāha-Zeile Vorrang (sie
 * ist zeitkritisch), „dann …“ fällt zuerst weg (das große Widget zeigt es).
 * Bei Schriftgröße 1,4 fehlt auf einem 2×1-Widget der Platz für alle Zeilen.
 */
/** Mittlere Zeichenbreite je sp (Roboto Medium, gemischter Text mit Ziffern),
 *  bewusst eher großzügig: lieber einmal zu früh kürzen als umbrechen. */
private const val CHAR_PER_SP = 0.58f

/** Innenabstand links und rechts zusammen (CompactContent: je 16 dp). */
private const val PADDING_H_DP = 32f

/**
 * „dann ʿAṣr um 15:38“ oder, wenn das in der Breite nicht in eine Zeile
 * passt, die Kurzform „ʿAṣr 15:38“. Ein Umbruch wäre schlimmer als die
 * Kurzform: [compactLines] rechnet mit EINER Zeile, die zweite fiel unten aus
 * dem Widget, und mit ihr die Uhrzeit.
 */
internal fun compactNextLine(
    full: String,
    short: String,
    widthDp: Float,
    fontScale: Float,
    sp: Int = 13,
): String {
    val available = widthDp - PADDING_H_DP
    fun width(text: String) = text.length * sp * CHAR_PER_SP * fontScale
    return if (width(full) <= available) full else short
}

/**
 * Schriftgröße der großen Restzeit („10+ Min“) im Aufbau „Restzeit groß“:
 * 28 sp, wenn sie in die Breite passt, sonst 22 oder 18. Mit Auslassungspunkten
 * gekürzt („noch 10+ …“) war sie im 2×1-Widget wertlos.
 */
internal fun bigCountdownSp(text: String, widthDp: Float, fontScale: Float): Int {
    val available = widthDp - PADDING_H_DP
    return listOf(28, 22, 18).firstOrNull { sp -> text.length * sp * CHAR_PER_SP * fontScale <= available } ?: 18
}

/**
 * Welche Zeilen die „Tagesliste“ zeigt, wenn nicht alle [size] in die Höhe
 * passen ([fits]): ab dem laufenden Gebet, damit die kommenden sichtbar
 * bleiben; reicht es bis zum Ende, wird nach oben aufgefüllt.
 */
internal fun listWindow(size: Int, currentIndex: Int, fits: Int): IntRange {
    if (fits >= size) return 0 until size
    val n = fits.coerceAtLeast(1)
    val start = currentIndex.coerceAtLeast(0).coerceAtMost(size - n)
    return start until start + n
}

/**
 * Wie [listWindow], aber eine Zeile mit Nebenzeiten darunter zählt doppelt
 * ([weights]). Ab dem laufenden Gebet so viele, wie in [fits] Zeilen passen;
 * bleibt Platz, wird nach oben aufgefüllt. Mindestens ein Eintrag.
 */
internal fun listWindowWeighted(weights: List<Int>, currentIndex: Int, fits: Int): IntRange {
    if (weights.isEmpty()) return IntRange.EMPTY
    var start = currentIndex.coerceIn(0, weights.size - 1)
    var end = start
    var used = weights[start]
    while (end + 1 < weights.size && used + weights[end + 1] <= fits) used += weights[++end]
    while (start - 1 >= 0 && used + weights[start - 1] <= fits) used += weights[--start]
    return start..end
}

/**
 * Ab welchem Eintrag die „Tagesliste“ zeigt: das laufende Gebet, sonst das
 * nächste noch kommende. Während Ḍuḥā oder Zawāl läuft kein Gebet; vorher
 * begann die Liste dann oben mit dem längst vergangenen Faǧr.
 */
internal fun listAnchor(current: List<Boolean>, faded: List<Boolean>): Int {
    val running = current.indexOf(true)
    if (running >= 0) return running
    val upcoming = faded.indexOf(false)
    return if (upcoming >= 0) upcoming else (faded.size - 1).coerceAtLeast(0)
}

/** Wie viele Listenzeilen (13 sp) unter die Kopfzeile (16 sp) passen. */
internal fun listRowsThatFit(heightDp: Float, fontScale: Float): Int {
    fun line(sp: Int) = sp * LINE_PER_SP * fontScale
    // Am Emulator nachgemessen: eine Zeile braucht rund 19 dp (Schrift plus
    // Innenabstand), mit 18 dp ragte die dritte im 2×1-Widget unten heraus.
    val free = heightDp - 20f - line(16) - 4f
    return (free / (line(13) + 4f)).toInt().coerceAtLeast(1)
}

internal fun compactLines(
    heightDp: Float,
    fontScale: Float,
    countdown: Boolean,
    karaha: Boolean,
    next: Boolean,
): CompactLines {
    fun line(sp: Int) = sp * LINE_PER_SP * fontScale
    var used = PADDING_DP + line(24)
    if (countdown) used += line(15) + 2f
    val withKaraha = karaha && used + line(13) + 2f <= heightDp
    if (withKaraha) used += line(13) + 2f
    val withNext = next && used + line(13) <= heightDp
    return CompactLines(karaha = withKaraha, next = withNext)
}
