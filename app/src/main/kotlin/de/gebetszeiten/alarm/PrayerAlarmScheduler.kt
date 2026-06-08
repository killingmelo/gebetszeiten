package de.gebetszeiten.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
    const val ACTION_PRAYER = "de.gebetszeiten.action.PRAYER_ALARM"

    suspend fun scheduleNext(context: Context, settings: AppSettings, zone: ZoneId = ZoneId.systemDefault()) {
        val next = PrayerProvider.next(context, settings, zone, ZonedDateTime.now(zone))
        val triggerAt = next.time.toInstant().toEpochMilli()

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PrayerAlarmReceiver::class.java).setAction(ACTION_PRAYER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            // Exact-alarm permission revoked: degrade gracefully to an
            // inexact (still doze-friendly) alarm rather than crashing.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}
