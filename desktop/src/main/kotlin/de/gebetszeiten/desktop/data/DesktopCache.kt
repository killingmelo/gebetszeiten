package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.officialtimes.CacheEntry
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.DueLocation
import de.gebetszeiten.core.prayertimes.officialtimes.RawEntry
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.refreshedHeader
import de.gebetszeiten.core.prayertimes.officialtimes.withAttempt
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * Zeiten-Cache des Desktops: dieselbe Speicherschicht ([CacheStore]) wie in
 * der Handy-App, nur als eine UTF-8-Datei statt DataStore. Es gibt keine
 * Favoriten, also keine angehefteten Orte; hoechstens [MAX_ENTRIES] Orte
 * mit Zeitplan. Geschrieben wird atomar (Temp-Datei + Verschieben), damit ein
 * Abbruch nie einen halben Cache hinterlaesst.
 */
class DesktopCache(private val file: Path) {

    /** Zeiten nur, wenn es fuer (lat,lng) einen Eintrag gibt - sonst null,
     *  nie die eines anderen Standorts. */
    @Synchronized
    fun get(date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val entry = CacheStore.select(readEntries(), lat, lng) ?: return null
        return ScheduleText.parseDay(entry.body, date)
    }

    @Synchronized
    fun cachedLocationId(lat: Double, lng: Double): Int? =
        CacheStore.select(readEntries(), lat, lng)?.header?.locationId

    /** Zeitpunkt des letzten erfolgreichen Abrufs fuer (lat,lng), oder null. */
    @Synchronized
    fun updatedEpochMs(lat: Double, lng: Double): Long? =
        CacheStore.select(readEntries(), lat, lng)?.header?.updatedEpochMs

    /** Erfolgreichen Abruf ablegen; das Versuchsprotokoll des Ortes bleibt. */
    @Synchronized
    fun putAll(
        schedule: Map<LocalDate, SixTimes>,
        lat: Double,
        lng: Double,
        locationId: Int?,
        verification: Verification?,
    ) {
        if (schedule.isEmpty()) return
        val entries = readEntries()
        val added = CacheEntry(
            header = refreshedHeader(
                existing = CacheStore.select(entries, lat, lng)?.header,
                lat = lat,
                lng = lng,
                locationId = locationId,
                updatedEpochMs = System.currentTimeMillis(),
                verification = verification,
            ),
            schedule = schedule,
        )
        write(CacheStore.put(entries, added, pinnedCoords = emptyList(), maxUnpinned = MAX_ENTRIES))
    }

    /** Zeitpunkt und Fehlergrund des letzten Versuchs; [error] = null heisst
     *  Erfolg. Ohne Eintrag entsteht einer mit leerem Zeitplan (haelt die
     *  Statuszeile am Leben und bremst die Auswahl, siehe `DueLocation.hopeless`). */
    @Synchronized
    fun recordAttempt(error: String?, nowEpochMs: Long, lat: Double, lng: Double) {
        write(
            CacheStore.withAttempt(
                entries = readEntries(),
                error = error,
                nowEpochMs = nowEpochMs,
                lat = lat,
                lng = lng,
                pinnedCoords = emptyList(),
                maxUnpinned = MAX_ENTRIES,
            ),
        )
    }

    @Synchronized
    fun dueOrder(active: Pair<Double, Double>, today: LocalDate): List<DueLocation> =
        CacheStore.dueOrder(readEntries(), emptyList(), active, today)

    /** Fehlende oder unlesbare Datei = leerer Cache; kaputte Eintraege
     *  ueberspringt [CacheStore.split]. */
    private fun readEntries(): List<RawEntry> {
        if (!Files.isRegularFile(file)) return emptyList()
        return try {
            CacheStore.split(String(Files.readAllBytes(file), StandardCharsets.UTF_8))
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun write(entries: List<RawEntry>) {
        val dir = file.toAbsolutePath().parent
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "cache", ".tmp")
        try {
            Files.write(tmp, CacheStore.serializeRaw(entries).toByteArray(StandardCharsets.UTF_8))
            atomicReplace(tmp, file)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private companion object {
        /** Wie in der Handy-App: fuenf nicht angeheftete Orte. */
        const val MAX_ENTRIES = 5
    }
}
