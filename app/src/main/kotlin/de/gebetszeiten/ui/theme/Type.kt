package de.gebetszeiten.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.gebetszeiten.R

/** Serife für Gebetsnamen und Zeiten (Noto Serif Display, siehe DESIGN.md). */
val SerifFamily = FontFamily(
    Font(R.font.serif_light, FontWeight.Light),
    Font(R.font.serif_regular, FontWeight.Normal),
)

/** Grotesk für Fließtext und Bedienelemente (Inter). */
val SansFamily = FontFamily(
    Font(R.font.sans_regular, FontWeight.Normal),
    Font(R.font.sans_medium, FontWeight.Medium),
    Font(R.font.sans_semibold, FontWeight.SemiBold),
)

/** Arabische Schrift (Noto Naskh Arabic). */
val NaskhFamily = FontFamily(Font(R.font.naskh_regular, FontWeight.Normal))

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Light,
        fontSize = 56.sp, lineHeight = 1.05.em,
    ),
    headlineSmall = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Light,
        fontSize = 26.sp, lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // Großbuchstaben-Zeilen (Abschnittsköpfe)
    labelSmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp,
    ),
    // Die übrigen Material-Rollen in Inter, damit Knöpfe, Chips, Textfelder
    // und Kleintext nicht auf Roboto zurückfallen. Größen wie die Material-Vorgabe.
    headlineMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    // Knöpfe und Chips
    labelLarge = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
)
