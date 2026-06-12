package de.gebetszeiten.prayer

import android.content.Context
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.data.AppSettings
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Compact karaha indicator shared by widget and lock-screen notification:
 * "⚠️ Karaha bis 13:21" while inside a window, "⚠️ Karaha ab 13:01" in the
 * 15 minutes before one. Static text — redrawn only at the window boundaries
 * via the display-alarm chain, never in between.
 */
data class KarahaLine(val text: String, val active: Boolean)

object KarahaDisplay {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun line(
        context: Context,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
    ): KarahaLine? {
        if (!settings.showKaraha) return null
        val times = PrayerProvider.daily(context, settings, now.toLocalDate(), zone)
        return when (val status = Karaha.status(Karaha.windows(times), now)) {
            is Karaha.Status.Active ->
                KarahaLine("⚠️ Karaha bis ${status.window.end.format(timeFormat)}", active = true)
            is Karaha.Status.Soon ->
                KarahaLine("⚠️ Karaha ab ${status.window.start.format(timeFormat)}", active = false)
            Karaha.Status.None -> null
        }
    }
}
