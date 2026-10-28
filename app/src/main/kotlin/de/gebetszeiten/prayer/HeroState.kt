package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import java.time.Duration
import java.time.ZonedDateTime

/** Der Abschnitt des Tages, in dem man sich gerade befindet. */
enum class Moment { FAJR, SHURUQ, DUHA, ZAWAL, DHUHR, ASR, MAGHRIB, ISHA }

/** Welches Titelbild zum Moment gehört. */
enum class PhotoKey { FAJR, SHURUQ, ZUHR, ASR, MAGHRIB, ISHA }

data class Upcoming(val prayer: Prayer, val at: ZonedDateTime)

data class MakruhLine(val start: ZonedDateTime, val end: ZonedDateTime, val state: KarahaCountdown.State)

data class HeroState(
    val moment: Moment,
    val photo: PhotoKey,
    /** null = nicht anzeigen */
    val remaining: Duration?,
    val next: Upcoming?,
    val makruh: MakruhLine?,
)

/** Ab so vielen Minuten vor Beginn wird ein Karāha-Abschnitt im Titel angekündigt. */
private const val MAKRUH_LEAD_MINUTES = 15L

private fun photoFor(moment: Moment) = when (moment) {
    Moment.FAJR -> PhotoKey.FAJR
    Moment.SHURUQ, Moment.DUHA -> PhotoKey.SHURUQ
    Moment.ZAWAL, Moment.DHUHR -> PhotoKey.ZUHR
    Moment.ASR -> PhotoKey.ASR
    Moment.MAGHRIB -> PhotoKey.MAGHRIB
    Moment.ISHA -> PhotoKey.ISHA
}

/** Erster Karāha-Abschnitt, der läuft oder höchstens 15 Minuten bevorsteht. */
private fun makruhLine(karaha: KarahaTimes, now: ZonedDateTime): MakruhLine? {
    val windows = listOf(
        karaha.sunriseStart to karaha.sunriseEnd,
        karaha.zevalStart to karaha.zevalEnd,
        karaha.isfirarStart to karaha.isfirarEnd,
    )
    for ((start, end) in windows) {
        val state = KarahaCountdown.state(now, start, end) ?: continue
        val soon = Duration.between(now, start) <= Duration.ofMinutes(MAKRUH_LEAD_MINUTES)
        if (state.active || soon) return MakruhLine(start, end, state)
    }
    return null
}

fun heroState(
    today: DailyPrayerTimes,
    karaha: KarahaTimes,
    nafl: NaflTimes,
    nextFajr: ZonedDateTime?,
    now: ZonedDateTime,
    isToday: Boolean,
): HeroState {
    if (!isToday) {
        return HeroState(Moment.FAJR, PhotoKey.FAJR, remaining = null, next = null, makruh = null)
    }

    // Moment, Ende des laufenden Abschnitts und nächstes Pflichtgebet.
    val moment: Moment
    val until: ZonedDateTime?
    val next: Upcoming?
    when {
        now.isBefore(today.fajr) -> {
            moment = Moment.ISHA
            until = today.fajr
            next = Upcoming(Prayer.FAJR, today.fajr)
        }
        now.isBefore(today.sunrise) -> {
            moment = Moment.FAJR
            until = today.sunrise
            next = Upcoming(Prayer.DHUHR, today.dhuhr)
        }
        now.isBefore(nafl.duhaStart) -> {
            moment = Moment.SHURUQ
            until = today.dhuhr
            next = Upcoming(Prayer.DHUHR, today.dhuhr)
        }
        now.isBefore(karaha.zevalStart) -> {
            moment = Moment.DUHA
            until = today.dhuhr
            next = Upcoming(Prayer.DHUHR, today.dhuhr)
        }
        // Ab Beginn der Zawāl-Zeit (= Ende des Ḍuḥā-Fensters) ist Ḍuḥā vorbei
        // und die Zeit makrūh; der Titel sagt das, bis Ẓuhr beginnt.
        now.isBefore(today.dhuhr) -> {
            moment = Moment.ZAWAL
            until = today.dhuhr
            next = Upcoming(Prayer.DHUHR, today.dhuhr)
        }
        now.isBefore(today.asr) -> {
            moment = Moment.DHUHR
            until = today.asr
            next = Upcoming(Prayer.ASR, today.asr)
        }
        now.isBefore(today.maghrib) -> {
            moment = Moment.ASR
            until = today.maghrib
            next = Upcoming(Prayer.MAGHRIB, today.maghrib)
        }
        now.isBefore(today.isha) -> {
            moment = Moment.MAGHRIB
            until = today.isha
            next = Upcoming(Prayer.ISHA, today.isha)
        }
        else -> {
            moment = Moment.ISHA
            until = nextFajr
            next = nextFajr?.let { Upcoming(Prayer.FAJR, it) }
        }
    }

    return HeroState(
        moment = moment,
        photo = photoFor(moment),
        remaining = until?.let { Duration.between(now, it) },
        next = next,
        makruh = makruhLine(karaha, now),
    )
}
