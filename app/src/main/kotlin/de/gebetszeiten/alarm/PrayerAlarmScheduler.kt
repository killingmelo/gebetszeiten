package de.gebetszeiten.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.PrayerProvider
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules exactly one alarm — the next prayer transition. When it fires, the
 * receiver schedules the following one. This event-driven chain means ~5–6
 * wake-ups per day with no polling and no background service.
 */
object PrayerAlarmScheduler {

    private const val REQUEST_CODE = 100
    private const val PRE_REQUEST_CODE = 101
    private const val DISPLAY_REQUEST_CODE = 102
    const val ACTION_PRAYER = "de.gebetszeiten.action.PRAYER_ALARM"
    const val ACTION_PRE_REMINDER = "de.gebetszeiten.action.PRE_REMINDER"
    const val ACTION_DISPLAY_STEP = "de.gebetszeiten.action.DISPLAY_STEP"

    suspend fun scheduleNext(context: Context, settings: AppSettings, zone: ZoneId = ZoneId.systemDefault()) {
        val now = ZonedDateTime.now(zone)
        val next = PrayerProvider.next(context, settings, zone, now)
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        setAlarm(alarmManager, next.time.toInstant().toEpochMilli(), pendingIntent(context, REQUEST_CODE, ACTION_PRAYER))
        schedulePreReminder(context, alarmManager, settings, zone, now)
        scheduleDisplayStep(context, alarmManager, settings, zone, now)
    }

    /**
     * Remaining-time mode only: one alarm at the next floor-step boundary
     * (full hours before the prayer, plus the 30- and 10-minute marks) so the
     * widget and the persistent notification can show a static "noch 2+ Std"
     * instead of a per-second chronometer. ~10–20 extra ms-scale wake-ups per
     * day, in exchange for zero continuous rendering.
     */
    private suspend fun scheduleDisplayStep(
        context: Context,
        alarmManager: AlarmManager,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
    ) {
        val pending = pendingIntent(context, DISPLAY_REQUEST_CODE, ACTION_DISPLAY_STEP)
        if (!settings.showCountdown) {
            alarmManager.cancel(pending)
            return
        }
        // Both surfaces' targets: widget = next transition, notification = next
        // actual prayer (sunrise skipped). Boundaries of either count.
        val targets = buildList {
            add(PrayerProvider.next(context, settings, zone, now).time)
            if (settings.persistentNotification) {
                add(PrayerProvider.nextPrayer(context, settings, zone, now).time)
            }
        }
        val nowMs = System.currentTimeMillis()
        val nextBoundary = targets.flatMap { target ->
            val targetMs = target.toInstant().toEpochMilli()
            buildList {
                // 10-minute steps through the final hour (50/40/30/20/10),
                // matching remainingStepLabel's resolution.
                for (tenMin in 1..5) {
                    add(targetMs - tenMin * 10 * 60_000L)
                }
                var hour = 1L
                while (true) {
                    val boundary = targetMs - hour * 3_600_000L
                    if (boundary <= nowMs) break
                    add(boundary)
                    hour++
                }
            }
        }.filter { it > nowMs + 1_000 }.minOrNull()
        if (nextBoundary != null) {
            setAlarm(alarmManager, nextBoundary, pending)
        } else {
            alarmManager.cancel(pending)
        }
    }

    /**
     * Optional heads-up N minutes before the next reminder-enabled prayer.
     * Rescheduled by the same transition chain — no extra recurring wake-ups
     * beyond the single pre-alarm itself.
     */
    private suspend fun schedulePreReminder(
        context: Context,
        alarmManager: AlarmManager,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
    ) {
        val pre = pendingIntent(context, PRE_REQUEST_CODE, ACTION_PRE_REMINDER)
        val lead = settings.reminderLeadMinutes
        val nextPrayer = PrayerProvider.nextPrayer(context, settings, zone, now)
        val triggerAt = nextPrayer.time.minusMinutes(lead.toLong()).toInstant().toEpochMilli()
        val wanted = lead > 0 &&
            nextPrayer.prayer.name in settings.reminders &&
            triggerAt > System.currentTimeMillis()
        if (wanted) {
            setAlarm(alarmManager, triggerAt, pre)
        } else {
            alarmManager.cancel(pre)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PrayerAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAlarm(alarmManager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        // Don't pre-check canScheduleExactAlarms(): it only reflects the
        // SCHEDULE_EXACT_ALARM appop and reports false even when USE_EXACT_ALARM
        // is granted, which silently degraded every alarm to a 1-hour window.
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            // Exact-alarm permission really revoked: degrade gracefully to an
            // inexact (still doze-friendly) alarm rather than crashing.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}
