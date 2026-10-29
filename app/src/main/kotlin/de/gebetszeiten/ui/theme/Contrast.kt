package de.gebetszeiten.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

private fun channel(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun luminance(c: Color): Double =
    0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

/** Kontrastverhältnis nach WCAG 2.x (sRGB-Linearisierung), 1.0 bis 21.0. */
fun contrastRatio(fg: Color, bg: Color): Double {
    val a = luminance(fg)
    val b = luminance(bg)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}
