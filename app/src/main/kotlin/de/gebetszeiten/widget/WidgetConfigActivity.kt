package de.gebetszeiten.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import de.gebetszeiten.R
import de.gebetszeiten.alarm.PrayerAlarmScheduler
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.ui.SelectChip
import de.gebetszeiten.ui.theme.GebetszeitenTheme
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.Tokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Einstellungen für EIN Widget: öffnet sich beim Hinzufügen und später über
 * „Widget bearbeiten“ (Android 12+, `reconfigurable`). Die Werte liegen im
 * Glance-Zustand dieses Widgets ([NextPrayerWidget.CHOICE_THEME] usw.); ohne
 * eigene Werte gelten die aus den App-Einstellungen bzw. die Vorgaben aus
 * [WidgetOptions]. So können zwei Widgets verschieden aussehen.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Abbrechen (Zurück) heißt beim Hinzufügen: kein Widget.
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            val settings = SettingsRepository(this@WidgetConfigActivity).current()
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            val state = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            val start = NextPrayerWidget.withWidgetChoice(settings, state)
            val startOptions = NextPrayerWidget.optionsOf(state)
            setContent {
                GebetszeitenTheme {
                    WidgetConfigScreen(
                        start = start,
                        startOptions = startOptions,
                        onCancel = { finish() },
                        onDone = { chosen, options ->
                            lifecycleScope.launch {
                                save(glanceId, chosen, options)
                                PrayerAlarmScheduler.scheduleNext(this@WidgetConfigActivity, settings)
                                setResult(RESULT_OK, result)
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    private suspend fun save(glanceId: androidx.glance.GlanceId, chosen: AppSettings, options: WidgetOptions) {
        updateAppWidgetState(this, glanceId) { prefs ->
            prefs[NextPrayerWidget.CHOICE_THEME] = chosen.widgetTheme
            prefs[NextPrayerWidget.CHOICE_FORMAT] = chosen.widgetFormat
            prefs[NextPrayerWidget.CHOICE_OPACITY] = chosen.widgetOpacity
            prefs[NextPrayerWidget.CHOICE_COUNTDOWN] = chosen.countdownMode
            prefs[NextPrayerWidget.CHOICE_ALIGN] = options.align.key
            prefs[NextPrayerWidget.CHOICE_SHURUQ] = options.shuruq
            prefs[NextPrayerWidget.CHOICE_DETAILS] = options.details
            prefs[NextPrayerWidget.CHOICE_NEXT] = options.next
            prefs[NextPrayerWidget.CHOICE_HEADER] = options.header
            prefs[NextPrayerWidget.REFRESH] = (prefs[NextPrayerWidget.REFRESH] ?: 0L) + 1
        }
        NextPrayerWidget().update(this, glanceId)
    }
}

/** Die drei Restzeit-Arten in der Reihenfolge der App-Einstellungen. */
private val countdownModes = listOf(
    AppSettings.COUNTDOWN_OFF to R.string.countdown_off,
    AppSettings.PRECISION_STEPS to R.string.countdown_steps,
    AppSettings.PRECISION_EXACT to R.string.countdown_exact,
)

@Composable
private fun WidgetConfigScreen(
    start: AppSettings,
    startOptions: WidgetOptions,
    onCancel: () -> Unit,
    onDone: (AppSettings, WidgetOptions) -> Unit,
) {
    val t = LocalTokens.current
    var theme by remember { mutableStateOf(WidgetTheme.of(start.widgetTheme)) }
    var format by remember { mutableStateOf(WidgetFormat.of(start.widgetFormat)) }
    var opacity by remember { mutableFloatStateOf(start.widgetOpacity.toFloat()) }
    var countdown by remember { mutableStateOf(start.countdownMode) }
    var options by remember { mutableStateOf(startOptions) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.widget_config_title),
            style = MaterialTheme.typography.headlineMedium,
            color = t.ink,
        )
        WidgetPreview(theme, format, opacity.roundToInt(), countdown, options)

        Heading(stringResource(R.string.settings_widget_theme), t)
        Chips(WidgetTheme.entries, theme, { stringResource(it.labelRes) }) { theme = it }

        Heading(stringResource(R.string.settings_widget_format), t)
        Chips(WidgetFormat.entries, format, { stringResource(it.labelRes) }) { format = it }

        Heading(stringResource(R.string.widget_config_countdown), t)
        Chips(countdownModes, countdownModes.first { it.first == countdown }, { stringResource(it.second) }) {
            countdown = it.first
        }

        Heading(stringResource(R.string.widget_config_content), t)
        Toggle(stringResource(R.string.widget_content_shuruq), options.shuruq, t) { options = options.copy(shuruq = it) }
        Toggle(stringResource(R.string.widget_content_details), options.details, t) { options = options.copy(details = it) }
        Toggle(stringResource(R.string.widget_content_next), options.next, t) { options = options.copy(next = it) }
        Toggle(stringResource(R.string.widget_content_header), options.header, t) { options = options.copy(header = it) }

        Heading(stringResource(R.string.widget_config_align), t)
        Chips(WidgetAlign.entries, options.align, { stringResource(it.labelRes) }) {
            options = options.copy(align = it)
        }

        Heading(stringResource(R.string.settings_widget_opacity, opacity.roundToInt()), t)
        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..100f, steps = 9)
        Text(
            stringResource(R.string.settings_widget_opacity_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = t.stone,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.widget_config_cancel)) }
            Button(onClick = {
                onDone(
                    start.copy(
                        widgetTheme = theme.key,
                        widgetFormat = format.key,
                        widgetOpacity = opacity.roundToInt(),
                        countdownMode = countdown,
                    ),
                    options,
                )
            }) { Text(stringResource(R.string.widget_config_done)) }
        }
    }
}

