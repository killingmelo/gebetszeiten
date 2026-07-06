package de.gebetszeiten.official

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CacheFreshnessTest {

    @Test fun stampMatchesWithinOneKm() {
        // ~500 m nördlich von Nürnberg-Zentrum.
        assertTrue(stampMatches(49.4521, 11.0767, 49.4566, 11.0767))
    }

    @Test fun stampRejectsDifferentCity() {
        // Nürnberg vs. Fürth (~7 km).
        assertFalse(stampMatches(49.4521, 11.0767, 49.4759, 10.9886))
    }

    @Test fun stampRejectsMissing() {
        assertFalse(stampMatches(null, null, 49.4521, 11.0767))
    }

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
