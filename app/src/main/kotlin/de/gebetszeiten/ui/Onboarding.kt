package de.gebetszeiten.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.notify.PrayerNotifier
import de.gebetszeiten.notify.NotificationBlock
import de.gebetszeiten.notify.PREVIEW_EXAMPLE_MINUTES
import de.gebetszeiten.notify.ongoingPreviewRows
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.ui.theme.LocalTokens
import java.time.Duration
import java.util.Locale

/**
 * Die Ersteinrichtung: Vollbild, drei Schritte, Pflicht.
 *
 * Vollbild und nicht wegtippbare Karte, weil sie unentrinnbar sein soll — und
 * eine Karte, die man nicht wegtippen kann, ist ein Vollbild mit Umweg. Der
 * Zurueck-Knopf geht hoechstens einen Schritt zurueck, nie hinaus.
 *
 * Nichts hier schreibt sofort: alle Aenderungen sammeln sich in einem
 * Entwurf, und erst „Fertig" speichert ihn. Das ist die technische Seite der
 * Zusage „bis zur Antwort bleibt alles, wie es war".
 */
@Composable
internal fun Onboarding(
    settings: AppSettings,
    onFinish: (AppSettings) -> Unit,
) {
    var step by remember { mutableStateOf(OnboardingStep.ORT) }
    // Die Vorauswahl. Fuer eine Neuinstallation steht die Dauerzeile auf AN:
    // sie kostet nachweislich keinen zusaetzlichen Weckvorgang, solange die
    // Restzeit aus bleibt, und sie ist das, wonach der Anlass dieser Aufgabe
    // gefragt hat.
    //
    // Fuer einen BESTANDSNUTZER nicht: sein gespeicherter Wert kann eine
    // bewusste Abwahl sein, und eine Vorauswahl, die sie umdreht, waere genau
    // das ungefragte Anheften, das diese ganze Ersteinrichtung vermeiden
    // soll. Er sieht den Schalter, die Vorschau und einen Satz, der sagt,
    // dass es die Anzeige gibt — mehr braucht es nicht.
    var draft by remember(settings.onboardingDone) {
        mutableStateOf(
            if (settings.onboardingFresh) settings.copy(persistentNotification = true) else settings,
        )
    }
    val copy = onboardingCopy(step, settings.onboardingFresh)

    // Nur einen Schritt zurueck. Im ersten Schritt faengt der Handler die
    // Geste ab, ohne etwas zu tun: hinaus fuehrt sie nicht.
    BackHandler { previousStep(step)?.let { step = it } }

    val t = LocalTokens.current
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Das Onboarding ist Vollbild und liegt NICHT im Scaffold der
                // Hauptansicht — es muss die System-Einzuege selbst nehmen,
                // sonst liegt die Schrittzahl unter der Statusleiste (am
                // Emulator gesehen). `safeDrawing` deckt Statusleiste,
                // Navigationsleiste, Display-Ausschnitt UND Tastatur ab.
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Schrittzahl als Großbuchstaben-Zeile wie die Abschnittsköpfe auf „Heute“.
            Text(
                stringResource(
                    R.string.onboarding_progress,
                    OnboardingStep.values().indexOf(step) + 1,
                    OnboardingStep.values().size,
                ).uppercase(Locale.GERMAN),
                style = MaterialTheme.typography.labelSmall,
                color = t.stone,
            )
            Text(copy.headline, style = MaterialTheme.typography.headlineSmall, color = t.ink)
            Text(
                copy.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = t.stone,
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineBelow(t.hairline)
                    .padding(bottom = 16.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    // Haarlinie über den Knöpfen: der Inhalt endet sichtbar hier.
                    .hairlineBelow(t.hairline)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    OnboardingStep.ORT -> OnboardingPlaceStep(draft) { draft = it }
                    OnboardingStep.ANZEIGE -> OnboardingDisplayStep(draft) { draft = it }
                    OnboardingStep.ERINNERUNGEN -> OnboardingReminderStep(draft) { draft = it }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                previousStep(step)?.let { vorher ->
                    TextButton(onClick = { step = vorher }) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = canContinue(step, draft),
                    onClick = {
                        val weiter = nextStep(step)
                        if (weiter == null) onFinish(draft) else step = weiter
                    },
                ) { Text(onboardingContinueLabel(step)) }
            }
        }
    }
}

@Composable
private fun OnboardingPlaceStep(draft: AppSettings, onChange: (AppSettings) -> Unit) {
    Text(
        stringResource(R.string.onboarding_place_current, draft.city),
        style = MaterialTheme.typography.bodyLarge,
    )
    PlacePicker(
        useOnline = draft.useOnline,
        calculationFillsGaps = draft.calculationFillsGaps,
        currentCity = draft.city,
        // Kein Autofokus: die Tastatur soll nicht ueber der Erklaerung
        // aufspringen, bevor der Nutzer sie gelesen hat.
        autoFocus = false,
        onPick = { c ->
            onChange(
                draft.copy(
                    city = c.name,
                    latitude = c.latitude,
                    longitude = c.longitude,
                    region = c.region,
                ),
            )
        },
    )
}

