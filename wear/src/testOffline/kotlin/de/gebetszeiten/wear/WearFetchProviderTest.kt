package de.gebetszeiten.wear

import org.junit.Assert.assertFalse
import org.junit.Test

class WearFetchProviderTest {
    @Test fun `der offline-Flavor der Uhr ruft nie ab`() {
        assertFalse(WearFetchProvider.isOnline)
    }
}
