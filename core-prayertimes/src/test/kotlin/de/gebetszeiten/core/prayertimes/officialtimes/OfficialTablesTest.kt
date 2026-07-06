package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OfficialTablesTest {

    private val sample = sequenceOf(
        "2026-01-01\t06:15\t08:05\t12:24\t14:13\t16:33\t18:11",
        "",                                   // leere Zeile ignorieren
        "kaputt\t06:15",                      // defekte Zeile ignorieren
        "2026-06-07\t03:35\t05:04\t13:20\t17:36\t21:25\t22:45",
    )

    @Test fun parsesValidRowsAndMapsColumns() {
        val map = parseOfficialTimes(sample)
        val jan1 = map.getValue(LocalDate.of(2026, 1, 1))
        assertEquals(LocalTime.of(6, 15), jan1.fajr)     // imsak
        assertEquals(LocalTime.of(8, 5), jan1.sunrise)   // gunes
        assertEquals(LocalTime.of(12, 24), jan1.dhuhr)   // ogle
        assertEquals(LocalTime.of(14, 13), jan1.asr)     // ikindi
        assertEquals(LocalTime.of(16, 33), jan1.maghrib) // aksam
        assertEquals(LocalTime.of(18, 11), jan1.isha)    // yatsi
    }

    @Test fun skipsBrokenLines() {
        val map = parseOfficialTimes(sample)
        assertEquals(2, map.size)
    }

    @Test fun missingDateReturnsNull() {
        val map = parseOfficialTimes(sample)
        assertNull(map[LocalDate.of(2027, 1, 1)])
    }
}
