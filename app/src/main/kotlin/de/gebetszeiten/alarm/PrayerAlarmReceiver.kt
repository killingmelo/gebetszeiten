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
                val now = ZonedDateTime.now(zone)

                if (intent.action == PrayerAlarmScheduler.ACTION_PRE_REMINDER) {
                    // Heads-up before the prayer; the transition chain stays untouched.
                    val upcoming = PrayerProvider.nextPrayer(context, settings, zone, now)
                    if (upcoming.prayer.name in settings.reminders) {
                        PrayerNotifier.notifyPre(context, upcoming, settings.reminderLeadMinutes)
                    }
                    return@launch
                }

                val active = PrayerProvider.currentlyActive(context, settings, zone, now)
                // Sunrise is no prayer; otherwise notify only if the user enabled
                // a reminder for this prayer.
                if (active != null && active.prayer != Prayer.SUNRISE &&
                    active.prayer.name in settings.reminders
                ) {
                    // Next transition (may be sunrise) ends this notification;
                    // the text line always names a real prayer.
                    val transition = PrayerProvider.next(context, settings, zone, now)
                    val nextPrayer = if (transition.prayer == Prayer.SUNRISE) {
                        PrayerProvider.next(context, settings, zone, transition.time)
                    } else {
                        transition
                    }
                    PrayerNotifier.notifyPrayer(
                        context,
                        active,
                        nextPrayer,
                        transition.time.toInstant().toEpochMilli(),
                    )
                }
                PrayerNotifier.updateOngoing(
                    context,
                    PrayerProvider.nextPrayer(context, settings, zone, now),
                    settings.persistentNotification,
                    settings.showCountdown,
                )
                NextPrayerWidget().updateAll(context)
                PrayerAlarmScheduler.scheduleNext(context, settings, zone)
            } finally {
                pending.finish()
            }
        }
    }
}
