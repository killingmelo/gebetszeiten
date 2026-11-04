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
