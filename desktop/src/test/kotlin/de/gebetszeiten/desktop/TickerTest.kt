package de.gebetszeiten.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TickerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 25)

    private fun at(h: Int, m: Int, s: Int = 0): ZonedDateTime =
        ZonedDateTime.of(day, LocalTime.of(h, m, s), zone)

    @Test
    fun nextRunIsEarlierOfChangeAndEvent() {
        val now = at(12, 0)
        assertEquals(at(12, 0, 20), nextRunAt(now, nextChange = at(12, 0, 40), nextEvent = at(12, 0, 20)))
        assertEquals(at(12, 0, 15), nextRunAt(now, nextChange = at(12, 0, 15), nextEvent = at(12, 0, 50)))
        // Nur eines bekannt: das gilt.
        assertEquals(at(12, 0, 30), nextRunAt(now, nextChange = null, nextEvent = at(12, 0, 30)))
        assertEquals(at(12, 0, 45), nextRunAt(now, nextChange = at(12, 0, 45), nextEvent = null))
    }

    @Test
    fun nullsFallBackToOneMinute() {
        val now = at(12, 0)
        assertEquals(at(12, 1), nextRunAt(now, nextChange = null, nextEvent = null))
    }

    @Test
    fun neverLaterThanOneMinute() {
        // dueEvents laesst nur 2 Minuten Spiel: der Takt darf nie laenger schlafen.
        val now = at(12, 0)
        assertEquals(at(12, 1), nextRunAt(now, nextChange = at(15, 30), nextEvent = at(13, 0)))
    }

    @Test
    fun pastTargetRunsAtOnce() {
        // Uhr vorgestellt: der geplante Zeitpunkt liegt schon zurueck.
        val now = at(12, 0)
        assertEquals(now, nextRunAt(now, nextChange = at(11, 59), nextEvent = at(13, 0)))
    }

    @Test
    fun wallClockJumpOnlyBeyondNinetySeconds() {
        val mono0 = 1_000_000_000L
        val wall0 = 1_800_000_000_000L
        // 60 s vergangen, Wanduhr 60 s weiter: alles normal.
        assertFalse(wallClockJumped(wall0, mono0, wall0 + 60_000, mono0 + 60_000_000_000L))
        // 80 s Abweichung: noch kein Sprung.
        assertFalse(wallClockJumped(wall0, mono0, wall0 + 140_000, mono0 + 60_000_000_000L))
        // Ruhezustand / Uhr vorgestellt: Wanduhr 2 h weiter, Monotonuhr 60 s.
        assertTrue(wallClockJumped(wall0, mono0, wall0 + 7_200_000, mono0 + 60_000_000_000L))
        // Uhr zurueckgestellt.
        assertTrue(wallClockJumped(wall0, mono0, wall0 - 120_000, mono0 + 60_000_000_000L))
    }

    @Test
    fun dataDirPropertyBeforeEnvironmentBeforeAppData() {
        assertEquals(Path.of("C:\\p"), resolveDataDir(property = "C:\\p", environment = "C:\\e", appData = "C:\\a"))
        assertEquals(Path.of("C:\\e"), resolveDataDir(property = null, environment = "C:\\e", appData = "C:\\a"))
        assertEquals(Path.of("C:\\e"), resolveDataDir(property = " ", environment = "C:\\e", appData = "C:\\a"))
        assertEquals(Path.of("C:\\a", "Gebetszeiten"), resolveDataDir(property = null, environment = null, appData = "C:\\a"))
    }

    @Test
    fun popupSourceLineNamesOriginAndDate() {
        val updated = at(9, 30).toInstant().toEpochMilli()
        assertEquals("Diyanet · Stand 25.10.2026", sourceLine(hasTimes = true, fromCache = true, updatedEpochMs = updated, zone = zone))
        assertEquals("Diyanet · mitgelieferte Tabelle", sourceLine(hasTimes = true, fromCache = false, updatedEpochMs = null, zone = zone))
        assertEquals("Keine amtlichen Zeiten", sourceLine(hasTimes = false, fromCache = false, updatedEpochMs = updated, zone = zone))
    }

    @Test
    fun secondInstanceIsRefused() {
        val dir = Files.createTempDirectory("gebetszeiten-lock")
        val first = SingleInstance.tryAcquire(dir)
        assertNotNull(first)
        assertNull(SingleInstance.tryAcquire(dir))
        first!!.release()
        val again = SingleInstance.tryAcquire(dir)
        assertNotNull(again)
        again!!.release()
    }
}
