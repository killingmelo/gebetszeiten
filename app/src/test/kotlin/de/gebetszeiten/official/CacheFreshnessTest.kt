package de.gebetszeiten.official

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// stampMatches liegt jetzt in core-prayertimes (CacheStampTest) — hier bleibt
// nur noch needsRefresh.
class CacheFreshnessTest {

    // Fester "jetzt"-Zeitpunkt fuer alle Retry-Bremsen-Tests (Millis).
    private val now = 1_786_000_000_000L
    private val fiveMinMs = 5 * 60 * 1000L
    private val fortyFiveMinMs = 45 * 60 * 1000L
    private val oneHourMs = 60 * 60 * 1000L
    private val thirteenHoursMs = 13 * 60 * 60 * 1000L

    @Test fun refreshWhenStampMismatch() {
        assertTrue(
            needsRefresh(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 6), stampOk = false,
                lastAttemptEpochMs = null, lastAttemptFailed = false, nowEpochMs = now,
            )
        )
    }

    @Test fun refreshWhenCoverageThin() {
        // Nur noch 3 Tage Zukunft abgedeckt.
        assertTrue(
            needsRefresh(
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 6), stampOk = true,
                lastAttemptEpochMs = null, lastAttemptFailed = false, nowEpochMs = now,
            )
        )
    }

    @Test fun noRefreshWhenFreshAndMatching() {
        assertFalse(
            needsRefresh(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 6), stampOk = true,
                lastAttemptEpochMs = null, lastAttemptFailed = false, nowEpochMs = now,
            )
        )
    }

    @Test fun refreshWhenEmpty() {
        assertTrue(
            needsRefresh(
                null, LocalDate.of(2026, 7, 6), stampOk = true,
                lastAttemptEpochMs = null, lastAttemptFailed = false, nowEpochMs = now,
            )
        )
    }

    // --- Task 1: Wiederholungs-Bremse ---

    @Test fun `gebremst kurz nach Fehlschlag`() {
        assertFalse(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9), // duenn
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = now - fiveMinMs,
                lastAttemptFailed = true,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `Erholung nach Ablauf der Fehlschlag-Sperre`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9), // duenn
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = now - fortyFiveMinMs,
                lastAttemptFailed = true,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `gebremst kurz nach Erfolg mit duenner Abdeckung`() {
        assertFalse(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9), // duenn
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = now - oneHourMs,
                lastAttemptFailed = false,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `Erholung nach Ablauf der Kurzerfolg-Sperre`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9), // duenn
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = now - thirteenHoursMs,
                lastAttemptFailed = false,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `Ortswechsel schlaegt die Fehlschlag-Sperre`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9),
                today = LocalDate.of(2026, 7, 6),
                stampOk = false,
                lastAttemptEpochMs = now - fiveMinMs,
                lastAttemptFailed = true,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `force schlaegt die Fehlschlag-Sperre`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9),
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = now - fiveMinMs,
                lastAttemptFailed = true,
                nowEpochMs = now,
                force = true,
            )
        )
    }

    @Test fun `nie zuvor versucht blockiert nie`() {
        assertFalse(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 20), // reichlich Abdeckung
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = null,
                lastAttemptFailed = true, // sollte bei null ohnehin nie greifen
                nowEpochMs = now,
            )
        )
    }

    @Test fun `nie zuvor versucht loest trotzdem Abruf aus wenn Abdeckung duenn ist`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9), // duenn
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = null,
                lastAttemptFailed = true,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `Grenze exakt retryAfterFailureMs ist Sperre schon abgelaufen`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9), // duenn
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = now - (30 * 60 * 1000L),
                lastAttemptFailed = true,
                nowEpochMs = now,
            )
        )
    }
}
