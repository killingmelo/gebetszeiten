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
    const val ACTION_PRAYER = "de.gebetszeiten.action.PRAYER_ALARM"
    const val ACTION_PRE_REMINDER = "de.gebetszeiten.action.PRE_REMINDER"

    suspend fun scheduleNext(context: Context, settings: AppSettings, zone: ZoneId = ZoneId.systemDefault()) {
        val now = ZonedDateTime.now(zone)
        val next = PrayerProvider.next(context, settings, zone, now)
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        setAlarm(alarmManager, next.time.toInstant().toEpochMilli(), pendingIntent(context, REQUEST_CODE, ACTION_PRAYER))
        schedulePreReminder(context, alarmManager, settings, zone, now)
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
