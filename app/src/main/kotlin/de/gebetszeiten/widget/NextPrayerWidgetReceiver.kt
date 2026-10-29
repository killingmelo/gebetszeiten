package de.gebetszeiten.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import de.gebetszeiten.alarm.PrayerAlarmScheduler
import de.gebetszeiten.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()

    /** Erstes Widget platziert: einmal neu planen, damit die Grenzen von
     *  Ḍuḥā und Zawāl sofort wecken (sonst erst nach dem nächsten Ereignis). */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PrayerAlarmScheduler.scheduleNext(context, SettingsRepository(context).current(), ZoneId.systemDefault())
            } catch (e: Exception) {
                // Eine Neuplanung darf den Prozess nie abstürzen lassen.
                Log.w("NextPrayerWidgetReceiver", "Neuplanung nach dem Platzieren fehlgeschlagen", e)
            } finally {
                pending.finish()
            }
        }
    }
}
