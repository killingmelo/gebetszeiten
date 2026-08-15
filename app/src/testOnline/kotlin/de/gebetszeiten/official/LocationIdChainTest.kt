package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reihenfolge der ID-Aufloesung: Bundle -> Index -> Cache -> Namenssuche.
 *
 * Die Namenssuche war bis 2026-08 der Primaerweg und damit die Ursache des
 * Serdivan-Fehlers: Diyanet fuehrt keinen Standort dieses Namens, also lieferte
 * sie null und der Ort fiel auf die eigene Berechnung zurueck. Sie darf nur
 * noch als letzte Stufe laufen.
 */
class LocationIdChainTest {

    /** Echte Werte: Serdivan hat keinen eigenen Diyanet-Eintrag und wird ueber
     *  den Standort SAKARYA (Adapazari, 2,1 km entfernt) aufgeloest. */
    private val sakarya = DiyanetPlace(9807, "SAKARYA", "SAKARYA", "TR", 40.78056, 30.40333)

    @Test
    fun `Index greift vor Cache und Namenssuche`() = runBlocking {
        var nameCalls = 0
        val id = resolveLocationIdChain(
            bundledId = null,
            indexPlace = sakarya,
            cachedId = 1234,
            searchByName = { nameCalls++; 5678 },
        )
        assertEquals(9807, id)
        assertEquals("Namenssuche darf nicht laufen", 0, nameCalls)
    }

    @Test
    fun `Bundle schlaegt den Index`() = runBlocking {
        val id = resolveLocationIdChain(
            bundledId = 11024,
            indexPlace = sakarya,
            cachedId = null,
            searchByName = { null },
        )
        assertEquals(11024, id)
    }

    @Test
    fun `ohne Bundle und Index kommt der Cache`() = runBlocking {
        var nameCalls = 0
        val id = resolveLocationIdChain(
            bundledId = null,
            indexPlace = null,
            cachedId = 1234,
            searchByName = { nameCalls++; 5678 },
        )
        assertEquals(1234, id)
        assertEquals(0, nameCalls)
    }

    @Test
    fun `Namenssuche bleibt die letzte Rettung`() = runBlocking {
        val id = resolveLocationIdChain(
            bundledId = null,
            indexPlace = null,
            cachedId = null,
            searchByName = { 5678 },
        )
        assertEquals(5678, id)
    }

    @Test
    fun `nichts aufloesbar bleibt null`() = runBlocking {
        assertNull(
            resolveLocationIdChain(
                bundledId = null,
                indexPlace = null,
                cachedId = null,
                searchByName = { null },
            ),
        )
    }
}
