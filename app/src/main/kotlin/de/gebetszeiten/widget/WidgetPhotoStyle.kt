package de.gebetszeiten.widget

import androidx.compose.ui.graphics.Color

/**
 * Farben für Schrift über dem Foto im großen Widget. Das Foto liegt unter
 * einer schwarzen Abdunklung; die Schrift ist immer hell, in Tag und Nacht.
 */
internal object WidgetPhotoStyle {
    /** Deckkraft der schwarzen Abdunklung über dem Foto. */
    const val SCRIM_ALPHA = 0.6f

    val Scrim = Color.Black.copy(alpha = SCRIM_ALPHA)

    /** Helle Schrift (Nacht-Tinte). */
    val Text = Color(0xFFF7F1E8)
}
