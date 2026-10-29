package de.gebetszeiten.ui.theme

import androidx.compose.ui.graphics.Color

/** Farbrollen der App (Oura-Stil, siehe DESIGN.md "Abweichungen"). */
data class Tokens(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val stone: Color,
    val hairline: Color,
    val accent: Color,
    val gold: Color,
    val ochre: Color,
    val error: Color,
)

/**
 * Einzige Quelle der Farben; `Theme.kt` baut daraus die Material-Schemata,
 * `PaletteContrastTest` prüft genau diese Werte.
 *
 * Gegenüber der Spec nachgedunkelt bzw. aufgehellt, damit AA (4,5:1) gilt:
 * Tag-Gold, Tag-Fehler, Nacht-Fehler (auf Fläche #19191c).
 */
object Palette {
    val Day = Tokens(
        canvas = Color(0xFFF7F1E8),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF1C1B1A),
        stone = Color(0xFF4A4741),
        hairline = Color(0xFFE1DCD3),
        accent = Color(0xFF224228),
        gold = Color(0xFF966417),
        ochre = Color(0xFFBC924C),
        error = Color(0xFFCD341F),
    )
    val Night = Tokens(
        canvas = Color(0xFF000000),
        surface = Color(0xFF19191C),
        ink = Color(0xFFF7F1E8),
        stone = Color(0xFFA8A5A0),
        hairline = Color(0xFF232326),
        accent = Color(0xFF849671),
        gold = Color(0xFFDDAA61),
        ochre = Color(0xFFBC924C),
        error = Color(0xFFE54B35),
    )
    // Hochkontrast: AAA (7:1) für Text und Akzente.
    val DayHc = Tokens(
        canvas = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF000000),
        stone = Color(0xFF33312D),
        hairline = Color(0xFF6B6760),
        accent = Color(0xFF224228),
        gold = Color(0xFF795113),
        ochre = Color(0xFF795113),
        error = Color(0xFFA62A19),
    )
    val NightHc = Tokens(
        canvas = Color(0xFF000000),
        surface = Color(0xFF000000),
        ink = Color(0xFFFFFFFF),
        stone = Color(0xFFD0CCC5),
        hairline = Color(0xFF8A8780),
        accent = Color(0xFF9DB08A),
        gold = Color(0xFFDDAA61),
        ochre = Color(0xFFDDAA61),
        error = Color(0xFFEB705F),
    )
}
