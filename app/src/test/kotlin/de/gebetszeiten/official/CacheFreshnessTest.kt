package de.gebetszeiten.official

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// stampMatches liegt jetzt in core-prayertimes (CacheStampTest) — hier bleibt
// nur noch needsRefresh.
class CacheFreshnessTest {

    @Test fun refreshWhenStampMismatch() {
        assertTrue(needsRefresh(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 6), stampOk = false))
    }

    @Test fun refreshWhenCoverageThin() {
        // Nur noch 3 Tage Zukunft abgedeckt.
        assertTrue(needsRefresh(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 6), stampOk = true))
    }

    @Test fun noRefreshWhenFreshAndMatching() {
        assertFalse(needsRefresh(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 6), stampOk = true))
    }

    @Test fun refreshWhenEmpty() {
        assertTrue(needsRefresh(null, LocalDate.of(2026, 7, 6), stampOk = true))
    }
}
