package de.gebetszeiten.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.gebetszeiten.R
import de.gebetszeiten.prayer.NextPrayer
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.ui.MainActivity
import java.time.format.DateTimeFormatter

/** Posts a deliberately silent notification at a prayer time. */
object PrayerNotifier {

    private const val CHANNEL_ID = "prayer_times"
    private const val ONGOING_CHANNEL_ID = "next_prayer"
    private const val NOTIFICATION_ID = 1
    private const val ONGOING_ID = 2
    private const val PRE_ID = 3
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // LOW = no sound, shown silently in the shade and on the lock screen.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
        val ongoing = NotificationChannel(
            ONGOING_CHANNEL_ID,
            context.getString(R.string.ongoing_channel_name),
            // MIN = silent and collapsed; lock-screen visible via channel visibility.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.ongoing_channel_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(ongoing)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Persistent silent "next prayer" line for shade + lock screen. With
     * [countdown] the remaining time is rendered by the system (chronometer);
     * either way the app posts this only once per prayer transition — no extra
     * wake-ups.
     */
    fun updateOngoing(context: Context, next: NextPrayer?, enabled: Boolean, countdown: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!enabled || next == null) {
            manager.cancel(ONGOING_ID)
            return
        }
        if (!canPost(context)) return
        ensureChannel(context)
        val whenMillis = next.time.toInstant().toEpochMilli()
        val notification = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    R.string.ongoing_title,
                    context.getString(next.prayer.labelRes()),
                    next.time.format(timeFormat),
                ),
            )
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .apply {
                if (countdown) {
                    setWhen(whenMillis)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setShowWhen(true)
                } else {
                    setShowWhen(false)
                }
            }
            .build()
        manager.notify(ONGOING_ID, notification)
    }

    /** Heads-up "prayer X in N minutes" — silent, auto-clears at prayer entry. */
    fun notifyPre(context: Context, next: NextPrayer, leadMinutes: Int) {
        if (!canPost(context)) return
        ensureChannel(context)
        val untilPrayer = next.time.toInstant().toEpochMilli() - System.currentTimeMillis()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    R.string.pre_reminder_title,
                    context.getString(next.prayer.labelRes()),
                    leadMinutes,
                ),
            )
            .setContentText(context.getString(R.string.pre_reminder_text, next.time.format(timeFormat)))
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .apply { if (untilPrayer > 0) setTimeoutAfter(untilPrayer) }
            .build()
        NotificationManagerCompat.from(context).notify(PRE_ID, notification)
    }

    /**
     * @param next the next actual prayer (sunrise excluded) — shown as text.
     * @param clearAtMillis epoch millis of the next transition (sunrise included)
     *   at which the notification auto-dismisses.
     */
    fun notifyPrayer(
        context: Context,
        prayer: NextPrayer,
        next: NextPrayer? = null,
        clearAtMillis: Long? = null,
    ) {
        if (!canPost(context)) return
        ensureChannel(context)
        val title = context.getString(
            R.string.notification_entered,
            context.getString(prayer.prayer.labelRes()),
            prayer.time.format(timeFormat),
        )
        val text = next?.let {
            context.getString(
                R.string.notification_next_line,
                context.getString(it.prayer.labelRes()),
                it.time.format(timeFormat),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            // Timestamp = the prayer's entry time, so a notification read later
            // clearly refers to that moment, not to "now".
            .setWhen(prayer.time.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .apply {
                // Auto-dismiss at the next transition: the system clears it with
                // no extra wake-up, so no stale notification lingers.
                clearAtMillis?.let {
                    val untilNext = it - System.currentTimeMillis()
                    if (untilNext > 0) setTimeoutAfter(untilNext)
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
