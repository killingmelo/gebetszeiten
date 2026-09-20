package de.gebetszeiten.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import de.gebetszeiten.R
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.notify.OngoingPreviewRow
import de.gebetszeiten.notify.countdownIconRes
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Das Bild zur Entscheidung: eine nachgebaute Statusleiste und eine
 * nachgebaute Benachrichtigung, beide aus den ECHTEN Mitteln.
 *
 * Das Symbol kommt ueber [countdownIconRes] aus derselben Vektordatei, die
 * spaeter wirklich in der Statusleiste steht; die Texte aus derselben
 * Funktion, die die echte Anzeige baut
 * ([de.gebetszeiten.notify.ongoingPreviewRows] -> `ongoing`). Keine
 * gemalten Bildschirmfotos, nichts Nachgebautes, das veralten koennte.
 *
 * Die Statusleiste wird ABSICHTLICH nur EINMAL gezeichnet, ueber allen drei
 * Stufen: „Stufen" und „Genau" zeigen dasselbe Symbol, und das soll man
 * sehen, nicht lesen muessen. Bewiesen in `OngoingPreviewTest`.
 */
@Composable
internal fun CountdownPreview(
    rows: List<OngoingPreviewRow>,
    selected: String,
    exampleLabel: String?,
    onSelect: (String) -> Unit,
) {
    val row = rows.firstOrNull { it.mode == selected } ?: rows.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusBarStrip(countdownIconRes(row.glyph))
        Text(
            stringResource(R.string.onboarding_preview_same_icon),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShadeCardPreview(row)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.countdown_off) to AppSettings.COUNTDOWN_OFF,
                stringResource(R.string.countdown_steps) to AppSettings.PRECISION_STEPS,
                stringResource(R.string.countdown_exact) to AppSettings.PRECISION_EXACT,
            ).forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
        if (exampleLabel != null) {
            Text(
                exampleLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Der nachgebaute Leistenbalken. Absichtlich schlicht: er soll zeigen, WAS
 * dort steht, nicht so tun, als waere er die echte Systemleiste.
 */
@Composable
private fun StatusBarStrip(iconRes: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = stringResource(R.string.onboarding_preview_statusbar_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.onboarding_preview_statusbar_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Die nachgebaute Benachrichtigung — antippbar, damit „runterziehen"
 * erlebbar wird und nicht nur behauptet.
 *
 * Bei „Genau" laeuft hier ein echter Sekundenzaehler, weil genau das der
 * Unterschied zu „Stufen" ist. Er ist an den Lebenszyklus gebunden wie der
 * Minutentakt in `HeuteContent`: ohne das liefe er im zwischengespeicherten
 * Hintergrundprozess weiter.
 */
@Composable
private fun ShadeCardPreview(row: OngoingPreviewRow) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.texts.subText?.let {
                    Text(
                        "· $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row.showsChronometer) {
                    Text(
                        rememberTickingCountdown(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(row.texts.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                if (expanded) row.texts.bigText else (row.texts.contentText ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    if (expanded) R.string.onboarding_preview_collapse else R.string.onboarding_preview_expand,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Der Sekundenzaehler der Vorschau — dasselbe, was spaeter der Systemzaehler
 * zeichnet, nur hier von Hand, weil `setUsesChronometer` es ausserhalb einer
 * echten Benachrichtigung nicht gibt.
 *
 * Er laeuft vom Beispielwert abwaerts und beginnt von vorn, statt bei null zu
 * stehen: die Vorschau soll zeigen, DASS es sekundenweise zaehlt.
 */
@Composable
private fun rememberTickingCountdown(): String {
    val gesamt = Duration.ofMinutes(de.gebetszeiten.notify.PREVIEW_EXAMPLE_MINUTES).seconds
    var sekunden by remember { mutableLongStateOf(gesamt) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                delay(1_000)
                sekunden = if (sekunden <= 1) gesamt else sekunden - 1
            }
        }
    }
    return "%d:%02d:%02d".format(sekunden / 3600, (sekunden % 3600) / 60, sekunden % 60)
}
