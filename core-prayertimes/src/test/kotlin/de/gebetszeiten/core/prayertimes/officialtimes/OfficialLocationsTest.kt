package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialLocationsTest {

    private val nuernberg = OfficialLocation(9541, "Nürnberg", 49.45421, 11.07752, "t001")
    private val fuerth = OfficialLocation(9327, "Fürth", 49.47593, 10.98856, "t001")
    private val berlin = OfficialLocation(11002, "Berlin", 52.52437, 13.41053, "t002")

    @Test
    fun parsesIndexLinesAndSkipsBroken() {
        val list = parseOfficialLocations(
            sequenceOf(
                "9541\tNürnberg\t49.45421\t11.07752\tt001",
                "kaputt ohne tabs",
                "11002\tBerlin\t52.52437\t13.41053\tt002",
            ),
        )
        assertEquals(listOf(nuernberg, berlin), list)
    }

    @Test
    fun nearestPicksClosestLocation() {
        // Zirndorf (49.442, 10.955) liegt näher an Fürth als an Nürnberg.
        val hit = OfficialLocations.nearest(listOf(nuernberg, fuerth, berlin), 49.442, 10.955)
        assertEquals("Fürth", hit?.name)
    }

    @Test
    fun nearestNullBeyondMaxDistance() {
        // Wien ist > 25 km von jedem deutschen Standort entfernt.
        assertNull(OfficialLocations.nearest(listOf(nuernberg, fuerth, berlin), 48.208, 16.372))
    }

    @Test
    fun nearestNullOnEmptyList() {
        assertNull(OfficialLocations.nearest(emptyList(), 49.45, 11.07))
    }
}