@Composable
private fun OnboardingDisplayStep(draft: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    // Ein Beispiel, ausdruecklich als solches beschriftet: die echten Zeiten
    // des gerade gewaehlten Orts liegen zu diesem Zeitpunkt noch nicht vor
    // (der Abruf laeuft erst nach „Fertig").
    val remaining = Duration.ofMinutes(PREVIEW_EXAMPLE_MINUTES)
    val isha = stringResource(Prayer.ISHA.labelRes())
    // Die Stufe ist hier fest, weil die Restzeit fest ist — beide aus
    // derselben Konstanten. Deshalb laesst sich der Titel VOR dem Aufruf
    // aufloesen, und `stringResource` bleibt in der Composable, wo es
    // hingehoert (`context.getString` uebersteht keinen Konfigurationswechsel).
    val titleWithStep = stringResource(
        R.string.ongoing_title_remaining,
        de.gebetszeiten.prayer.remainingStepShort(remaining),
        isha,
    )
    val rows = ongoingPreviewRows(
        remaining = remaining,
        titleWithStep = { titleWithStep },
        titleWithTime = stringResource(R.string.ongoing_title, isha, "21:31"),
        timeLine = stringResource(R.string.ongoing_at, "21:31"),
        activeLine = stringResource(R.string.ongoing_since, stringResource(Prayer.MAGHRIB.labelRes())),
        city = draft.city,
    )
    CountdownPreview(
        rows = rows,
        // Die Vorschau zeigt, was der Schalter bewirkt — sie zeigt sie auch
        // dann, wenn die Dauerzeile (noch) aus ist.
        selected = draft.countdownMode,
        exampleLabel = stringResource(R.string.onboarding_preview_example),
        onSelect = { onChange(draft.copy(countdownMode = it)) },
    )
    ToggleLine(
        label = stringResource(R.string.settings_persistent),
        checked = draft.persistentNotification,
        onChange = { onChange(draft.copy(persistentNotification = it)) },
    )
    // Hier — und nur hier — wird die Benachrichtigungs-Erlaubnis eingeholt:
    // nachdem erklaert wurde, wofuer. Genau das fehlte vorher, als der
    // Systemdialog beim allerersten Zeichnen kam.
    //
    // `nachgefragt` zwingt die Neuberechnung nach der Antwort: `blockOf`
    // liest den Systemzustand, den Compose nicht beobachtet.
    var nachgefragt by remember { mutableIntStateOf(0) }
    val block = remember(nachgefragt) { PrayerNotifier.blockOf(context) }
    val erlauben = rememberNotificationPermissionRequest { nachgefragt++ }
    if (block != NotificationBlock.NONE) {
        Text(
            de.gebetszeiten.notify.notificationBlockText(block).orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = {
                if (block == NotificationBlock.NO_PERMISSION) {
                    erlauben()
                } else {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(de.gebetszeiten.notify.notificationBlockAction(block).orEmpty()) }
    }
}

@Composable
private fun OnboardingReminderStep(draft: AppSettings, onChange: (AppSettings) -> Unit) {
    listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach { p ->
        ToggleLine(
            label = stringResource(p.labelRes()),
            checked = p.name in draft.reminders,
            onChange = { an ->
                onChange(
                    draft.copy(
                        reminders = if (an) draft.reminders + p.name else draft.reminders - p.name,
                    ),
                )
            },
        )
    }
    Text(stringResource(R.string.settings_style), style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            stringResource(R.string.reminder_style_silent) to AppSettings.STYLE_SILENT,
            stringResource(R.string.reminder_style_vibrate) to AppSettings.STYLE_VIBRATE,
            stringResource(R.string.reminder_style_sound) to AppSettings.STYLE_SOUND,
        ).forEach { (label, v) ->
            SelectChip(
                selected = draft.reminderStyle == v,
                onClick = { onChange(draft.copy(reminderStyle = v)) },
                label = label,
            )
        }
    }
    Text(stringResource(R.string.settings_lead), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.settings_lead_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            stringResource(R.string.lead_off) to 0,
            stringResource(R.string.lead_5) to 5,
            stringResource(R.string.lead_10) to 10,
            stringResource(R.string.lead_15) to 15,
            stringResource(R.string.lead_30) to 30,
        ).forEach { (label, v) ->
            SelectChip(
                selected = draft.reminderLeadMinutes == v,
                onClick = { onChange(draft.copy(reminderLeadMinutes = v)) },
                label = label,
            )
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // Wie ToggleRow in den Einstellungen: die ganze Zeile schaltet.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .hairlineBelow(LocalTokens.current.hairline)
            .toggleable(
                value = checked,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onChange,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
}
