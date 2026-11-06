package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.TimeOffsets
import de.gebetszeiten.prayer.KarahaTimes
import de.gebetszeiten.prayer.Moment
import de.gebetszeiten.prayer.NaflTimes
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.prayer.offsetMark
import de.gebetszeiten.prayer.timeRange
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Die heutigen Zeiten, die „Verstehen“ braucht (bereits um die Verschiebungen des Nutzers verschoben). */
data class VerstehenDay(
    val times: DailyPrayerTimes,
    val karaha: KarahaTimes,
    val nafl: NaflTimes,
    val nextFajr: ZonedDateTime?,
)

/** Teile, deren Zeit die App selbst rechnet (nicht amtlich). */
val GERECHNET: Set<Teil> = setOf(Teil.DUHA, Teil.AWWABIN, Teil.TAHAJJUD, Teil.SHURUQ, Teil.ZAWAL, Teil.ISFIRAR)

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Eine Zeitangabe samt Verschiebungsmarke, falls für [prayer] eine eingestellt ist. */
private fun uhr(at: ZonedDateTime, prayer: Prayer?, offsets: TimeOffsets): String {
    val mark = prayer?.let { offsetMark(offsets.of(it)) }
    return at.format(HM) + (mark?.let { " ($it)" } ?: "")
}

/**
 * Die Zeitzeile eines Teils für „Heute“ ([day] null ergibt null). Amtliche Zeiten
 * tragen die Verschiebungsmarke ihres Gebets (die Zeiten selbst sind schon
 * verschoben), gerechnete Zeiten sind als solche gekennzeichnet und tragen keine.
 * Fehlt das Ende (ʿIšāʾ ohne Faǧr von morgen), steht nur der Beginn; fehlt bei
 * einem gerechneten Teil die Zeit ganz (Tahaǧǧud), gibt es keine Zeile.
 */
fun zeitzeile(teil: Teil, day: VerstehenDay?, ort: String, offsets: TimeOffsets): String? {
    if (day == null) return null
    val t = day.times
    val gerechnet = teil in GERECHNET
    val a: String
    val b: String?
    when (teil) {
        Teil.FAJR -> { a = uhr(t.fajr, Prayer.FAJR, offsets); b = uhr(t.sunrise, Prayer.SUNRISE, offsets) }
        Teil.ZUHR -> { a = uhr(t.dhuhr, Prayer.DHUHR, offsets); b = uhr(t.asr, Prayer.ASR, offsets) }
        Teil.ASR -> { a = uhr(t.asr, Prayer.ASR, offsets); b = uhr(t.maghrib, Prayer.MAGHRIB, offsets) }
        Teil.MAGHRIB -> { a = uhr(t.maghrib, Prayer.MAGHRIB, offsets); b = uhr(t.isha, Prayer.ISHA, offsets) }
        Teil.ISHA -> {
            a = uhr(t.isha, Prayer.ISHA, offsets)
            b = day.nextFajr?.let { uhr(it, Prayer.FAJR, offsets) }
        }
        Teil.SHURUQ -> { a = day.karaha.sunriseStart.format(HM); b = day.karaha.sunriseEnd.format(HM) }
        // Gerechnete Zeile: auch das Ende (Beginn von Ẓuhr) trägt keine Verschiebungsmarke.
        Teil.ZAWAL -> { a = day.karaha.zevalStart.format(HM); b = t.dhuhr.format(HM) }
        Teil.ISFIRAR -> { a = day.karaha.isfirarStart.format(HM); b = day.karaha.isfirarEnd.format(HM) }
        Teil.DUHA -> { a = day.nafl.duhaStart.format(HM); b = day.nafl.duhaEnd.format(HM) }
        Teil.AWWABIN -> { a = day.nafl.awwabinStart.format(HM); b = day.nafl.awwabinEnd.format(HM) }
        Teil.TAHAJJUD -> {
            a = (day.nafl.tahajjudStart ?: return null).format(HM)
            b = (day.nafl.tahajjudEnd ?: return null).format(HM)
        }
    }
    return when {
        b == null -> "Heute in $ort ab $a"
        gerechnet -> "Heute etwa ${timeRange(a, b)}, von der App gerechnet"
        else -> "Heute in $ort ${timeRange(a, b)}"
    }
}

/** Sprungziel in der Lehre: Kapitel (0 bis 4 Gebete, 5 „Freiwillig und verpönt“) und Teil. */
data class Ziel(val kapitel: Int, val teil: Teil)

private const val KAPITEL_FREIWILLIG = 5

/** Wohin ein Sprung aus „Heute“ von [section] führt. */
fun zielVon(section: Section): Ziel = when (section) {
    Section.FAJR, Section.JAMAA -> Ziel(0, Teil.FAJR)
    Section.ZUHR -> Ziel(1, Teil.ZUHR)
    Section.ASR -> Ziel(2, Teil.ASR)
    Section.MAGHRIB -> Ziel(3, Teil.MAGHRIB)
    Section.ISHA -> Ziel(4, Teil.ISHA)
    Section.SHURUQ_MAKRUH -> Ziel(KAPITEL_FREIWILLIG, Teil.SHURUQ)
    Section.DUHA -> Ziel(KAPITEL_FREIWILLIG, Teil.DUHA)
    Section.ISFIRAR_MAKRUH -> Ziel(KAPITEL_FREIWILLIG, Teil.ISFIRAR)
    Section.AWWABIN -> Ziel(KAPITEL_FREIWILLIG, Teil.AWWABIN)
    Section.TAHAJJUD -> Ziel(KAPITEL_FREIWILLIG, Teil.TAHAJJUD)
}

/** Wohin „Verstehen“ beim laufenden [moment] öffnet. */
fun zielVonMoment(moment: Moment): Ziel = when (moment) {
    Moment.FAJR -> Ziel(0, Teil.FAJR)
    Moment.SHURUQ -> Ziel(KAPITEL_FREIWILLIG, Teil.SHURUQ)
    Moment.DUHA -> Ziel(KAPITEL_FREIWILLIG, Teil.DUHA)
    Moment.ZAWAL -> Ziel(KAPITEL_FREIWILLIG, Teil.ZAWAL)
    Moment.DHUHR -> Ziel(1, Teil.ZUHR)
    Moment.ASR -> Ziel(2, Teil.ASR)
    Moment.MAGHRIB -> Ziel(3, Teil.MAGHRIB)
    Moment.ISHA -> Ziel(4, Teil.ISHA)
}
