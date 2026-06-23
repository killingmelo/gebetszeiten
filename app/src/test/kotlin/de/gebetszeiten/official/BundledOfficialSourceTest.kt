package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledOfficialSourceTest {

    @Test fun resolvesNurnbergRegardlessOfSpelling() {
        assertEquals(
            listOf("official/nuernberg-2026.tsv"),
            BundledOfficialSource.assetPathsFor("Nürnberg"),
        )
        assertEquals(
            listOf("official/nuernberg-2026.tsv"),
            BundledOfficialSource.assetPathsFor("  NÜRNBERG "),
        )
    }

    @Test fun unknownCityHasNoTable() {
        assertTrue(BundledOfficialSource.assetPathsFor("Berlin").isEmpty())
    }
}
