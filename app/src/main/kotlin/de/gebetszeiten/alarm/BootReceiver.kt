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
                PrayerNotifier.ensureChannel(context)
                PrayerNotifier.updateOngoing(
                    context,
                    PrayerProvider.nextPrayer(context, settings, zone, ZonedDateTime.now(zone)),
                    settings.persistentNotification,
                )
                NextPrayerWidget().updateAll(context)
                PrayerAlarmScheduler.scheduleNext(context, settings, zone)
            } finally {
                pending.finish()
            }
        }
    }
}
