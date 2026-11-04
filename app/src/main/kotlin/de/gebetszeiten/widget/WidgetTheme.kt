package de.gebetszeiten.widget

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import de.gebetszeiten.R
import de.gebetszeiten.ui.averageColor
import de.gebetszeiten.ui.blend
import de.gebetszeiten.ui.heroTint
import de.gebetszeiten.ui.theme.Palette
import de.gebetszeiten.ui.theme.Tokens

/**
 * Farbthemen des Widgets. „System“ folgt Tag und Nacht wie die App; die
 * übrigen bleiben fest, egal was das Telefon gerade zeigt. Jedes Thema hält
 * für Text, Nebentext, Akzent, Gold und Fehler mindestens 4,5:1 auf seinem
 * Grund (`WidgetThemeTest`); bei geringer Deckkraft hängt der Kontrast
 * zusätzlich vom Hintergrundbild ab, das kann die App nicht prüfen.
 */
enum class WidgetTheme(val key: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.widget_theme_system),
    PAPER("paper", R.string.widget_theme_paper),
    NIGHT("night", R.string.widget_theme_night),
    FOREST("forest", R.string.widget_theme_forest),
    SAND("sand", R.string.widget_theme_sand),
    /** Dunkler Grund und Akzent aus dem Foto des laufenden Abschnitts
     *  ([photoTokens]); wechselt mit dem Abschnitt. */
    PHOTO("photo", R.string.widget_theme_photo),
    ;

    /** Farben für helles und dunkles System; feste Themen geben zweimal dieselben. */
    val tokens: Pair<Tokens, Tokens>
        get() = when (this) {
            SYSTEM -> Palette.Day to Palette.Night
            PAPER -> Palette.Day to Palette.Day
            NIGHT -> Palette.Night to Palette.Night
            FOREST -> WidgetPalettes.Forest to WidgetPalettes.Forest
            SAND -> WidgetPalettes.Sand to WidgetPalettes.Sand
            // Ohne Bildpunkte (z. B. keine Zeiten, also kein Abschnitt): Nacht.
            PHOTO -> Palette.Night to Palette.Night
        }

    companion object {
        /** Unbekannte oder alte Werte fallen auf [SYSTEM] zurück. */
        fun of(key: String?): WidgetTheme = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Aufbau des Widgets. [STANDARD] ist der bisherige (klein oder groß je nach
 * Fläche); die anderen gelten in jeder Größe.
 */
enum class WidgetFormat(val key: String, @StringRes val labelRes: Int) {
    STANDARD("standard", R.string.widget_format_standard),
    /** Die Restzeit groß, darunter das nächste Gebet. */
    COUNTDOWN("countdown", R.string.widget_format_countdown),
    /** Alle Gebete des Tages untereinander, das laufende hervorgehoben. */
    LIST("list", R.string.widget_format_list),
    /** Eine Zeile: Abschnitt, Restzeit, nächstes Gebet. Für 4×1. */
    SLIM("slim", R.string.widget_format_slim),
    ;

    companion object {
        fun of(key: String?): WidgetFormat = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/**
 * Farben für [WidgetTheme.PHOTO] aus den Bildpunkten des Fotos: der Grund ist
 * das Bild, stark abgedunkelt (22 % seines Mittelwerts auf Schwarz), der
 * Akzent dessen kräftigster Ton, hell genug für 4,5:1 ([heroTint]). Immer
 * dunkel: ein heller Grund aus einem dunklen Nachtbild würde schlammig.
 */
internal fun photoTokens(pixels: IntArray): Tokens {
    val canvas = blend(0xFF000000.toInt(), averageColor(pixels), 0.78)
    val ink = 0xFFF7F1E8.toInt()
    return Tokens(
        canvas = Color(canvas),
        surface = Color(blend(ink, canvas, 0.08)),
        ink = Color(ink),
        stone = Color(blend(ink, canvas, 0.72)),
        hairline = Color(blend(ink, canvas, 0.15)),
        accent = Color(heroTint(pixels, dark = true, background = canvas)),
        gold = Color(0xFFDDAA61),
        ochre = Color(0xFFBC924C),
        error = Color(0xFFFF9C8A),
    )
}

/** Die beiden Themen, die es nur im Widget gibt; Farben aus der Oura-Palette (DESIGN.md). */
internal object WidgetPalettes {
    /** Waldgrün als Grund, Creme als Schrift. */
    val Forest = Tokens(
        canvas = Color(0xFF224228),
        surface = Color(0xFF2C4F32),
        ink = Color(0xFFF7F1E8),
        stone = Color(0xFFCED4B8),
        hairline = Color(0xFF3A5A40),
        accent = Color(0xFFDDBE78),
        gold = Color(0xFFDDAA61),
        ochre = Color(0xFFBC924C),
        error = Color(0xFFFF9C8A),
    )

    /** Heller Sand als Grund, Espresso als Schrift. */
    val Sand = Tokens(
        canvas = Color(0xFFE8D5A9),
        surface = Color(0xFFF1E3C2),
        ink = Color(0xFF39221E),
        stone = Color(0xFF5A4630),
        hairline = Color(0xFFD2BC8C),
        accent = Color(0xFF224228),
        gold = Color(0xFF684D07),
        ochre = Color(0xFFBC924C),
        error = Color(0xFF9E2A16),
    )
}
