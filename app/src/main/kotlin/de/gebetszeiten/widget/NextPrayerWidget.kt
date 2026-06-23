package de.gebetszeiten.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.gebetszeiten.R
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.ui.theme.DarkColors
import de.gebetszeiten.ui.theme.LightColors
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.hijriTextShort
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.prayer.remainingStepLabel
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Ultra-sparse widget for the home screen and (Android 16 QPR1+) lock screen.
 * Shows only the next prayer's name and time. It is redrawn event-driven — at
 * each prayer transition the alarm receiver calls [GlanceAppWidget.updateAll],
 * so there is no periodic refresh.
 */
class NextPrayerWidget : GlanceAppWidget() {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    // Two breakpoints: the sparse next-prayer view and (when the user resizes
    // the widget large enough) a full day plan. Glance re-renders only at the
    // existing event-driven updates — size changes are handled by the system.
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    private companion object {
        val COMPACT = DpSize(110.dp, 40.dp)
        val FULL = DpSize(220.dp, 120.dp)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsRepository(context).current()
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val next = PrayerProvider.next(context, settings, zone, now)
        val name = context.getString(next.prayer.labelRes())
        val time = next.time.format(timeFormat)
        // Per-surface remaining-time mode: the widget has its own OFF/STEPS/EXACT.
        val showCountdown = settings.widgetCountdown != de.gebetszeiten.data.AppSettings.COUNTDOWN_OFF
        val exact = settings.widgetCountdown == de.gebetszeiten.data.AppSettings.PRECISION_EXACT
        val karahaLine = de.gebetszeiten.prayer.KarahaDisplay.line(context, settings, zone, now)
        // Floor-rounded remaining step ("noch 2+ Std") — static between the
        // display-step alarms; EXACT mode uses the system chronometer instead.
        val remaining = java.time.Duration.between(now, next.time)
        val remainingLabel = remainingStepLabel(remaining)
        val urgent = de.gebetszeiten.prayer.isUrgent(remaining)
        // elapsedRealtime base for the system-rendered countdown (EXACT mode).
        val chronoBase = android.os.SystemClock.elapsedRealtime() +
            (next.time.toInstant().toEpochMilli() - System.currentTimeMillis())
        // Day plan for the large layout: label, time, is-it-the-next-prayer.
        val dayPlan = PrayerProvider.daily(context, settings, now.toLocalDate(), zone)
            .ordered()
            .map { (prayer, at) ->
                DayEntry(
                    label = context.getString(prayer.labelRes()),
                    time = at.format(timeFormat),
                    isNext = prayer == next.prayer && at == next.time,
                )
            }

        val transparent = settings.widgetTransparent
        // Header for the large layout: makes a silently wrong city visible and
        // doubles as the Hijri date line.
        val header = "${settings.city} · ${hijriTextShort(now.toLocalDate(), settings.hijriOffsetDays)}"

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                if (LocalSize.current.width >= FULL.width) {
                    DayPlanContent(dayPlan, header, transparent, karahaLine)
                } else {
                    NextPrayerContent(
                        context, name, time, showCountdown, exact,
                        remainingLabel, urgent, chronoBase, transparent, karahaLine,
                    )
                }
            }
        }
    }

    @Composable
    private fun widgetBackground(transparent: Boolean) = if (transparent) {
        androidx.glance.color.ColorProvider(
            day = LightColors.surface.copy(alpha = 0.45f),
            night = DarkColors.surface.copy(alpha = 0.45f),
        )
    } else {
        GlanceTheme.colors.widgetBackground
    }

    private data class DayEntry(val label: String, val time: String, val isNext: Boolean)

    @Composable
    private fun NextPrayerContent(
        context: Context,
        name: String,
        time: String,
        showCountdown: Boolean,
        exact: Boolean,
        remainingLabel: String,
        urgent: Boolean,
        chronoBase: Long,
        transparent: Boolean,
        karahaLine: de.gebetszeiten.prayer.KarahaLine?,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(transparent))
                .padding(14.dp)
                // The whole widget opens the app — expected tap behaviour.
                .clickable(actionStartActivity<de.gebetszeiten.ui.MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // One line, no label: the user placed this widget knowingly, and
            // "um/noch" carries the future semantics — gains font size instead.
            Text(
                text = "$name · $time",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (showCountdown) {
                Spacer(GlanceModifier.height(2.dp))
                if (exact) {
                    // Live countdown, drawn by the system — no app wake-ups.
                    val rv = android.widget.RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
                        setChronometerCountDown(R.id.widget_chrono, true)
                        setChronometer(R.id.widget_chrono, chronoBase, null, true)
                        // Final minutes: same urgency cue the STEPS countdown gets
                        // (the EXACT chronometer otherwise stayed textColorPrimary).
                        if (urgent) setTextColor(R.id.widget_chrono, context.getColor(R.color.widget_urgent))
                    }
                    AndroidRemoteViews(rv)
                } else {
                    Text(
                        text = remainingLabel,
                        style = TextStyle(
                            // Final minutes: urgency colour.
                            color = if (urgent) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
            karahaLine?.let { KarahaText(it, fontSize = 13) }
        }
    }

    /** "⚠️ Karaha bis/ab HH:MM" — error colour inside a window, dimmed before. */
    @Composable
    private fun KarahaText(line: de.gebetszeiten.prayer.KarahaLine, fontSize: Int) {
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = line.text,
            style = TextStyle(
                color = if (line.active) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }

    /** All six times of today as a 3×2 grid; the next prayer is accented. */
    @Composable
    private fun DayPlanContent(
        entries: List<DayEntry>,
        header: String,
        transparent: Boolean,
        karahaLine: de.gebetszeiten.prayer.KarahaLine?,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(transparent))
                .padding(14.dp)
                .clickable(actionStartActivity<de.gebetszeiten.ui.MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            karahaLine?.let { KarahaText(it, fontSize = 11) }
            Spacer(GlanceModifier.height(8.dp))
            entries.chunked(3).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(GlanceModifier.height(10.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    row.forEachIndexed { colIndex, entry ->
                        if (colIndex > 0) Spacer(GlanceModifier.width(12.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = entry.label,
                                style = TextStyle(
                                    color = if (entry.isNext) GlanceTheme.colors.primary
                                    else GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = if (entry.isNext) FontWeight.Bold else FontWeight.Medium,
                                ),
                            )
                            Text(
                                text = entry.time,
                                style = TextStyle(
                                    color = if (entry.isNext) GlanceTheme.colors.primary
                                    else GlanceTheme.colors.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
