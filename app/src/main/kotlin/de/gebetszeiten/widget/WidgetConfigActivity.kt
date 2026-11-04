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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
 * eigene Werte gelten die aus den App-Einstellungen. So können zwei Widgets
 * verschieden aussehen.
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
            setContent {
                GebetszeitenTheme {
                    WidgetConfigScreen(
                        start = start,
                        onCancel = { finish() },
                        onDone = { chosen ->
                            lifecycleScope.launch {
                                save(glanceId, chosen)
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

    private suspend fun save(glanceId: androidx.glance.GlanceId, chosen: AppSettings) {
        updateAppWidgetState(this, glanceId) { prefs ->
            prefs[NextPrayerWidget.CHOICE_THEME] = chosen.widgetTheme
            prefs[NextPrayerWidget.CHOICE_FORMAT] = chosen.widgetFormat
            prefs[NextPrayerWidget.CHOICE_OPACITY] = chosen.widgetOpacity
            prefs[NextPrayerWidget.REFRESH] = (prefs[NextPrayerWidget.REFRESH] ?: 0L) + 1
        }
        NextPrayerWidget().update(this, glanceId)
    }
}

@Composable
private fun WidgetConfigScreen(start: AppSettings, onCancel: () -> Unit, onDone: (AppSettings) -> Unit) {
    val t = LocalTokens.current
    var theme by remember { mutableStateOf(WidgetTheme.of(start.widgetTheme)) }
    var format by remember { mutableStateOf(WidgetFormat.of(start.widgetFormat)) }
    var opacity by remember { mutableFloatStateOf(start.widgetOpacity.toFloat()) }

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
        WidgetPreview(theme, format, opacity.roundToInt())

        Text(stringResource(R.string.settings_widget_theme), style = MaterialTheme.typography.bodyLarge, color = t.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetTheme.entries.forEach {
                SelectChip(selected = theme == it, onClick = { theme = it }, label = stringResource(it.labelRes))
            }
        }
        Text(stringResource(R.string.settings_widget_format), style = MaterialTheme.typography.bodyLarge, color = t.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetFormat.entries.forEach {
                SelectChip(selected = format == it, onClick = { format = it }, label = stringResource(it.labelRes))
            }
        }
        Text(
            stringResource(R.string.settings_widget_opacity, opacity.roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
            color = t.ink,
        )
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
                    ),
                )
            }) { Text(stringResource(R.string.widget_config_done)) }
        }
    }
}

/**
 * Ungefähre Vorschau im 2×1-Format auf einem dunklen Verlauf als
 * Hintergrundbild-Ersatz. Beispielwerte, keine echten Zeiten; „Wie das
 * Tagesbild“ zeigt hier die Nacht-Farben, im Widget die des Fotos.
 */
@Composable
private fun WidgetPreview(theme: WidgetTheme, format: WidgetFormat, opacity: Int) {
    val dark = LocalTokens.current.canvas == de.gebetszeiten.ui.theme.Palette.Night.canvas
    val tokens: Tokens = if (dark) theme.tokens.second else theme.tokens.first
    val title = stringResource(R.string.prayer_dhuhr)
    val lead = stringResource(R.string.hero_remaining_lead)
    val nextLead = stringResource(R.string.hero_next_lead)
    val next = stringResource(R.string.hero_next_at, stringResource(R.string.prayer_asr), "15:25")
    Text(
        stringResource(R.string.widget_config_example),
        style = MaterialTheme.typography.labelMedium,
        color = LocalTokens.current.stone,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7FA3D9), Color(0xFF141722)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(tokens.canvas.copy(alpha = opacity / 100f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            when (format) {
                WidgetFormat.STANDARD -> {
                    Text(title, color = tokens.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("$nextLead $next", color = tokens.stone, fontSize = 13.sp)
                }
                WidgetFormat.COUNTDOWN -> {
                    Text("$title $lead", color = tokens.stone, fontSize = 13.sp)
                    Text("30 Min", color = tokens.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("$nextLead $next", color = tokens.stone, fontSize = 13.sp)
                }
                WidgetFormat.LIST -> {
                    Text(title, color = tokens.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth()) {
                        Text(title, color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("12:37", color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.prayer_asr), color = tokens.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("15:25", color = tokens.ink, fontSize = 13.sp)
                    }
                }
                WidgetFormat.SLIM -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = tokens.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${stringResource(R.string.prayer_asr)} 15:25", color = tokens.stone, fontSize = 13.sp)
                }
            }
        }
    }
}
