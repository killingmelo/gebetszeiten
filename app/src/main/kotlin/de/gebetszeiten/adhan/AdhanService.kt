package de.gebetszeiten.adhan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.ui.MainActivity

/**
 * Spielt den Aḏān (und danach die Duʿāʾ) als Vordergrunddienst vom Typ
 * `mediaPlayback`. Gestartet wird er aus [de.gebetszeiten.alarm.PrayerAlarmReceiver]
 * beim Eintritt eines Gebets, also aus dem exakten Wecker heraus, der den
 * Start eines Vordergrunddienstes aus dem Hintergrund erlaubt; oder aus den
 * Einstellungen zum Probehören.
 *
 * Stopp: Knopf in der Meldung, jede Lautstärketaste (eine [MediaSession]
 * mit eigener Lautstärkesteuerung fängt sie ab), Verlust des Audiofokus
 * (Anruf, andere Wiedergabe) oder das Ende der letzten Aufnahme.
 */
class AdhanService : Service() {

    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private var queue: List<AdhanSource> = emptyList()
    private var stream: String = AdhanConfig.STREAM_ALARM
    private var volume: Float = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        val tracks = intent?.getStringArrayListExtra(EXTRA_TRACKS)?.map(::decode).orEmpty()
        val prayer = intent?.getStringExtra(EXTRA_PRAYER)
        if (tracks.isEmpty()) {
            stopAll()
            return START_NOT_STICKY
        }
        // Zuerst in den Vordergrund: Android verlangt das wenige Sekunden nach
        // dem Start, sonst beendet es den Dienst mit einem Absturz.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(prayer),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        // Ein zweiter Start (erneut „Probe hören“) ersetzt den ersten ganz:
        // alte Sitzung und alter Audiofokus duerfen nicht liegen bleiben.
        releasePlayer()
        releaseSessionAndFocus()
        stream = normalizedStream(intent?.getStringExtra(EXTRA_STREAM))
        volume = intent?.getFloatExtra(EXTRA_VOLUME, 1f) ?: 1f
        queue = tracks
        if (!requestFocus()) {
            stopAll()
            return START_NOT_STICKY
        }
        startSession()
        playNext()
        return START_NOT_STICKY
    }

    private fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(
            if (stream == AdhanConfig.STREAM_NOTIFICATION) AudioAttributes.USAGE_NOTIFICATION_EVENT
            else AudioAttributes.USAGE_ALARM,
        )
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // Ein Anruf oder eine andere Wiedergabe hat Vorrang: anhalten, nicht ducken.
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopAll()
    }

    private fun requestFocus(): Boolean {
        val audio = getSystemService(AudioManager::class.java)
        // Während eines Gesprächs bleibt der Aḏān aus, statt ins Telefonat zu fallen.
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Lautstärketasten beenden die Wiedergabe. Eine aktive Sitzung mit
     * eigener ([VolumeProvider]) Lautstärke bekommt die Tasten, statt dass
     * das System den Kanal regelt. Nur bei eingeschaltetem Bildschirm (auch
     * auf dem Sperrbildschirm): bei dunklem Bildschirm reicht Android
     * Lautstärketasten nur an laufende Musik weiter, keiner Sitzung (auf dem
     * Emulator unter API 36 nachgeprüft).
     */
    private fun startSession() {
        val s = MediaSession(this, "adhan")
        s.setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 1, 1) {
            override fun onAdjustVolume(direction: Int) = stopAll()
        })
        s.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_STOP)
                .build(),
        )
        s.setCallback(object : MediaSession.Callback() {
            override fun onStop() = stopAll()
            override fun onPause() = stopAll()
        })
        s.isActive = true
        session = s
    }

    private fun playNext() {
        releasePlayer()
        val next = queue.firstOrNull() ?: return stopAll()
        queue = queue.drop(1)
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes())
            when (next) {
                is AdhanSource.Bundled -> resources.openRawResourceFd(R.raw.adhan_almakki).use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                is AdhanSource.Custom -> mp.setDataSource(this, Uri.parse(next.uri))
            }
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener { playNext() }
            mp.setOnErrorListener { _, _, _ -> playNext(); true }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            // Eine eigene Datei kann geloescht oder ihr Zugriff entzogen sein:
            // dann das naechste Stueck statt Stille ohne Ende.
            mp.release()
            playNext()
        }
    }

    private fun releasePlayer() {
        player?.run {
            setOnCompletionListener(null)
            runCatching { stop() }
            release()
        }
        player = null
    }

    private fun releaseSessionAndFocus() {
        session?.run {
            isActive = false
            release()
        }
        session = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun stopAll() {
        queue = emptyList()
        releasePlayer()
        releaseSessionAndFocus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (player != null || session != null) stopAll()
        super.onDestroy()
    }

    private fun buildNotification(prayer: String?): Notification {
        ensureChannel(this)
        val name = prayer?.let { p -> runCatching { getString(Prayer.valueOf(p).labelRes()) }.getOrNull() }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(de.gebetszeiten.ui.theme.Palette.Day.accent.toArgb())
            .setContentTitle(if (name != null) getString(R.string.adhan_playing_title, name) else getString(R.string.adhan_playing_title_plain))
            .setContentText(getString(R.string.adhan_playing_text))
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .addAction(0, getString(R.string.adhan_stop), stop)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "adhan"
        private const val NOTIFICATION_ID = 5
        private const val ACTION_PLAY = "de.gebetszeiten.adhan.PLAY"
        private const val ACTION_STOP = "de.gebetszeiten.adhan.STOP"
        private const val EXTRA_TRACKS = "tracks"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_STREAM = "stream"
        private const val EXTRA_VOLUME = "volume"
        private const val BUNDLED = "bundled"

        private fun encode(source: AdhanSource): String = when (source) {
            AdhanSource.Bundled -> BUNDLED
            is AdhanSource.Custom -> source.uri
        }

        private fun decode(value: String): AdhanSource =
            if (value == BUNDLED) AdhanSource.Bundled else AdhanSource.Custom(value)

        /** Ein stiller Kanal: der Ton kommt vom Dienst selbst, nicht von der Meldung. */
        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.adhan_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.adhan_channel_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /**
         * Ob der Plan jetzt erklingen darf: Nicht-stören und ein lautlos
         * gestelltes Telefon auf dem Benachrichtigungs-Kanal halten ihn an.
         */
        fun allowedNow(context: Context, plan: AdhanPlan): Boolean {
            val nm = context.getSystemService(NotificationManager::class.java)
            val filter = nm.currentInterruptionFilter
            val alarmsAllowed = when (filter) {
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
                // Die Richtlinie lesen darf eine App ohne Nicht-stören-Zugriff
                // nicht auf jedem Geraet; dann lieber schweigen als abstuerzen.
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> runCatching {
                    nm.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
                }.getOrDefault(false)
                else -> false
            }
            val notificationAudible = context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
            return adhanAllowedUnderDnd(
                plan.stream,
                interruptionFilterAll = filter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                    filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
                alarmsAllowed = alarmsAllowed,
            ) && (plan.stream == AdhanConfig.STREAM_ALARM || notificationAudible)
        }

        /** Startet die Wiedergabe. Gibt zurück, ob sie angestoßen wurde. */
        @SuppressLint("MissingPermission")
        fun play(context: Context, plan: AdhanPlan, prayer: String?): Boolean = runCatching {
            val intent = Intent(context, AdhanService::class.java)
                .setAction(ACTION_PLAY)
                .putStringArrayListExtra(EXTRA_TRACKS, ArrayList(plan.tracks.map(::encode)))
                .putExtra(EXTRA_PRAYER, prayer)
                .putExtra(EXTRA_STREAM, plan.stream)
                .putExtra(EXTRA_VOLUME, plan.volume)
            ContextCompat.startForegroundService(context, intent)
            true
        }.getOrDefault(false)

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, AdhanService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
