package de.gebetszeiten.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.notify.PrayerNotifier
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.widget.NextPrayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/** Re-establishes alarms and refreshes the widget after the device reboots or
 *  the clock / time zone changes (which would otherwise leave stale alarms). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsRepository(context).current()
                val zone = ZoneId.systemDefault()
                val now = ZonedDateTime.now(zone)
                PrayerNotifier.ensureChannel(context)
                val active = PrayerProvider.currentlyActive(context, settings, zone, now)
                    ?.takeIf { it.prayer != de.gebetszeiten.core.prayertimes.Prayer.SUNRISE }
                PrayerNotifier.updateOngoing(
                    context,
                    PrayerProvider.nextPrayer(context, settings, zone, now),
                    settings.persistentNotification,
                    settings.showCountdown,
                    active,
                    replacesEntry = settings.reminderStyle == de.gebetszeiten.data.AppSettings.STYLE_SILENT,
                    activeUntil = PrayerProvider.activeUntil(context, settings, zone, now, active),
                    exact = settings.remainingPrecision == de.gebetszeiten.data.AppSettings.PRECISION_EXACT,
                )
                NextPrayerWidget().updateAll(context)
                PrayerAlarmScheduler.scheduleNext(context, settings, zone)
            } finally {
                pending.finish()
            }
        }
    }
}
