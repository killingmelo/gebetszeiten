package de.gebetszeiten.core.prayertimes.officialtimes

import java.time.LocalDate
import java.time.LocalTime

/**
 * Kompaktes Zeilenformat des amtlichen Zeiten-Caches — eine Zeile pro Tag:
 * "YYYY-MM-DD fajr sunrise dhuhr asr maghrib isha". Geteilt zwischen
 * Phone-Cache und Wear-Sync, damit das Format genau einmal existiert.
 */
object ScheduleText {

    fun serialize(schedule: Map<LocalDate, SixTimes>): String =
        schedule.entries
            .sortedBy { it.key }
            .joinToString("\n") { (d, t) ->
                "$d ${t.fajr} ${t.sunrise} ${t.dhuhr} ${t.asr} ${t.maghrib} ${t.isha}"
            }

    /** Tolerant: unlesbare Zeilen werden übersprungen (wie der bisherige
     *  Cache-Parser) — ein komplett kaputter Payload ergibt eine leere Map. */
    fun parse(text: String): Map<LocalDate, SixTimes> =
        text.lineSequence().mapNotNull { parseLine(it) }.toMap()

    private fun parseLine(line: String): Pair<LocalDate, SixTimes>? {
        val p = line.trim().split(" ")
        if (p.size != 7) return null
        return try {
            LocalDate.parse(p[0]) to SixTimes(
                fajr = LocalTime.parse(p[1]),
                sunrise = LocalTime.parse(p[2]),
                dhuhr = LocalTime.parse(p[3]),
                asr = LocalTime.parse(p[4]),
                maghrib = LocalTime.parse(p[5]),
                isha = LocalTime.parse(p[6]),
            )
        } catch (e: Exception) {
            null
        }
    }
}
