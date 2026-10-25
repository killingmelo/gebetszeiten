package de.gebetszeiten.desktop.notify

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.display.germanName
import de.gebetszeiten.core.prayertimes.display.karahaLine
import de.gebetszeiten.desktop.data.DesktopSettings
import java.time.ZonedDateTime

enum class EventKind { PRAYER, LEAD, KARAHA_WARNING }

/** Ein geplantes Ereignis: wann, was angezeigt und ob der Ezan laeuft. */
data class NotifyEvent(
    val at: ZonedDateTime,
    val kind: EventKind,
    val prayer: Prayer?,
    val title: String,
    val text: String,
    val playEzan: Boolean,
)

/** Wer laenger als das zurueckliegt, wurde verschlafen und wird verworfen. */
private const val MISSED_GRACE_MIN = 2L

/** Alle Ereignisse eines Tages, nach Zeit sortiert. Sonnenaufgang ist nie dabei. */
fun notificationEvents(times: DailyPrayerTimes, s: DesktopSettings): List<NotifyEvent> {
    val events = mutableListOf<NotifyEvent>()
    for (prayer in s.reminders) {
        if (prayer == Prayer.SUNRISE) continue
        val time = times.time(prayer)
        val name = prayer.germanName()
        events += NotifyEvent(
            at = time,
            kind = EventKind.PRAYER,
            prayer = prayer,
            title = name,
            text = "Es ist Zeit für $name",
            playEzan = s.ezanFile != null || (prayer == Prayer.FAJR && s.fajrEzanFile != null),
        )
        if (s.leadMinutes > 0) {
            events += NotifyEvent(
                at = time.minusMinutes(s.leadMinutes.toLong()),
                kind = EventKind.LEAD,
                prayer = prayer,
                title = name,
                text = "$name in ${s.leadMinutes} Min",
                playEzan = false,
            )
        }
    }
    if (s.karahaWarning) {
        for (window in Karaha.windows(times)) {
            events += NotifyEvent(
                at = window.start.minusMinutes(Karaha.WARN_LEAD_MIN),
                kind = EventKind.KARAHA_WARNING,
                prayer = null,
                title = "Karaha",
                text = karahaLine(Karaha.Status.Soon(window))!!.text,
                playEzan = false,
            )
        }
    }
    return events.sortedBy { it.at }
}

/** Faellige Ereignisse: `lastRun < at <= now`, aber nicht aelter als 2 Minuten. */
fun dueEvents(events: List<NotifyEvent>, lastRun: ZonedDateTime, now: ZonedDateTime): List<NotifyEvent> {
    val earliest = now.minusMinutes(MISSED_GRACE_MIN)
    return events.filter { it.at.isAfter(lastRun) && !it.at.isAfter(now) && !it.at.isBefore(earliest) }
}
