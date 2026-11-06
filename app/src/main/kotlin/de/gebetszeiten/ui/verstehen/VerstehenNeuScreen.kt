package de.gebetszeiten.ui.verstehen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.ui.theme.LocalTokens

/**
 * Neue Verstehen-Seite. Vorerst ein leerer Platzhalter mit derselben Signatur
 * wie [VerstehenScreen]; sichtbar nur, wenn `BuildConfig.VERSTEHEN_NEU` gesetzt ist.
 */
@Composable
fun VerstehenNeuScreen(
    inner: PaddingValues,
    settings: AppSettings,
    focus: Section?,
    onFocusConsumed: () -> Unit,
) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxSize().background(t.canvas).padding(inner))
}
