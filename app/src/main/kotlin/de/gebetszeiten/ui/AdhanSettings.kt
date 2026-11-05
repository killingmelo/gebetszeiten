package de.gebetszeiten.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.adhan.AdhanConfig
import de.gebetszeiten.adhan.AdhanService
import de.gebetszeiten.adhan.adhanPlan
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.prayer.labelRes

/**
 * Alle Aḏān-Einstellungen. Die Ersteinrichtung zeigt nur den Hauptschalter
 * ([full] = false), das Einstellungsblatt alles.
 *
 * [toggle] ist der Schalter der jeweiligen Oberfläche (ToggleRow bzw.
 * ToggleLine), damit beide Stellen wie ihre Umgebung aussehen.
 */
@Composable
internal fun AdhanSettings(
    config: AdhanConfig,
    onChange: (AdhanConfig) -> Unit,
    full: Boolean,
    toggle: @Composable (label: String, checked: Boolean, onChange: (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    toggle(stringResource(R.string.adhan_enable), config.enabled) { onChange(config.copy(enabled = it)) }
    Text(
        stringResource(if (full) R.string.adhan_hint else R.string.adhan_onboarding_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!full || !config.enabled) {
        if (config.enabled) PreviewButtons(context, config)
        return
    }

    Text(stringResource(R.string.adhan_prayers), style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach { p ->
            val on = p.name in config.prayers
            SelectChip(
                selected = on,
                onClick = { onChange(config.copy(prayers = if (on) config.prayers - p.name else config.prayers + p.name)) },
                label = stringResource(p.labelRes()),
            )
        }
    }

    Text(stringResource(R.string.adhan_voice), style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            stringResource(R.string.adhan_voice_bundled) to AdhanConfig.VOICE_BUNDLED,
            stringResource(R.string.adhan_voice_custom) to AdhanConfig.VOICE_CUSTOM,
        ).forEach { (label, v) ->
            SelectChip(selected = config.voice == v, onClick = { onChange(config.copy(voice = v)) }, label = label)
        }
    }
    if (config.voice == AdhanConfig.VOICE_CUSTOM) {
        FilePicker(context, config.customUri) { onChange(config.copy(customUri = it)) }
    }

    // Kein Schalter: die Datei IST die Wahl, „Entfernen“ nimmt sie zurueck.
    Text(stringResource(R.string.adhan_fajr), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.adhan_fajr_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilePicker(context, config.fajrUri, required = false) { onChange(config.copy(fajrUri = it)) }

    toggle(stringResource(R.string.adhan_dua), config.duaEnabled) { onChange(config.copy(duaEnabled = it)) }
    Text(
        stringResource(R.string.adhan_dua_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (config.duaEnabled) {
        FilePicker(context, config.duaUri) { onChange(config.copy(duaUri = it)) }
    }

    Text(stringResource(R.string.adhan_stream), style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            stringResource(R.string.adhan_stream_alarm) to AdhanConfig.STREAM_ALARM,
            stringResource(R.string.adhan_stream_notification) to AdhanConfig.STREAM_NOTIFICATION,
        ).forEach { (label, v) ->
            SelectChip(selected = config.stream == v, onClick = { onChange(config.copy(stream = v)) }, label = label)
        }
    }
    Text(
        stringResource(R.string.adhan_stream_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(stringResource(R.string.adhan_volume), style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            stringResource(R.string.adhan_volume_40) to 40,
            stringResource(R.string.adhan_volume_70) to 70,
            stringResource(R.string.adhan_volume_100) to 100,
        ).forEach { (label, v) ->
            SelectChip(selected = config.volumePercent == v, onClick = { onChange(config.copy(volumePercent = v)) }, label = label)
        }
    }
    Text(
        stringResource(R.string.adhan_volume_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PreviewButtons(context, config)
}

/** Probe mit genau dem, was zu Ẓuhr liefe, unabhängig von der Gebetsauswahl. */
@Composable
private fun PreviewButtons(context: Context, config: AdhanConfig) {
    val plan = adhanPlan(config.copy(enabled = true, prayers = AdhanConfig.ALL_PRAYERS), Prayer.DHUHR.name)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { plan?.let { AdhanService.play(context, it, null) } }, enabled = plan != null) {
            Text(stringResource(R.string.adhan_preview))
        }
        TextButton(onClick = { AdhanService.stop(context) }) { Text(stringResource(R.string.adhan_stop)) }
    }
}

/**
 * Wählt eine Audiodatei über den Systemdialog und behält die Leseerlaubnis
 * dauerhaft, damit der Dienst sie Wochen später noch öffnen darf.
 */
@Composable
private fun FilePicker(context: Context, uri: String?, required: Boolean = true, onPicked: (String?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked: Uri? ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPicked(picked.toString())
        }
    }
    val name = remember(uri) { uri?.let { displayName(context, it) } }
    // Ohne Datei schweigt nur eine Pflicht-Auswahl; die Faǧr-Datei ist freiwillig.
    if (name != null || required) {
        Text(
            if (name != null) stringResource(R.string.adhan_file_chosen, name) else stringResource(R.string.adhan_file_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { launcher.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.adhan_pick_file)) }
        if (uri != null) {
            TextButton(onClick = {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                onPicked(null)
            }) { Text(stringResource(R.string.adhan_remove)) }
        }
    }
}

private fun displayName(context: Context, uri: String): String = runCatching {
    context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: Uri.parse(uri).lastPathSegment ?: uri
