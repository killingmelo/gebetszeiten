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
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.ui.theme.DarkColors
import de.gebetszeiten.ui.theme.LightColors
import de.gebetszeiten.prayer.PrayerProvider
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
        val showCountdown = settings.showCountdown
        // Floor-rounded remaining step ("noch 2+ Std") — static between the
        // display-step alarms, unlike a chronometer that redraws every second.
        val remainingLabel = remainingStepLabel(java.time.Duration.between(now, next.time))
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

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                if (LocalSize.current.width >= FULL.width) {
                    DayPlanContent(dayPlan, transparent)
                } else {
                    NextPrayerContent(name, time, showCountdown, remainingLabel, transparent)
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
        name: String,
        time: String,
        showCountdown: Boolean,
        remainingLabel: String,
        transparent: Boolean,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(transparent))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Nächstes Gebet",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(1.dp))
            if (showCountdown) {
                Text(
                    text = remainingLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "um $time",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            } else {
                Text(
                    text = time,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }

    /** All six times of today as a 3×2 grid; the next prayer is accented. */
    @Composable
    private fun DayPlanContent(entries: List<DayEntry>, transparent: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(transparent))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
