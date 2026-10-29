package de.gebetszeiten.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.Tokens

/** Fläche eines gewählten Chips: Akzent zu 12 % über dem Grund. */
internal fun selectedChipFill(t: Tokens): Color = t.accent.copy(alpha = 0.12f).compositeOver(t.canvas)

/**
 * Der eine Auswahl-Chip für Einstellungen und Ersteinrichtung. Die Material-
 * Vorgabe färbte einen gewählten Chip mit `secondaryContainer` (= surface,
 * weiß auf Creme, kaum sichtbar). Hier trägt der gewählte Chip ein Häkchen
 * (unabhängig von Farbe), einen Akzentrand und eine leicht grüne Fläche.
 */
@Composable
internal fun SelectChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val t = LocalTokens.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                // Kein eigener Text für TalkBack: der Chip meldet „ausgewählt“ selbst.
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        trailingIcon = trailingIcon,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = t.ink,
            iconColor = t.stone,
            selectedContainerColor = selectedChipFill(t),
            selectedLabelColor = t.ink,
            selectedLeadingIconColor = t.accent,
            selectedTrailingIconColor = t.ink,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = t.hairline,
            selectedBorderColor = t.accent,
            selectedBorderWidth = 1.5.dp,
        ),
    )
}
