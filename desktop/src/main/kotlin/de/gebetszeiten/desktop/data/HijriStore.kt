package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.HijriDay
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** So weit voraus soll das Hicri-Datum bekannt sein (Kandil am Vorabend, Ramazan-Beginn). */
private const val AHEAD_DAYS = 30L

/** Vorgehalten wird hoechstens so weit zurueck. */
private const val KEEP_PAST_DAYS = 7L

/**
 * Hicri-Daten von Diyanet, eine Zeile je Tag (`2026-10-27\t16\t5\t1448`), in
 * `hijri.txt`. Getrennt vom Zeiten-Cache, dessen Format die Handy-App teilt.
 * Fehlt ein Tag, gibt es kein Hicri-Datum dafuer - nie ein berechnetes.
 */
class HijriStore(private val file: Path) {

    @Synchronized
    fun get(date: LocalDate): HijriDay? = read()[date]

    /** Neue Tage dazu, alte (vor heute - 7) weg; Neues gewinnt bei Ueberschneidung. */
    @Synchronized
    fun putAll(days: Map<LocalDate, HijriDay>, today: LocalDate) {
        if (days.isEmpty()) return
        val merged = (read() + days).filterKeys { !it.isBefore(today.minusDays(KEEP_PAST_DAYS)) }.toSortedMap()
        val text = merged.entries.joinToString("") { (d, h) -> "$d\t${h.day}\t${h.month}\t${h.year}\n" }
        val dir = file.toAbsolutePath().parent
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "hijri", ".tmp")
        try {
            Files.write(tmp, text.toByteArray(StandardCharsets.UTF_8))
            atomicReplace(tmp, file)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** True, wenn heute bis heute + 30 nicht lueckenlos bekannt ist. */
    @Synchronized
    fun needsRefresh(today: LocalDate): Boolean {
        val known = read()
        return (0..AHEAD_DAYS).any { today.plusDays(it) !in known }
    }

    private fun read(): Map<LocalDate, HijriDay> {
        if (!Files.isRegularFile(file)) return emptyMap()
        val lines = try {
            Files.readAllLines(file, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            return emptyMap()
        }
        return lines.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 4) return@mapNotNull null
            runCatching { LocalDate.parse(f[0]) to HijriDay(f[1].toInt(), f[2].toInt(), f[3].toInt()) }.getOrNull()
        }.toMap()
    }
}
