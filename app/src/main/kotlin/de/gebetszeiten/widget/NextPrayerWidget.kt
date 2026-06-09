package de.gebetszeiten.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.gebetszeiten.R
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.ui.theme.DarkColors
import de.gebetszeiten.ui.theme.LightColors
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.labelRes
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsRepository(context).current()
        val zone = ZoneId.systemDefault()
        val next = PrayerProvider.next(context, settings, zone, ZonedDateTime.now(zone))
        val name = context.getString(next.prayer.labelRes())
        val time = next.time.format(timeFormat)
        val showCountdown = settings.showCountdown
        // elapsedRealtime base for a system-rendered count-down (no app wakeups).
        val chronoBase = SystemClock.elapsedRealtime() +
            (next.time.toInstant().toEpochMilli() - System.currentTimeMillis())

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
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
                        // System-rendered live count-down (no app wake-ups).
                        val rv = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
                            setChronometerCountDown(R.id.widget_chrono, true)
                            setChronometer(R.id.widget_chrono, chronoBase, null, true)
                        }
                        AndroidRemoteViews(rv)
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
        }
    }
}
