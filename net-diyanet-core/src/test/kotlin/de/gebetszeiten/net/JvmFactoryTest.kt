package de.gebetszeiten.net

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die JVM-Fabrik reicht den Index-Treffer als Standort-ID durch die Kette.
 * Netz ist im Test nicht erlaubt, deshalb wird die Kette selbst geprueft:
 * ein Index-Ort liefert seine ID, ohne dass die Namenssuche (Netz) laeuft.
 */
class JvmFactoryTest {

    @Test
    fun jvmFactoryUsesNearestPlaceInChain() = runBlocking {
        val place = DiyanetPlace(9541, "NUERNBERG", "BAYERN", "DE", 49.45, 11.07)
        val id = resolveLocationIdChain(
            bundledId = null,
            indexPlace = place,
            cachedId = null,
            searchByName = { error("kein Netz") },
        )
        assertEquals(9541, id)
    }
}
