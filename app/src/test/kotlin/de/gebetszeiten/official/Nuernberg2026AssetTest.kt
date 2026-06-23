package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Liest das committete Asset über seinen Repo-Pfad (Unit-Test-CWD = app-Modul). */
class Nuernberg2026AssetTest {

    private val table: Map<LocalDate, SixTimes> by lazy {
        File("src/main/assets/official/nuernberg-2026.tsv")
            .useLines { parseOfficialTimes(it) }
    }

    @Test fun coversWholeYear2026() {
        assertEquals(365, table.size)
        assertEquals(LocalDate.of(2026, 1, 1), table.keys.min())
        assertEquals(LocalDate.of(2026, 12, 31), table.keys.max())
    }

    @Test fun matchesOfficialReferenceForJune7() {
        val t = table.getValue(LocalDate.of(2026, 6, 7))
        assertEquals(LocalTime.of(3, 35), t.fajr)
        assertEquals(LocalTime.of(5, 4), t.sunrise)
        assertEquals(LocalTime.of(13, 20), t.dhuhr)
        assertEquals(LocalTime.of(17, 36), t.asr)
        assertEquals(LocalTime.of(21, 25), t.maghrib)
        assertEquals(LocalTime.of(22, 45), t.isha)
    }
}
