package de.gebetszeiten.desktop.notify

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.HijriDay
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.Nafile
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.display.germanName
import de.gebetszeiten.core.prayertimes.display.karahaLine
import de.gebetszeiten.desktop.data.DesktopSettings
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class EventKind { PRAYER, LEAD, KARAHA_WARNING, END_WARNING, HINT }

/** Welcher Ton-Regler gilt (siehe `DesktopSettings.tone*`). */
enum class ToneCategory { PRAYER, LEAD, WARNING, HINT }

/**
 * Ein geplantes Ereignis: wann, was angezeigt und ob der Ezan laeuft.
 * [until]: bis dahin wird ein verpasstes Ereignis nachgeholt (Programm erst
 * spaeter gestartet); null = nur puenktlich (2 Minuten Spiel).
 * [occasion] waehlt den Lesetext.
 */
data class NotifyEvent(
    val at: ZonedDateTime,
    val kind: EventKind,
    val prayer: Prayer?,
    val title: String,
    val text: String,
    val playEzan: Boolean,
    val occasion: Occasion? = null,
    val until: ZonedDateTime? = null,
) {
    val tone: ToneCategory
        get() = when (kind) {
            EventKind.PRAYER -> ToneCategory.PRAYER
            EventKind.LEAD -> ToneCategory.LEAD
            EventKind.KARAHA_WARNING, EventKind.END_WARNING -> ToneCategory.WARNING
            EventKind.HINT -> ToneCategory.HINT
        }
}

/**
 * Alles, was fuer die Ereignisse eines Tages noetig ist. [next] (Zeiten des
 * Folgetags) braucht das Isha-Ende und Teheccüd; [hijri]/[hijriNext] kommen
 * nur von Diyanet und fehlen sonst - dann gibt es keine Hicri-Hinweise.
 */
data class DayContext(
    val times: DailyPrayerTimes,
    val next: DailyPrayerTimes? = null,
    val hijri: HijriDay? = null,
    val hijriNext: HijriDay? = null,
)

/** Wer laenger als das zurueckliegt, wurde verschlafen und wird verworfen. */
private const val MISSED_GRACE_MIN = 2L

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Kompatibel zur alten Form: nur ein Tag, ohne Folgetag und Hicri. */
fun notificationEvents(times: DailyPrayerTimes, s: DesktopSettings): List<NotifyEvent> =
    notificationEvents(DayContext(times), s)

