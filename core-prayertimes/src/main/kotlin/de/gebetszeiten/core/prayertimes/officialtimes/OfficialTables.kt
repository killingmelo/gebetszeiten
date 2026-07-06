package de.gebetszeiten.core.prayertimes.officialtimes

import java.time.LocalDate
import java.time.LocalTime

/**
 * Parst eine amtliche Tagestabelle (TSV ohne Header):
 *   date  fajr  sunrise  dhuhr  asr  maghrib  isha   (Tab-getrennt, ISO-Werte)
 * Defekte oder leere Zeilen werden übersprungen.
 */
fun parseOfficialTimes(lines: Sequence<String>): Map<LocalDate, SixTimes> =
    lines.mapNotNull { parseLine(it) }.toMap()

private fun parseLine(line: String): Pair<LocalDate, SixTimes>? {
    val p = line.trim().split('\t')
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
