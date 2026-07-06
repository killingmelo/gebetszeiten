package de.gebetszeiten.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Optional wrist vibration at each prayer time. Same event-driven chain as the
 * phone: exactly ONE exact alarm at the next prayer; when it fires, the wrist
 * buzzes gently and the following alarm is scheduled — ~5–6 ms-scale wake-ups
 * per day, no timers, nothing while disabled (chain fully cancelled).
 */
object WearVibration {

    const val ACTION_VIBRATE = "de.gebetszeiten.action.WEAR_VIBRATE"
    private const val REQUEST_CODE = 200

    /** (Re)establish or cancel the chain according to the setting. */
    suspend fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WearAlarmReceiver::class.java).setAction(ACTION_VIBRATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!WearSettings.vibrate(context)) {
            alarmManager.cancel(pending)
            return
        }
        val zone = ZoneId.systemDefault()
        val location = WearSettings.location(context)
        val next = WearPrayer.next(context, location, zone, ZonedDateTime.now(zone))
        val triggerAt = next.second.toInstant().toEpochMilli()
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Two gentle pulses + one longer — distinct from notification buzzes. */
    fun buzz(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 250, 180, 250, 180, 450), -1),
        )
    }
}

/** Fires at each prayer time (vibrates + chains the next alarm) and re-arms
 *  the chain after reboot / app update / clock or timezone changes. */
class WearAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WearVibration.ACTION_VIBRATE) {
            WearVibration.buzz(context)
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WearVibration.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
