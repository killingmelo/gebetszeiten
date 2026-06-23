package de.gebetszeiten.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.notify.PrayerNotifier
import de.gebetszeiten.notify.entryClearAtMillis
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
                        PrayerNotifier.notifyPre(
                            context,
                            upcoming,
                            settings.reminderLeadMinutes,
                            settings.reminderStyle,
                        )
                    }
                    return@launch
                }

                if (intent.action == PrayerAlarmScheduler.ACTION_DISPLAY_STEP) {
                    // Display boundary (remaining-time step or karaha window):
                    // redraw widget + persistent line, chain the next one.
                    val stepActive = PrayerProvider.currentlyActive(context, settings, zone, now)
                        ?.takeIf { it.prayer != Prayer.SUNRISE }
                    PrayerNotifier.updateOngoing(
                        context,
                        PrayerProvider.nextPrayer(context, settings, zone, now),
                        settings.persistentNotification,
                        settings.notificationCountdown != de.gebetszeiten.data.AppSettings.COUNTDOWN_OFF,
                        stepActive,
                        replacesEntry = false, // step ticks never clear the entry alert
                        activeUntil = PrayerProvider.activeUntil(context, settings, zone, now, stepActive),
                        exact = settings.notificationCountdown == de.gebetszeiten.data.AppSettings.PRECISION_EXACT,
                        karahaLine = de.gebetszeiten.prayer.KarahaDisplay.line(context, settings, zone, now),
                    )
                    NextPrayerWidget().updateAll(context)
                    PrayerAlarmScheduler.scheduleNext(context, settings, zone)
                    return@launch
                }

                val active = PrayerProvider.currentlyActive(context, settings, zone, now)
                    ?.takeIf { it.prayer != Prayer.SUNRISE }
                // Sunrise is no prayer; otherwise notify only if the user enabled
                // a reminder for this prayer. With the persistent notification on
                // AND a fully silent style, that single line carries the entry
                // info instead — a separate entry notification would be
                // redundant. An audible/vibrating style still needs the entry
                // notification as the alert carrier (it auto-clears).
                val styleSilent = settings.reminderStyle == de.gebetszeiten.data.AppSettings.STYLE_SILENT
                if (active != null && active.prayer.name in settings.reminders &&
                    (!settings.persistentNotification || !styleSilent)
                ) {
                    // Next transition (may be sunrise) ends this notification;
                    // the text line always names a real prayer.
                    val transition = PrayerProvider.next(context, settings, zone, now)
                    val nextPrayer = if (transition.prayer == Prayer.SUNRISE) {
                        PrayerProvider.next(context, settings, zone, transition.time)
                    } else {
                        transition
                    }
                    // With the persistent line on AND an audible style, the entry
                    // banner is only the alert carrier — let it auto-clear after a
                    // few minutes instead of lingering until the next transition,
                    // so it doesn't duplicate the persistent line all interval long.
                    PrayerNotifier.notifyPrayer(
                        context,
                        active,
                        nextPrayer,
                        entryClearAtMillis(
                            nowMillis = now.toInstant().toEpochMilli(),
                            transitionMillis = transition.time.toInstant().toEpochMilli(),
                            persistent = settings.persistentNotification,
                            audible = !styleSilent,
                        ),
                        settings.reminderStyle,
                    )
                }
                PrayerNotifier.updateOngoing(
                    context,
                    PrayerProvider.nextPrayer(context, settings, zone, now),
                    settings.persistentNotification,
                    settings.notificationCountdown != de.gebetszeiten.data.AppSettings.COUNTDOWN_OFF,
                    active,
                    replacesEntry = styleSilent,
                    activeUntil = PrayerProvider.activeUntil(context, settings, zone, now, active),
                    exact = settings.notificationCountdown == de.gebetszeiten.data.AppSettings.PRECISION_EXACT,
                    karahaLine = de.gebetszeiten.prayer.KarahaDisplay.line(context, settings, zone, now),
                )
                NextPrayerWidget().updateAll(context)
                PrayerAlarmScheduler.scheduleNext(context, settings, zone)
            } finally {
                pending.finish()
            }
        }
    }
}
