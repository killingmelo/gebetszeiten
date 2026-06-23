package de.gebetszeiten.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = md_primary_light,
    onPrimary = md_onPrimary_light,
    primaryContainer = md_primaryContainer_light,
    onPrimaryContainer = md_onPrimaryContainer_light,
    secondary = md_secondary_light,
    onSecondary = md_onSecondary_light,
    secondaryContainer = md_secondaryContainer_light,
    onSecondaryContainer = md_onSecondaryContainer_light,
    background = md_background_light,
    onBackground = md_onBackground_light,
    surface = md_surface_light,
    onSurface = md_onSurface_light,
    surfaceVariant = md_surfaceVariant_light,
    onSurfaceVariant = md_onSurfaceVariant_light,
    outline = md_outline_light,
)

internal val DarkColors = darkColorScheme(
    primary = md_primary_dark,
    onPrimary = md_onPrimary_dark,
    primaryContainer = md_primaryContainer_dark,
    onPrimaryContainer = md_onPrimaryContainer_dark,
    secondary = md_secondary_dark,
    onSecondary = md_onSecondary_dark,
    secondaryContainer = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    background = md_background_dark,
    onBackground = md_onBackground_dark,
    surface = md_surface_dark,
    onSurface = md_onSurface_dark,
    surfaceVariant = md_surfaceVariant_dark,
    onSurfaceVariant = md_onSurfaceVariant_dark,
    outline = md_outline_dark,
)

private val LightHighContrast = lightColorScheme(
    primary = Color(0xFF0A5C46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0E7A5F),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF2C4A3E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBFE3D2),
    onSecondaryContainer = Color(0xFF00150C),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFDDE6E0),
    onSurfaceVariant = Color(0xFF1A1A1A),
    outline = Color(0xFF3A3A3A),
)

private val DarkHighContrast = darkColorScheme(
    primary = Color(0xFFA7F2DA),
    onPrimary = Color(0xFF00150C),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFCEE9DA),
    onSecondary = Color(0xFF00150C),
    secondaryContainer = Color(0xFF2C463C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFE8E8E8),
    outline = Color(0xFFB8B8B8),
)

/** True when the high-contrast scheme is active (read by accent colours). */
val LocalHighContrast = staticCompositionLocalOf { false }

/** True when the effective (possibly user-forced) theme is dark. Read by accent
 *  colours so they don't depend on the raw system setting. */
val LocalIsDark = staticCompositionLocalOf { false }

/** Branded calm-green theme. Keeps a consistent identity (no dynamic color),
 *  follows the system light/dark setting, with an optional high-contrast mode. */
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
    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalIsDark provides darkTheme,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