/** Alle Ereignisse eines Tages, nach Zeit sortiert. Sonnenaufgang ist nie ein Gebet. */
fun notificationEvents(ctx: DayContext, s: DesktopSettings): List<NotifyEvent> {
    val t = ctx.times
    val events = mutableListOf<NotifyEvent>()
    val asrEndReplacesIsfirar = s.endWarningMinutes > 0 && Prayer.ASR in s.reminders

    for (prayer in s.reminders) {
        if (prayer == Prayer.SUNRISE) continue
        val time = t.time(prayer)
        val name = prayer.germanName()
        val iftar = prayer == Prayer.MAGHRIB && isRamazanIftar(ctx, s)
        events += NotifyEvent(
            at = time,
            kind = EventKind.PRAYER,
            prayer = prayer,
            title = name,
            text = if (iftar) "Es ist Zeit für $name – Ifṭār. Taqabbala llāh." else "Es ist Zeit für $name",
            playEzan = s.ezanFile != null || (prayer == Prayer.FAJR && s.fajrEzanFile != null),
            occasion = if (iftar) Occasion.IFTAR else null,
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
        if (s.endWarningMinutes > 0) endWarning(prayer, ctx, s.endWarningMinutes)?.let { events += it }
    }

    if (s.karahaWarning) {
        for (window in Karaha.windows(t)) {
            if (window.kind == Karaha.Kind.ISFIRAR && asrEndReplacesIsfirar) continue
            events += NotifyEvent(
                at = window.start.minusMinutes(Karaha.WARN_LEAD_MIN),
                kind = EventKind.KARAHA_WARNING,
                prayer = null,
                title = "Karāha",
                text = karahaLine(Karaha.Status.Soon(window))!!.text,
                playEzan = false,
                occasion = Occasion.KARAHA,
            )
        }
    }

    events += hints(ctx, s)
    return events.sortedBy { it.at }
}

private fun isRamazanIftar(ctx: DayContext, s: DesktopSettings): Boolean =
    s.ramazanHints && ctx.hijri?.month == 9

/**
 * Warnung [minutes] vor Ende der Gebetszeit. Asr endet fuer die Warnung mit
 * dem Beginn der Iṣfirār-Karāha (danach ist das Gebet makruh); Isha mit dem
 * Fajr des Folgetags (ohne Folgetag keine Warnung).
 */
private fun endWarning(prayer: Prayer, ctx: DayContext, minutes: Int): NotifyEvent? {
    val t = ctx.times
    val name = prayer.germanName()
    val (end, text) = when (prayer) {
        Prayer.FAJR -> t.sunrise to "$name endet um ${t.sunrise.format(HHMM)} (Sonnenaufgang)"
        Prayer.DHUHR -> t.asr to "$name endet um ${t.asr.format(HHMM)}"
        Prayer.ASR -> {
            val isfirar = t.maghrib.minusMinutes(Karaha.ISFIRAR_BEFORE_MAGHRIB_MIN)
            isfirar to "Ab ${isfirar.format(HHMM)} ist $name makrūh (Iṣfirār), die Gebetszeit endet um ${t.maghrib.format(HHMM)}"
        }
        Prayer.MAGHRIB -> t.isha to "$name endet um ${t.isha.format(HHMM)}"
        Prayer.ISHA -> {
            val fajr = ctx.next?.fajr ?: return null
            fajr to "$name endet um ${fajr.format(HHMM)} (Faǧr)"
        }
        Prayer.SUNRISE -> return null
    }
    val at = end.minusMinutes(minutes.toLong())
    // Kuerzer als die Vorwarnzeit (z. B. Maghrib im Sommer hoch im Norden): sinnlos.
    if (!at.isAfter(t.time(prayer))) return null
    return NotifyEvent(
        at = at,
        kind = EventKind.END_WARNING,
        prayer = prayer,
        title = "$name: noch $minutes Min",
        text = text,
        playEzan = false,
        occasion = Occasion.VAKIT_ENDET,
    )
}

private fun hints(ctx: DayContext, s: DesktopSettings): List<NotifyEvent> {
    val t = ctx.times
    val day = t.date
    val h = ctx.hijri
    val hn = ctx.hijriNext
    val endOfDay = t.date.plusDays(1).atStartOfDay(t.zone)
    val out = mutableListOf<NotifyEvent>()
    // Tageshinweise morgens nach der İşrak-Karaha, nachholbar bis Dhuhr.
    val morning = t.sunrise.plusMinutes(Karaha.ISRAK_AFTER_SUNRISE_MIN)
    val evening = t.maghrib.plusMinutes(10)

    fun hint(at: ZonedDateTime, o: Occasion, text: String, until: ZonedDateTime?, title: String = o.title) {
        out += NotifyEvent(at, EventKind.HINT, null, title, text, playEzan = false, occasion = o, until = until)
    }

    if (s.cumaHints) {
        if (day.dayOfWeek == DayOfWeek.THURSDAY) {
            hint(evening, Occasion.CUMA_GECE, "Heute Nacht ist die Nacht zum Freitag: viel Ṣalawāt und Bittgebet.", endOfDay)
        }
        if (day.dayOfWeek == DayOfWeek.FRIDAY) {
            hint(morning, Occasion.CUMA, "Ǧumʿa mubāraka. Sūrat al-Kahf lesen, viel Ṣalawāt.", t.dhuhr.minusMinutes(45))
            hint(t.dhuhr.minusMinutes(45), Occasion.CUMA_NAMAZ, "Ǧumʿa-Gebet um ${t.dhuhr.format(HHMM)}. Ġusl, Sauberkeit, früh gehen.", t.dhuhr)
        }
    }

    if (s.hijriHints && h != null) {
        specialDay(h)?.let { o ->
            if (o != Occasion.RAMAZAN || s.ramazanHints) hint(morning, o, specialDayText(o, h), t.dhuhr)
        }
    }
    if (s.hijriHints) {
        kandilTonight(day, hn)?.let { o -> hint(evening, o, kandilText(o), endOfDay) }
    }

    if (s.ramazanHints) {
        if (hn != null && hn.month == 9 && hn.day == 1) {
            hint(evening, Occasion.RAMAZAN, "Morgen beginnt der Ramaḍān. Heute Nacht Tarāwīḥ, Saḥūr vor ${ctx.next?.fajr?.format(HHMM) ?: "Imsāk"}.", endOfDay, title = "Ramaḍān beginnt")
        }
        if (h != null && h.month == 9) {
            if (s.sahurMinutes > 0) {
                val at = t.fajr.minusMinutes(s.sahurMinutes.toLong())
                hint(at, Occasion.SAHUR, "Saḥūr endet um ${t.fajr.format(HHMM)} (Imsāk).", t.fajr)
            }
            if (s.iftarLeadMinutes > 0) {
                hint(t.maghrib.minusMinutes(s.iftarLeadMinutes.toLong()), Occasion.IFTAR, "Ifṭār in ${s.iftarLeadMinutes} Min (${t.maghrib.format(HHMM)}).", null, title = "Ifṭār bald")
            }
            // Mit Maghrib-Erinnerung steht das İftar dort (ein Banner, nicht zwei).
            if (Prayer.MAGHRIB !in s.reminders) hint(t.maghrib, Occasion.IFTAR, "Es ist Ifṭār-Zeit. Taqabbala llāh.", null)
        }
    }

    if (s.fastingHints && fastingSensible(hn)) {
        val tomorrow = day.plusDays(1).dayOfWeek
        val reasons = mutableListOf<String>()
        var occasion = Occasion.SUNNET_ORUC
        if (tomorrow == DayOfWeek.MONDAY) reasons += "Montag"
        if (tomorrow == DayOfWeek.THURSDAY) reasons += "Donnerstag"
        if (hn != null && hn.day in 13..15) {
            reasons += "al-Ayyām al-Bīḍ (${hn.day}.)"
            if (reasons.size == 1) occasion = Occasion.EYYAM_BIZ
        }
        if (reasons.isNotEmpty()) {
            val sahur = ctx.next?.fajr?.let { " Saḥūr bis ${it.format(HHMM)}." } ?: ""
            hint(t.isha, occasion, "Morgen: ${reasons.joinToString(" und ")} – Sunna-Fasten.$sahur", endOfDay)
        }
    }

    if (s.nafileHints) {
        val duha = Nafile.duha(t)
        hint(duha.start, Occasion.KUSLUK, "Ḍuḥā-Zeit bis ${duha.end.format(HHMM)}.", duha.end.minusMinutes(30))
        Nafile.tahajjud(t, ctx.next?.fajr)?.let { w ->
            hint(w.start, Occasion.TEHECCUD, "Letztes Nachtdrittel bis ${w.end.format(HHMM)}: Zeit für Tahaǧǧud.", w.start.plusMinutes(30))
        }
    }
    return out
}

/**
 * Faellige Ereignisse: `lastRun < at <= now`, aber nicht aelter als 2 Minuten;
 * dazu nachholbare ([NotifyEvent.until]), solange `at <= now < until`.
 * Doppeltes Ausloesen verhindert der Aufrufer (Menge der ausgeloesten).
 */
fun dueEvents(events: List<NotifyEvent>, lastRun: ZonedDateTime, now: ZonedDateTime): List<NotifyEvent> {
    val earliest = now.minusMinutes(MISSED_GRACE_MIN)
    return events.filter {
        val onTime = it.at.isAfter(lastRun) && !it.at.isAfter(now) && !it.at.isBefore(earliest)
        val catchUp = it.until != null && !it.at.isAfter(now) && now.isBefore(it.until)
        onTime || catchUp
    }
}
