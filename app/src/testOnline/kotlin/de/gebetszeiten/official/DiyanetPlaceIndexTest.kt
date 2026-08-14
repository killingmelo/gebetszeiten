package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Der Index-Loader selbst braucht einen Context; hier wird geprüft, dass das
 *  ausgelieferte Asset über den Parser dieselbe Auflösung liefert, die der
 *  Fetcher später erwartet. Der Context-Pfad wird im Gerätecheck verifiziert. */
class DiyanetPlaceIndexTest {

    private val places by lazy {
        File("src/online/assets/official/locations-world.tsv")
            .useLines { parseDiyanetPlaces(it) }
    }

    @Test fun `Nuernberg loest auf einen deutschen Standort auf`() {
        val hit = de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
            .nearest(places, 49.4521, 11.0767)
        assertEquals("DE", hit?.countryCode)
    }

    @Test fun `Wien liegt in keinem 25-km-Radius eines tuerkischen Standorts`() {
        val hit = de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
            .nearest(places, 48.2082, 16.3738)
        // Wien selbst kann ein Diyanet-Standort sein; wenn ja, dann als AT.
        assertTrue("unerwartet: $hit", hit == null || hit.countryCode == "AT")
    }
}
