package de.gebetszeiten.wear

import org.junit.Assert.assertTrue
import org.junit.Test

class WearFetchProviderTest {
    @Test fun `der online-Flavor der Uhr kann abrufen`() {
        assertTrue(WearFetchProvider.isOnline)
    }
}
