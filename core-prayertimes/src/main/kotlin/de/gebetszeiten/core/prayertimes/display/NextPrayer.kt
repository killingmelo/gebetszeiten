package de.gebetszeiten.core.prayertimes.display

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import java.time.ZonedDateTime

/** Das naechste echte Gebet samt Zeitpunkt. */
data class UpcomingPrayer(val prayer: Prayer, val time: ZonedDateTime)

/**
 * Naechstes Gebet strikt nach [now] — Sonnenaufgang (kein Gebet) wird
 * uebersprungen. Reine Fassung von `PrayerProvider.nextPrayer`: Reicht
 * [today] nicht mehr aus, kommt der erste Eintrag von [tomorrow]; fehlt
 * [tomorrow], gibt es `null` (keine erfundenen Zeiten).
 */
fun nextPrayer(today: DailyPrayerTimes, tomorrow: DailyPrayerTimes?, now: ZonedDateTime): UpcomingPrayer? {
    val candidates = today.ordered() + (tomorrow?.ordered() ?: emptyList())
    return candidates
        .firstOrNull { (prayer, time) -> prayer != Prayer.SUNRISE && time.isAfter(now) }
        ?.let { UpcomingPrayer(it.first, it.second) }
}
