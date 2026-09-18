package de.gebetszeiten.notify

import android.annotation.SuppressLint
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
import de.gebetszeiten.prayer.remainingStepShort
import de.gebetszeiten.ui.MainActivity
import java.time.format.DateTimeFormatter

/** Posts a deliberately silent notification at a prayer time. */
object PrayerNotifier {

    private const val CHANNEL_ID = "prayer_times"
    private const val CHANNEL_VIBRATE_ID = "prayer_times_vibrate"
    private const val CHANNEL_SOUND_ID = "prayer_times_sound"
    private const val ONGOING_CHANNEL_ID = "next_prayer"
    private const val NOTIFICATION_ID = 1
    private const val ONGOING_ID = 2
    private const val PRE_ID = 3
    private const val PAUSE_NOTICE_ID = 4
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // One channel per reminder style — channel settings are immutable after
        // creation, so the style picks the channel instead of mutating one.
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
        val vibrate = NotificationChannel(
            CHANNEL_VIBRATE_ID,
            context.getString(R.string.notification_channel_vibrate),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 180, 250, 180, 450)
        }
        manager.createNotificationChannel(vibrate)
        val sound = NotificationChannel(
            CHANNEL_SOUND_ID,
            context.getString(R.string.notification_channel_sound),
            // DEFAULT importance with the system's default notification sound.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(sound)
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

    private fun styleChannel(style: String): String = when (style) {
        de.gebetszeiten.data.AppSettings.STYLE_VIBRATE -> CHANNEL_VIBRATE_ID
        de.gebetszeiten.data.AppSettings.STYLE_SOUND -> CHANNEL_SOUND_ID
        else -> CHANNEL_ID
    }

    private fun silentStyle(style: String): Boolean =
        style != de.gebetszeiten.data.AppSettings.STYLE_VIBRATE &&
            style != de.gebetszeiten.data.AppSettings.STYLE_SOUND

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
     * [countdown] a floor-rounded remaining step ("noch 2+ Std") is shown —
     * deliberately NOT a chronometer, which would redraw every second while
     * the lock screen is visible; the step text is static and refreshed by the
     * display-step alarm chain. While enabled it REPLACES the per-prayer entry
     * notification (one combined line instead of two redundant ones);
     * [activeSince] carries the entry info ("aktuell: Asr" — `ongoing_since`,
     * ohne Uhrzeit seit `3b3191f`; die frueher hier zitierte Form
     * "Asr seit 17:37" gibt es nicht mehr).
     *
     * Mit [countdown] traegt ausserdem das Statusleisten-Symbol die Restzeit
     * („2h", „20") statt des Monds — in BEIDEN Modi, denn der Systemzaehler
     * von EXACT zeichnet nur den Text. Gezaehlt wird gegen [next], und das
     * ist bei allen VIER Aufrufern `PrayerProvider.nextPrayer` (Sonnenaufgang
     * uebersprungen), also dasselbe Ziel wie im Titel daneben.
     *
     * Beim Herunterziehen zeigt die Anzeige mehr als eingeklappt: [city] steht
     * als Untertitel in der Kopfzeile, und der `BigTextStyle`-Text traegt die
     * genaue Uhrzeit in BEIDEN Countdown-Modi, dazu laufendes Gebet, dessen
     * Ende und die Karaha-Zeile, soweit vorhanden. Der Titel und die
     * eingeklappte Zeile bleiben unveraendert.
     *
     * WELCHER Text wo steht, entscheidet diese Funktion nicht mehr selbst:
     * [ongoingMode] und [ongoingTexts] tun es, ohne `Context` und damit im
     * Test ausfuehrbar. Hier wird nur noch verdrahtet — Titel, Inhaltszeile,
     * aufgeklappter Text und Untertitel kommen unveraendert aus
     * [OngoingTexts]. Vorher war die Zusage „Uhrzeit im Aufgeklappten" eine
     * Verzweigung mitten in dieser Funktion und liess sich lautlos
     * entfernen, ohne dass ein Test fiel.
     */
    // notify() requires POST_NOTIFICATIONS; every path here is guarded by
    // canPost() above, which lint's data-flow doesn't track through the helper.
    @SuppressLint("MissingPermission")
    fun updateOngoing(
        context: Context,
        next: NextPrayer?,
        enabled: Boolean,
        countdown: Boolean,
        activeSince: NextPrayer? = null,
        replacesEntry: Boolean = true,
        // End of the running prayer's window when it is NOT the next prayer's
        // start — only Fajr, which becomes invalid at sunrise.
        activeUntil: java.time.ZonedDateTime? = null,
        // EXACT precision: live system-rendered chronometer instead of steps.
        exact: Boolean = false,
        // "⚠️ Karaha bis/ab HH:MM" — static, refreshed at window boundaries.
        karahaLine: de.gebetszeiten.prayer.KarahaLine? = null,
        // Der AKTIVE Ort (`settings.city`) als Untertitel in der Kopfzeile.
        // Nicht das Abrufziel aus `PrayerProvider.refreshOfficial` — das ist
        // seit Task 6 ein anderer Ort, und die Zeiten hier kommen vom aktiven.
        city: String? = null,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!enabled || next == null) {
            manager.cancel(ONGOING_ID)
            return
        }
        if (!canPost(context)) return
        ensureChannel(context)
        // Silent style: one combined line — a lingering per-prayer entry
        // notification would be redundant, so clear it. Audible styles keep
        // their (auto-clearing) entry notification as the alert carrier.
        if (replacesEntry) manager.cancel(NOTIFICATION_ID)
        val whenMillis = next.time.toInstant().toEpochMilli()
        val name = context.getString(next.prayer.labelRes())
        val timeStr = next.time.format(timeFormat)
        // STEPS countdown: the remaining time IS the headline ("Noch 20+ Min
        // bis Isha"); clock time moves to the detail line. Otherwise the
        // classic "Isha um 22:48" title (EXACT mode ticks via chronometer).
        // EINMAL gerechnet, fuer Text UND Symbol: zwei getrennte Aufrufe von
        // Instant.now() koennten ueber eine Sekundengrenze fallen und Titel
        // und Statusleiste auf verschiedene Stufen setzen.
        val remaining = java.time.Duration.between(
            java.time.Instant.now(),
            java.time.Instant.ofEpochMilli(whenMillis),
        )
        val stepShort = if (countdown && !exact) remainingStepShort(remaining) else ""
        // Dieselben Bausteine tragen die eingeklappte Zeile UND den
        // aufgeklappten Text — einmal aufgeloest, mehrfach benutzt.
        val atLine = context.getString(R.string.ongoing_at, timeStr)
        // Only ONE clock time on the lock screen (the next prayer in the
        // title); the running prayer is named without its time.
        val sinceLine = activeSince?.let {
            context.getString(R.string.ongoing_since, context.getString(it.prayer.labelRes()))
        }
        // Exception: Fajr's window end is actionable (prayer becomes
        // invalid at sunrise), so that one keeps its time.
        val untilLine = activeUntil?.let {
            context.getString(R.string.ongoing_until, it.format(timeFormat))
        }
        // Die EINE Entscheidung liegt in [ongoingMode]/[ongoingTexts] — ohne
        // Context, also ausfuehrbar im Test. Hier wird nur noch
        // zusammengesetzt, was sie liefern.
        val mode = ongoingMode(countdown, exact, stepShort)
        val texts = ongoingTexts(
            mode = mode,
            titleWithStep = context.getString(R.string.ongoing_title_remaining, stepShort, name),
            titleWithTime = context.getString(R.string.ongoing_title, name, timeStr),
            timeLine = atLine,
            activeLine = sinceLine,
            untilLine = untilLine,
            karahaText = karahaLine?.text,
            city = city,
        )
        val notification = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            // Die Restzeit in der Statusleiste statt des statischen Monds.
            // Ist [countdown] aus, liefert countdownGlyph None und
            // countdownIconRes wieder ic_notification — dann sieht die
            // Anzeige genauso aus wie bisher. Gilt auch fuer EXACT: der
            // Systemzaehler zeichnet den Text, das Symbol kommt von hier und
            // wird von der Anzeige-Weckkette weitergestellt
            // (AppSettings.needsDisplayStepAlarms).
            .setSmallIcon(countdownIconRes(countdownGlyph(remaining, countdown)))
            .setContentTitle(texts.title)
            // `null` heisst „gar keine Zeile" — genau wie frueher das
            // uebersprungene `setContentText`; die Vorgabe des Builders IST
            // null.
            .setContentText(texts.contentText)
            // Aufgeklappt („runterziehen"): die genaue Uhrzeit steht IMMER
            // dabei — im Stufen-Modus ist sie aus dem Titel verdraengt, und
            // genau die verlangt der Nutzer. Entschieden in [ongoingTexts].
            .setStyle(NotificationCompat.BigTextStyle().bigText(texts.bigText))
            // Der Ort in der Kopfzeile, ohne die Inhaltszeile zu belegen.
            .setSubText(texts.subText)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .apply {
                if (mode == OngoingMode.EXACT) {
                    // Live countdown rendered by the system — no app wake-ups.
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

    /** Heads-up "prayer X in N minutes" — styled like the entry reminder,
     *  auto-clears at prayer entry. */
    @SuppressLint("MissingPermission") // guarded by canPost()
    fun notifyPre(
        context: Context,
        next: NextPrayer,
        leadMinutes: Int,
        style: String = de.gebetszeiten.data.AppSettings.STYLE_SILENT,
    ) {
        if (!canPost(context)) return
        ensureChannel(context)
        val untilPrayer = next.time.toInstant().toEpochMilli() - System.currentTimeMillis()
        val notification = NotificationCompat.Builder(context, styleChannel(style))
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
            .setPriority(
                if (silentStyle(style)) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setSilent(silentStyle(style))
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
    @SuppressLint("MissingPermission") // guarded by canPost()
    fun notifyPrayer(
        context: Context,
        prayer: NextPrayer,
        next: NextPrayer? = null,
        clearAtMillis: Long? = null,
        style: String = de.gebetszeiten.data.AppSettings.STYLE_SILENT,
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
        val notification = NotificationCompat.Builder(context, styleChannel(style))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            // Timestamp = the prayer's entry time, so a notification read later
            // clearly refers to that moment, not to "now".
            .setWhen(prayer.time.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setPriority(
                if (silentStyle(style)) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setSilent(silentStyle(style))
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

    /**
     * Meldet EINMAL, dass Wecker und Dauerbenachrichtigung mangels amtlicher
     * Zeiten schweigen (Aufgabe 14) — und zieht die Meldung zurueck, sobald
     * wieder Zeiten da sind. [hasTimes] ist `PrayerProvider.nextPrayer(...)
     * != null`, dasselbe Signal, an dem [updateOngoing] oben schon erkennt,
     * ob es ueberhaupt eine Dauerbenachrichtigung geben kann — hier bewusst
     * UNABHAENGIG von `enabled`/`persistentNotification`: die WECKER
     * schweigen auch, wenn die Dauerbenachrichtigung nie eingeschaltet war.
     *
     * Eine ZWEITE, EIGENE Benachrichtigung ([PAUSE_NOTICE_ID]), nicht die
     * Dauerbenachrichtigung ([ONGOING_ID]) wiederbelebt: die entfernt
     * [updateOngoing] bei fehlenden Zeiten bereits korrekt (seit 8d5dd7c) —
     * genau darum verschwindet mit ihr jeder Hinweis lautlos, und diese
     * Funktion soll den Ausfall gerade SICHTBAR machen, nicht die stumme
     * Entfernung nachahmen.
     *
     * Auf dem BESTEHENDEN stillen Kanal [CHANNEL_ID] ("Gebetszeiten
     * (still)"), nicht auf einem eigenen: inhaltlich ist es eine
     * Gebetszeiten-Meldung, sie soll IMMER lautlos bleiben — unabhaengig von
     * `reminderStyle`, das nur fuer die Gebets-ERINNERUNGEN gilt, nicht fuer
     * einen Systemhinweis über deren Ausfall — und ein zusaetzlicher Kanal
     * waere ein weiterer Dauereintrag in den System-Benachrichtigungs-
     * einstellungen fuer etwas, das im Idealfall nie und sonst hoechstens
     * selten erscheint.
     *
     * Die Entscheidung selbst UND ihre Persistierung laufen atomar in
     * [de.gebetszeiten.data.SettingsRepository.resolvePauseNotice] — siehe
     * dort zum Rennen zwischen den VIER Aufrufstellen ([PrayerAlarmReceiver]
     * zweimal, [BootReceiver], [PrayerViewModel][de.gebetszeiten.ui.
     * PrayerViewModel]): nur eine davon bekommt tatsaechlich SHOW oder
     * CLEAR, die anderen sehen danach den schon aktualisierten Merker und
     * bekommen NOTHING.
     */
    @SuppressLint("MissingPermission") // guarded by canPost()
    suspend fun updatePauseNotice(context: Context, hasTimes: Boolean) {
        when (de.gebetszeiten.data.SettingsRepository(context).resolvePauseNotice(hasTimes)) {
            PauseNotice.SHOW -> {
                if (!canPost(context)) return
                ensureChannel(context)
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.pause_notice_title))
                    .setContentText(context.getString(R.string.pause_notice_text))
                    .setContentIntent(contentIntent(context))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(PAUSE_NOTICE_ID, notification)
            }
            PauseNotice.CLEAR -> NotificationManagerCompat.from(context).cancel(PAUSE_NOTICE_ID)
            PauseNotice.NOTHING -> Unit
        }
    }
}
