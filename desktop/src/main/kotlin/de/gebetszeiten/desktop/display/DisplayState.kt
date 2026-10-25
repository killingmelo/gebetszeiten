package de.gebetszeiten.desktop.display

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.display.displayStepBoundaries
import de.gebetszeiten.core.prayertimes.display.germanName
import de.gebetszeiten.core.prayertimes.display.isUrgent
import de.gebetszeiten.core.prayertimes.display.karahaLine
import de.gebetszeiten.core.prayertimes.display.nextDisplayBoundary
import de.gebetszeiten.core.prayertimes.display.nextPrayer
import de.gebetszeiten.core.prayertimes.display.remainingStepLabel
import de.gebetszeiten.core.prayertimes.display.remainingStepShort
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Farbton der Taskleisten-Anzeige. Karaha geht vor Dringlichkeit. */
enum class Tone { NORMAL, URGENT, KARAHA_SOON, KARAHA_ACTIVE, NO_TIMES }

/**
 * Was die Taskleiste zeigt: zwei Zeilen, Farbton, Tooltip — und der Zeitpunkt,
 * an dem sich etwas aendert (danach neu berechnen, dazwischen nie).
 */
data class DisplayState(
    val line1: String,
    val line2: String,
    val tone: Tone,
    val tooltip: String,
    val nextChangeAt: ZonedDateTime?,
)

private val NO_TIMES_STATE = DisplayState(
    line1 = "Keine Zeiten",
    line2 = "",
    tone = Tone.NO_TIMES,
    tooltip = "Keine amtlichen Zeiten für diesen Ort",
    nextChangeAt = null,
)

/**
 * Reine Funktion aus denselben Bausteinen wie Widget und Benachrichtigung der
 * Handy-App. Karaha-Fenster stammen nur aus [today]; nach Isha gibt es bis zum
 * Sonnenaufgang des Folgetags keines — der Tageswechsel steckt in
 * [DisplayState.nextChangeAt] (naechste Mitternacht).
 */
fun displayState(
    now: ZonedDateTime,
    today: DailyPrayerTimes?,
    tomorrow: DailyPrayerTimes?,
    showKaraha: Boolean,
): DisplayState {
    if (today == null) return NO_TIMES_STATE
    val next = nextPrayer(today, tomorrow, now) ?: return NO_TIMES_STATE

    // Echte verstrichene Zeit, auch ueber die Zeitumstellung hinweg.
    val remaining = Duration.between(now, next.time)
    val name = next.prayer.germanName()

    val windows = Karaha.windows(today)
    val karaha = if (showKaraha) karahaLine(Karaha.status(windows, now)) else null

    val tone = when {
        karaha != null && karaha.active -> Tone.KARAHA_ACTIVE
        karaha != null -> Tone.KARAHA_SOON
        isUrgent(remaining) -> Tone.URGENT
        else -> Tone.NORMAL
    }

    val short = remainingStepShort(remaining)
    val base = if (short.isEmpty()) "$name jetzt" else "Noch $short bis $name"
    val tooltip = if (karaha != null) "$base · ${karaha.text}" else base

    val nowMs = now.toInstant().toEpochMilli()
    val candidates = buildList {
        add(next.time)
        nextDisplayBoundary(displayStepBoundaries(next.time.toInstant().toEpochMilli(), nowMs), nowMs)
            ?.let { add(Instant.ofEpochMilli(it).atZone(now.zone)) }
        if (showKaraha) addAll(Karaha.boundaries(windows))
        add(now.toLocalDate().plusDays(1).atStartOfDay(now.zone))
    }

    return DisplayState(
        line1 = name,
        line2 = karaha?.text ?: remainingStepLabel(remaining),
        tone = tone,
        tooltip = tooltip,
        nextChangeAt = candidates.filter { it.isAfter(now) }.minOrNull(),
    )
}
