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
