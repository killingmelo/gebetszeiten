package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.officialtimes.DaySource
import de.gebetszeiten.core.prayertimes.officialtimes.daySourceOrder
import java.time.LocalDate
import java.time.ZoneId

/**
 * Die Zeiten eines Tages: nur amtliche Quellen, in der Reihenfolge von
 * [daySourceOrder] (Cache vor Mitgeliefertem). Der Desktop rechnet nie selbst:
 * [DaySource.CALCULATION] ergibt null. Kein Ort gewaehlt -> null.
 */
class TimesSource(private val cache: DesktopCache, private val bundled: BundledTables) {

    fun daily(s: DesktopSettings, date: LocalDate, zone: ZoneId): DailyPrayerTimes? {
        val (lat, lng) = s.coords ?: return null
        for (source in daySourceOrder(useOnline = true, calculationFillsGaps = false)) {
            val times = when (source) {
                DaySource.ONLINE_CACHE -> cache.get(date, lat, lng)
                DaySource.BUNDLED_TABLE -> bundled.get(lat, lng, date)
                DaySource.CALCULATION -> null
            }
            if (times != null) return times.toDaily(date, zone)
        }
        return null
    }
}
