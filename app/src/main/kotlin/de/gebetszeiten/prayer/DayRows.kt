package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import java.time.ZonedDateTime

/** Abschnitte der Tagesliste (auch von der Abschnittsansicht genutzt). */
enum class Section { FAJR, SHURUQ_MAKRUH, DUHA, ZUHR, ASR, ISFIRAR_MAKRUH, MAGHRIB, AWWABIN, ISHA, TAHAJJUD, JAMAA }

sealed interface DayRow {
    data class PrayerRow(
        val prayer: Prayer,
        val at: ZonedDateTime,
        val past: Boolean,
        val current: Boolean,
        val nextDay: Boolean = false,
    ) : DayRow

    data class SideRow(
        val kind: Section,
        val start: ZonedDateTime,
        val end: ZonedDateTime?,
        val past: Boolean,
    ) : DayRow
}

/**
 * Der ganze Tag in zeitlicher Reihenfolge. Vergangenes bleibt sichtbar und ist nur
 * markiert; `current` gilt für die Gebetszeile, in der man sich laut [heroState] befindet.
 */
fun dayRows(
    today: DailyPrayerTimes,
    karaha: KarahaTimes,
    nafl: NaflTimes,
    nextFajr: ZonedDateTime?,
    now: ZonedDateTime,
    isToday: Boolean,
    showKaraha: Boolean,
    showCemaat: Boolean,
    cemaatOffsetMinutes: Int,
): List<DayRow> {
    // Ḍuḥā hat keine Gebetszeile; vor dem Faǧr gehört man noch zum Vortag.
    val currentPrayer: Prayer? = if (!isToday || now.isBefore(today.fajr)) {
        null
    } else {
        when (heroState(today, karaha, nafl, nextFajr, now, isToday).moment) {
            Moment.FAJR -> Prayer.FAJR
            Moment.SHURUQ -> Prayer.SUNRISE
            Moment.DUHA -> null
            // Zawāl ist makrūh und keine Gebetszeile; Ẓuhr beginnt erst danach.
            Moment.ZAWAL -> null
            Moment.DHUHR -> Prayer.DHUHR
            Moment.ASR -> Prayer.ASR
            Moment.MAGHRIB -> Prayer.MAGHRIB
            Moment.ISHA -> Prayer.ISHA
        }
    }

    fun prayer(p: Prayer, at: ZonedDateTime) = DayRow.PrayerRow(
        prayer = p,
        at = at,
        past = isToday && !now.isBefore(at),
        current = p == currentPrayer,
    )

    fun side(kind: Section, start: ZonedDateTime, end: ZonedDateTime?) = DayRow.SideRow(
        kind = kind,
        start = start,
        end = end,
        past = isToday && !now.isBefore(end ?: start),
    )

    val rows = mutableListOf<DayRow>()
    rows += prayer(Prayer.FAJR, today.fajr)
    if (showCemaat) {
        rows += side(Section.JAMAA, today.sunrise.minusMinutes(cemaatOffsetMinutes.toLong()), null)
    }
    rows += prayer(Prayer.SUNRISE, today.sunrise)
    if (showKaraha) rows += side(Section.SHURUQ_MAKRUH, karaha.sunriseStart, karaha.sunriseEnd)
    rows += side(Section.DUHA, nafl.duhaStart, nafl.duhaEnd)
    if (showKaraha) rows += side(Section.ZUHR, karaha.zevalStart, karaha.zevalEnd)
    rows += prayer(Prayer.DHUHR, today.dhuhr)
    rows += prayer(Prayer.ASR, today.asr)
    if (showKaraha) rows += side(Section.ISFIRAR_MAKRUH, karaha.isfirarStart, karaha.isfirarEnd)
    rows += prayer(Prayer.MAGHRIB, today.maghrib)
    rows += side(Section.AWWABIN, nafl.awwabinStart, nafl.awwabinEnd)
    rows += prayer(Prayer.ISHA, today.isha)
    val tStart = nafl.tahajjudStart
    val tEnd = nafl.tahajjudEnd
    if (tStart != null && tEnd != null) rows += side(Section.TAHAJJUD, tStart, tEnd)
    if (isToday && nextFajr != null && !now.isBefore(today.isha)) {
        rows += DayRow.PrayerRow(Prayer.FAJR, nextFajr, past = false, current = false, nextDay = true)
    }
    return rows
}
