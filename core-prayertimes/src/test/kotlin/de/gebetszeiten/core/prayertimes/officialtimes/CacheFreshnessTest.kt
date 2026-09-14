package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// stampMatches liegt schon laenger in core-prayertimes (CacheStampTest);
// needsRefresh (und chooseTarget, in ChooseTargetTest im app-Modul) ist seit
// Aufgabe 6 mit umgezogen — hier bleibt nur noch needsRefresh.
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
                LocalDate.of(2027, 7, 20), LocalDate.of(2026, 7, 6), stampOk = true,
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

    // --- Die Fehlschlag-Sperre schlaegt jetzt !stampOk, nicht umgekehrt ---
    //
    // Vor Task 3 war das Versuchsprotokoll GLOBAL: `lastAttempt` konnte von
    // einem anderen Ort stammen, und eine Sperre haette den gerade gewaehlten
    // Ort wegen eines fremden Fehlschlags blockiert. Deshalb musste !stampOk
    // gewinnen. Seit Task 3 gehoert das Protokoll zum Eintrag DIESES Ortes,
    // die Sperre ist also vertrauenswuerdig — und sie MUSS gewinnen, sobald
    // es mehrere Orte gibt: ein Favorit, an dem noch nie ein Abruf gelang,
    // wuerde `dueOrder` sonst bei jedem Ausloeser gewinnen, jedes Mal
    // scheitern und alle anderen Orte aushungern.

    @Test fun `ohne Zeiten und kurz nach Fehlschlag wird gebremst`() {
        assertFalse(
            needsRefresh(
                coveredUntil = null,
                today = LocalDate.of(2026, 7, 6),
                stampOk = false,
                lastAttemptEpochMs = now - fiveMinMs,
                lastAttemptFailed = true,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `ohne Zeiten nach Ablauf der Fehlschlag-Sperre wird abgerufen`() {
        assertTrue(
            needsRefresh(
                coveredUntil = null,
                today = LocalDate.of(2026, 7, 6),
                stampOk = false,
                lastAttemptEpochMs = now - (31 * 60 * 1000L),
                lastAttemptFailed = true,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `force schlaegt auch die Sperre ohne Zeiten`() {
        assertTrue(
            needsRefresh(
                coveredUntil = null,
                today = LocalDate.of(2026, 7, 6),
                stampOk = false,
                lastAttemptEpochMs = now - fiveMinMs,
                lastAttemptFailed = true,
                nowEpochMs = now,
                force = true,
            )
        )
    }

    @Test fun `ohne Zeiten und nie versucht wird sofort abgerufen`() {
        assertTrue(
            needsRefresh(
                coveredUntil = null,
                today = LocalDate.of(2026, 7, 6),
                stampOk = false,
                lastAttemptEpochMs = null,
                lastAttemptFailed = false,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `ohne Zeiten und kurz nach ERFOLG wird nicht gebremst`() {
        // Die Kurzerfolg-Sperre greift nur bei stampOk: hat der Ort keine
        // Zeiten, war der letzte "Erfolg" keiner fuer ihn.
        assertTrue(
            needsRefresh(
                coveredUntil = null,
                today = LocalDate.of(2026, 7, 6),
                stampOk = false,
                lastAttemptEpochMs = now - fiveMinMs,
                lastAttemptFailed = false,
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
                coveredUntil = LocalDate.of(2027, 7, 20), // reichlich Abdeckung (ein Jahr)
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

    // --- Die 60-Tage-Schwelle, von beiden Seiten ---
    //
    // Die Diyanet-Jahresseite ist ein rollierendes ~16-Monats-Fenster: ein
    // Abruf je Ort deckt ueber ein Jahr ab. Die hohe Schwelle kostet deshalb
    // praktisch nichts und kauft zwei Monate Puffer — die App darf zwei
    // Monate erfolglos bleiben, bevor der Nutzer es merkt.

    @Test fun `59 Tage Abdeckung sind zu wenig`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 6).plusDays(59),
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = null,
                lastAttemptFailed = false,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `Grenze exakt 60 Tage Abdeckung genuegt noch`() {
        assertFalse(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 6).plusDays(60),
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = null,
                lastAttemptFailed = false,
                nowEpochMs = now,
            )
        )
    }

    @Test fun `Grenze exakt retryAfterShortResultMs ist Sperre schon abgelaufen`() {
        assertTrue(
            needsRefresh(
                coveredUntil = LocalDate.of(2026, 7, 9), // duenn
                today = LocalDate.of(2026, 7, 6),
                stampOk = true,
                lastAttemptEpochMs = now - (12 * 60 * 60 * 1000L),
                lastAttemptFailed = false,
                nowEpochMs = now,
            )
        )
    }
}
