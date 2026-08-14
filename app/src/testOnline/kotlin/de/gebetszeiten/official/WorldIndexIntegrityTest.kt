package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Wächter über die Pipeline-Ausgabe. Der Serdivan-Test ist der
 *  Regressionsschutz für den Bug, der diesen Umbau ausgelöst hat. */
class WorldIndexIntegrityTest {

    private val places by lazy {
        File("src/online/assets/official/locations-world.tsv")
            .useLines { parseDiyanetPlaces(it) }
    }

    @Test fun indexIstSubstantiell() {
        assertTrue("nur ${places.size} Standorte", places.size >= 6000)
    }

    @Test fun keineDoppeltenIds() {
        val dupes = places.groupBy { it.diyanetId }.filter { it.value.size > 1 }.keys
        assertEquals("doppelte IDs: $dupes", emptySet<Int>(), dupes)
    }

    @Test fun koordinatenUndFelderPlausibel() {
        places.forEach {
            assertTrue("${it.name}: lat ${it.latitude}", it.latitude in -90.0..90.0)
            assertTrue("${it.name}: lng ${it.longitude}", it.longitude in -180.0..180.0)
            assertTrue("${it.name}: id ${it.diyanetId}", it.diyanetId > 0)
            assertEquals("${it.name}: Ländercode", 2, it.countryCode.length)
            assertTrue("leerer Name bei id ${it.diyanetId}", it.name.isNotBlank())
        }
    }

    @Test fun serdivanFindetSakarya() {
        // DER Regressionstest: Serdivan (40.77376/30.38006) hat keinen eigenen
        // Diyanet-Eintrag und muss ueber Adapazari (id 9807) aufgeloest werden.
        val hit = DiyanetPlaces.nearest(places, 40.77376, 30.38006)
        assertNotNull("Serdivan findet keinen Diyanet-Standort", hit)
        assertEquals(9807, hit!!.diyanetId)
        assertTrue("zu weit: ${DiyanetPlaces.distanceKm(hit, 40.77376, 30.38006)} km",
            DiyanetPlaces.distanceKm(hit, 40.77376, 30.38006) < 5.0)
    }

    @Test fun tuerkeiUndDeutschlandBreitAbgedeckt() {
        assertTrue("TR zu duenn", places.count { it.countryCode == "TR" } >= 800)
        assertTrue("DE zu duenn", places.count { it.countryCode == "DE" } >= 900)
    }

    @Test fun grossstaedteVorhanden() {
        // Schreibweisen wortgetreu wie Diyanet sie fuehrt (live geprueft):
        // "NURNBERG" OHNE Umlaut, "İSTANBUL" MIT gepunktetem I. `ignoreCase`
        // gleicht Ü und U nicht aus — hier keine Schreibweise raten.
        listOf("TR" to "İSTANBUL", "TR" to "SAKARYA", "DE" to "NURNBERG", "DE" to "BERLIN")
            .forEach { (cc, name) ->
                assertTrue("$cc/$name fehlt",
                    places.any { it.countryCode == cc && it.name.equals(name, ignoreCase = true) })
            }
    }
}
