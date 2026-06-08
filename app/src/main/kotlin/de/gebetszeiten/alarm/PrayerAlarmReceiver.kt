package de.gebetszeiten.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.notify.PrayerNotifier
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.widget.NextPrayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Fires at each prayer transition: posts the silent notification (except at
 * sunrise, which only ends Fajr), advances the widget to the next prayer, and
 * schedules the following alarm.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsRepository(context).current()
                val zone = ZoneId.systemDefault()
                val active = PrayerProvider.currentlyActive(context, settings, zone, ZonedDateTime.now(zone))
                if (active != null && active.prayer != Prayer.SUNRISE) {
                    PrayerNotifier.notifyPrayer(context, active)
                }
                NextPrayerWidget().updateAll(context)
                PrayerAlarmScheduler.scheduleNext(context, settings, zone)
            } finally {
                pending.finish()
            }
        }
    }
}
