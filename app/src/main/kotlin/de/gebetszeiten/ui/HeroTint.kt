package de.gebetszeiten.ui

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Die Farbe der Restzeit auf „Heute“, abgeleitet aus dem Tagesbild.
 *
 * Reine Funktion über ARGB-Ints, damit sie im JVM-Test läuft: Farbton und
 * Sättigung kommen aus den kräftigen Pixeln des Bilds, die Helligkeit legt
 * der Modus fest (nachts hell, tags dunkel). Danach wird so lange in Richtung
 * Weiß bzw. Schwarz geschoben, bis die Farbe auf [background] mindestens
 * [minContrast] erreicht. Ein graues Bild ergibt eine fast neutrale Farbe;
 * das ist gewollt, die Farbe soll zum Bild passen, nicht es übertönen.
 */
internal fun heroTint(pixels: IntArray, dark: Boolean, background: Int, minContrast: Double = 4.5): Int {
    var x = 0.0
    var y = 0.0
    var satSum = 0.0
    var weightSum = 0.0
    for (p in pixels) {
        val (h, s, l) = rgbToHsl(p)
        // Kräftige Mitteltöne zählen, Lichter und Schatten kaum.
        val w = s * (1.0 - abs(l - 0.5) * 1.6).coerceAtLeast(0.0)
        if (w <= 0.0) continue
        val rad = Math.toRadians(h)
        x += cos(rad) * w
        y += sin(rad) * w
        satSum += s * w
        weightSum += w
    }
    val hue = if (weightSum == 0.0) 0.0 else (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    val sat = if (weightSum == 0.0) 0.0 else min(satSum / weightSum, 0.55)
    var light = if (dark) 0.78 else 0.30
    var color = hslToRgb(hue, sat, light)
    while (contrast(color, background) < minContrast) {
        light += if (dark) 0.02 else -0.02
        if (light !in 0.0..1.0) return if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        color = hslToRgb(hue, sat, light)
    }
    return color
}

/** [over] mit Deckkraft [alpha] auf [under] gelegt, wie die Abdunklung über dem Bild. */
internal fun blend(over: Int, under: Int, alpha: Double): Int {
    fun ch(shift: Int) = ((over shr shift and 0xFF) * alpha + (under shr shift and 0xFF) * (1 - alpha)).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Mittelwert aller Pixel, als Untergrund für die Kontrastprüfung. */
internal fun averageColor(pixels: IntArray): Int {
    if (pixels.isEmpty()) return 0xFF808080.toInt()
    var r = 0L
    var g = 0L
    var b = 0L
    for (p in pixels) {
        r += p shr 16 and 0xFF
        g += p shr 8 and 0xFF
        b += p and 0xFF
    }
    val n = pixels.size
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

/** WCAG-Kontrastverhältnis zweier Farben. */
internal fun contrast(a: Int, b: Int): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

internal fun luminance(c: Int): Double {
    fun lin(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(c shr 16 and 0xFF) + 0.7152 * lin(c shr 8 and 0xFF) + 0.0722 * lin(c and 0xFF)
}

private fun rgbToHsl(c: Int): Triple<Double, Double, Double> {
    val r = (c shr 16 and 0xFF) / 255.0
    val g = (c shr 8 and 0xFF) / 255.0
    val b = (c and 0xFF) / 255.0
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    if (mx == mn) return Triple(0.0, 0.0, l)
    val d = mx - mn
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> ((g - b) / d + (if (g < b) 6 else 0)) * 60
        g -> ((b - r) / d + 2) * 60
        else -> ((r - g) / d + 4) * 60
    }
    return Triple(h, s, l)
}

private fun hslToRgb(h: Double, s: Double, l: Double): Int {
    val c = (1 - abs(2 * l - 1)) * s
    val hp = h / 60.0
    val x = c * (1 - abs(hp % 2 - 1))
    val (r1, g1, b1) = when {
        hp < 1 -> Triple(c, x, 0.0)
        hp < 2 -> Triple(x, c, 0.0)
        hp < 3 -> Triple(0.0, c, x)
        hp < 4 -> Triple(0.0, x, c)
        hp < 5 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    val m = l - c / 2
    fun ch(v: Double) = ((v + m) * 255).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}
