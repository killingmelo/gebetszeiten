package de.gebetszeiten.prayer

import android.content.Context
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.display.karahaLine
import de.gebetszeiten.data.AppSettings
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Compact karaha indicator shared by widget and lock-screen notification.
 * Text und Format liegen in core-prayertimes (`...display.KarahaText`);
 * hier bleiben Einstellung und Zeitenquelle.
 */
typealias KarahaLine = de.gebetszeiten.core.prayertimes.display.KarahaLine

object KarahaDisplay {

    suspend fun line(
        context: Context,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
    ): KarahaLine? {
        if (!settings.showKaraha) return null
        // Keine Zeiten unter den aktuellen Einstellungen: keine Karaha-Zeile,
        // statt Fenster aus erfundenen Zeiten zu berechnen.
        val times = PrayerProvider.daily(context, settings, now.toLocalDate(), zone) ?: return null
        return karahaLine(Karaha.status(Karaha.windows(times), now))
    }
}
