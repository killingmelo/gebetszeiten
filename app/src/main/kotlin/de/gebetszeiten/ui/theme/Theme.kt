package de.gebetszeiten.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Material-Schema aus den Tokens; `containers` sind die fünf Flächenstufen (niedrigste bis höchste). */
private fun schemeOf(
    t: Tokens,
    other: Tokens,
    dark: Boolean,
    containers: List<Color>,
    bright: Color,
): ColorScheme {
    val primaryContainer = t.accent.copy(alpha = 0.12f).compositeOver(t.canvas)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.accent,
        onPrimary = t.canvas,
        primaryContainer = primaryContainer,
        onPrimaryContainer = t.ink,
        secondary = t.stone,
        onSecondary = t.canvas,
        secondaryContainer = t.surface,
        onSecondaryContainer = t.ink,
        tertiary = t.gold,
        onTertiary = t.canvas,
        tertiaryContainer = t.surface,
        onTertiaryContainer = t.ink,
        background = t.canvas,
        onBackground = t.ink,
        surface = t.canvas,
        onSurface = t.ink,
        surfaceVariant = t.surface,
        onSurfaceVariant = t.stone,
        outline = t.stone,
        outlineVariant = t.hairline,
        surfaceContainerLowest = containers[0],
        surfaceContainerLow = containers[1],
        surfaceContainer = containers[2],
        surfaceContainerHigh = containers[3],
        surfaceContainerHighest = containers[4],
        surfaceDim = t.canvas,
        surfaceBright = bright,
        inverseSurface = t.ink,
        inverseOnSurface = t.canvas,
        inversePrimary = other.accent,
        error = t.error,
        onError = t.canvas,
    )
}

private val DayContainers = List(5) { Palette.Day.surface }
private val NightContainers = listOf(
    Color(0xFF0B0B0C), Color(0xFF0F0F10), Color(0xFF131315), Color(0xFF161618), Color(0xFF19191C),
)
private val DayHcContainers = List(5) { Palette.DayHc.surface }
private val NightHcContainers = List(5) { Palette.NightHc.surface }

internal val LightColors = schemeOf(Palette.Day, Palette.Night, false, DayContainers, Palette.Day.surface)
internal val DarkColors = schemeOf(Palette.Night, Palette.Day, true, NightContainers, Palette.Night.surface)
private val LightHighContrast = schemeOf(Palette.DayHc, Palette.NightHc, false, DayHcContainers, Palette.DayHc.surface)
private val DarkHighContrast = schemeOf(Palette.NightHc, Palette.DayHc, true, NightHcContainers, Palette.NightHc.surface)

/** Die Farbrollen der aktiven Palette; alle Bildschirme lesen sie hier. */
val LocalTokens = staticCompositionLocalOf { Palette.Day }

/** True when the high-contrast scheme is active (read by accent colours). */
val LocalHighContrast = staticCompositionLocalOf { false }

/** True when the effective (possibly user-forced) theme is dark. Read by accent
 *  colours so they don't depend on the raw system setting. */
val LocalIsDark = staticCompositionLocalOf { false }

/** Oura-Stil (Creme/Waldgrün, nachts Schwarz). Keine dynamischen Farben,
 *  folgt der System-Einstellung hell/dunkel, mit optionalem Hochkontrast. */
@Composable
fun GebetszeitenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        darkTheme && highContrast -> DarkHighContrast
        darkTheme -> DarkColors
        highContrast -> LightHighContrast
        else -> LightColors
    }
    val tokens = when {
        darkTheme && highContrast -> Palette.NightHc
        darkTheme -> Palette.Night
        highContrast -> Palette.DayHc
        else -> Palette.Day
    }
    // Seit targetSdk 35 zeichnet die App unter die Systemleisten; deren
    // Symbole muessen der App-Hell/Dunkel-Wahl folgen (die auch gegen die
    // Systemeinstellung erzwungen sein kann) — sonst stehen im hellen Modus
    // weisse Uhr und Akku auf hellem Grund.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalIsDark provides darkTheme,
        LocalTokens provides tokens,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
