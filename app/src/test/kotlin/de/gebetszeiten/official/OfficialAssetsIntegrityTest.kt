package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Verifiziert die Pipeline-Ausgabe: Index konsistent, Tabellen vollständig
 *  und monoton, Nürnberg reproduziert die amtliche Phase-1-Referenz. */
class OfficialAssetsIntegrityTest {

    private val assets = File("../shared-assets/official")
    private val locations by lazy {
        File(assets, "locations-de.tsv").useLines { parseOfficialLocations(it) }
    }

    @Test fun indexIsSubstantialAndInGermanBounds() {
        assertTrue("nur ${locations.size} Standorte", locations.size >= 500)
        locations.forEach {
            assertTrue("${it.name}: lat ${it.latitude}", it.latitude in 47.0..55.5)
            assertTrue("${it.name}: lng ${it.longitude}", it.longitude in 5.5..15.5)
        }
        assertTrue(locations.any { it.name == "Nürnberg" })
        assertTrue(locations.any { it.name == "Berlin" })
    }

    @Test fun everyReferencedTableExistsCompleteAndOrdered() {
        val year = 2026
        locations.map { it.tableRef }.distinct().forEach { ref ->
            val f = File(assets, "tables/$ref-$year.tsv")
            assertTrue("$ref fehlt", f.isFile)
            val table = f.useLines { parseOfficialTimes(it) }
            assertEquals("$ref unvollständig", if (year % 4 == 0) 366 else 365, table.size)
            table.forEach { (date, t) ->
                val ordered = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
                assertEquals("$ref $date nicht aufsteigend", ordered.sorted(), ordered)
            }
        }
    }

    @Test fun nuernbergReproducesPhase1Reference() {
        val nbg = locations.first { it.name == "Nürnberg" }
        val table = File(assets, "tables/${nbg.tableRef}-2026.tsv").useLines { parseOfficialTimes(it) }
        val t = table.getValue(LocalDate.of(2026, 6, 7))
        assertEquals(LocalTime.of(3, 35), t.fajr)
        assertEquals(LocalTime.of(5, 4), t.sunrise)
        assertEquals(LocalTime.of(13, 20), t.dhuhr)
        assertEquals(LocalTime.of(17, 36), t.asr)
        assertEquals(LocalTime.of(21, 25), t.maghrib)
        assertEquals(LocalTime.of(22, 45), t.isha)
    }
}