@Composable
private fun Heading(text: String, t: Tokens) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = t.ink)
}

@Composable
private fun <T> Chips(items: List<T>, selected: T, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { SelectChip(selected = it == selected, onClick = { onPick(it) }, label = label(it)) }
    }
}

/** Eine Zeile mit Schalter; die ganze Zeile ist die Tippfläche. */
@Composable
private fun Toggle(label: String, checked: Boolean, t: Tokens, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = t.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Ungefähre Vorschau im 2×1-Format auf einem dunklen Verlauf als
 * Hintergrundbild-Ersatz. Beispielwerte, keine echten Zeiten; „Wie das
 * Tagesbild“ zeigt hier die Nacht-Farben, im Widget die des Fotos.
 */
@Composable
private fun WidgetPreview(
    theme: WidgetTheme,
    format: WidgetFormat,
    opacity: Int,
    countdownMode: String,
    options: WidgetOptions,
) {
    val dark = LocalTokens.current.canvas == de.gebetszeiten.ui.theme.Palette.Night.canvas
    val tokens: Tokens = if (dark) theme.tokens.second else theme.tokens.first
    val title = stringResource(R.string.prayer_dhuhr)
    val lead = stringResource(R.string.hero_remaining_lead)
    val nextLead = stringResource(R.string.hero_next_lead)
    val next = stringResource(R.string.hero_next_at, stringResource(R.string.prayer_asr), "15:25")
    val showCountdown = countdownMode != AppSettings.COUNTDOWN_OFF || format == WidgetFormat.COUNTDOWN
    val align = when (options.align) {
        WidgetAlign.START -> Alignment.Start
        WidgetAlign.CENTER -> Alignment.CenterHorizontally
        WidgetAlign.END -> Alignment.End
    }
    val shape = RoundedCornerShape(24.dp)
    Text(
        stringResource(R.string.widget_config_example),
        style = MaterialTheme.typography.labelMedium,
        color = LocalTokens.current.stone,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7FA3D9), Color(0xFF141722)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .heightIn(min = 100.dp)
                .clip(shape)
                .background(tokens.canvas.copy(alpha = opacity / 100f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = align,
        ) {
            when (format) {
                WidgetFormat.STANDARD -> {
                    Text(title, color = tokens.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (showCountdown) Text("$lead 30 Min", color = tokens.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (options.next) Text("$nextLead $next", color = tokens.stone, fontSize = 13.sp)
                }
                WidgetFormat.COUNTDOWN -> {
                    Text("$title $lead", color = tokens.stone, fontSize = 13.sp)
                    Text("30 Min", color = tokens.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (options.next) Text("$nextLead $next", color = tokens.stone, fontSize = 13.sp)
                }
                WidgetFormat.LIST -> {
                    Text(title, color = tokens.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth()) {
                        Text(title, color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("12:37", color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (options.details) {
                        Text(
                            "${stringResource(R.string.sub_makruh)} 12:17 bis 12:37",
                            color = tokens.stone,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.prayer_asr), color = tokens.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("15:25", color = tokens.ink, fontSize = 13.sp)
                    }
                }
                WidgetFormat.SLIM -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = tokens.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (options.next) {
                        Text("${stringResource(R.string.prayer_asr)} 15:25", color = tokens.stone, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
