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
        // Leerfall (Task 16): weder amtliche Zeiten noch der Notausgang
        // liefern eine naechste Zeit — es gibt dann nichts, wofuer die Uhr
        // vibrieren koennte. Dieselbe Behandlung wie eine ausgeschaltete
        // Einstellung: die Kette wird abbestellt statt gegen einen
        // erfundenen Zeitpunkt zu planen.
        val next = WearPrayer.next(context, location, zone, ZonedDateTime.now(zone)) ?: run {
            alarmManager.cancel(pending)
            return
        }
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

/**
 * Fires at each prayer time (vibrates + chains the next alarm) and re-arms
 * the chain after reboot / app update / clock or timezone changes.
 *
 * Registriert im Manifest fuer `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
 * `TIME_SET` UND `TIMEZONE_CHANGED` — bis Fix-Runde 3 (Important 3, Aufgabe
 * 16) rief [onReceive] dabei AUSSCHLIESSLICH [WearVibration.reschedule] auf.
 * Zwei Folgen, die niemand in der Tabelle der Heilungswege gefuehrt hatte:
 * - Zeitzonenwechsel: Kachel und Komplikation haben ihre Gueltigkeit an
 *   ABSOLUTEN Zeitpunkten, die angezeigten Zeichenketten sind aber mit der
 *   ALTEN Zone formatiert — nach einem Flug zeigten beide Oberflaechen
 *   stundenlang die falsche Wanduhrzeit, und nichts stiess sie an.
 * - Neustart im Leerfall: kein Alarm (abbestellt), keine Gueltigkeitsgrenze,
 *   kein Anstoss — die Komplikation blieb auf dem Leerfall stehen, bis die
 *   Kachel ihre 30-Minuten-Frische zog oder die App geoeffnet wurde.
 *
 * [notifyWearOfficialRefreshed] statt eines von Hand nachgebauten
 * `reschedule` + `notifyWearSurfaces` — derselbe geteilte Weg (inklusive der
 * getrennten `try`-Bloecke aus Fix-Runde 2, Important 2) wie nach einem
 * eigenen Abruf oder dem Notausgang-Toggle. Kein zusaetzlicher
 * `launchWearRefresh`-Aufruf: bei `TIMEZONE_CHANGED` aendert sich nur die
 * DARSTELLUNG, nicht der Inhalt — ein Netzabruf waere hier ohne Wirkung.
 */
class WearAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WearVibration.ACTION_VIBRATE) {
            WearVibration.buzz(context)
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                notifyWearOfficialRefreshed(context)
            } finally {
                pending.finish()
            }
        }
    }
}
