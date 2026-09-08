package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.SourceResult
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class VerificationTextTest {

    private fun verification(
        note: VerificationNote,
        chosen: SourceId? = SourceId.DIRECT,
        confirmedBy: List<SourceId> = emptyList(),
        comparedDays: Int = 0,
        differingDays: Int = 0,
        maxAbsMinutes: Int = 0,
        firstDiff: LocalDate? = null,
        checkedEpochMs: Long = 1_786_000_000_000L,
    ) = Verification(
        note = note,
        chosen = chosen,
        confirmedBy = confirmedBy,
        comparedDays = comparedDays,
        differingDays = differingDays,
        maxAbsMinutes = maxAbsMinutes,
        firstDiff = firstDiff,
        checkedEpochMs = checkedEpochMs,
    )

    @Test fun `ohne Verification gibt es keine Zeile`() {
        assertNull(verificationLine(null))
    }

    @Test fun `NONE bleibt stumm - dafuer hat die bestehende Statuszeile eigene Worte`() {
        assertNull(verificationLine(verification(VerificationNote.NONE, chosen = null)))
    }

    @Test fun `VERIFIED nennt Anzahl der Quellen und verglichene Tage`() {
        val line = verificationLine(
            verification(
                VerificationNote.VERIFIED,
                confirmedBy = listOf(SourceId.PROXY_ABDUS, SourceId.EZANVAKTI),
                comparedDays = 31,
            ),
        )

        assertEquals("Gegenprüfung: bestätigt durch 2 Quellen (31 Tage verglichen)", line)
    }

    @Test fun `VERIFIED mit einer einzigen Quelle sagt 1 Quelle, nicht 1 Quellen`() {
        val line = verificationLine(
            verification(
                VerificationNote.VERIFIED,
                confirmedBy = listOf(SourceId.PROXY_ABDUS),
                comparedDays = 31,
            ),
        )

        assertEquals("Gegenprüfung: bestätigt durch 1 Quelle (31 Tage verglichen)", line)
    }

    @Test fun `VERIFIED mit einem einzigen Tag sagt 1 Tag, nicht 1 Tage`() {
        val line = verificationLine(
            verification(
                VerificationNote.VERIFIED,
                confirmedBy = listOf(SourceId.PROXY_ABDUS),
                comparedDays = 1,
            ),
        )

        assertEquals("Gegenprüfung: bestätigt durch 1 Quelle (1 Tag verglichen)", line)
    }

    @Test fun `DRIFT nennt abweichende Tage und die groesste Abweichung`() {
        val line = verificationLine(
            verification(
                VerificationNote.DRIFT,
                confirmedBy = listOf(SourceId.PROXY_ABDUS),
                comparedDays = 31,
                differingDays = 2,
                maxAbsMinutes = 1,
                firstDiff = LocalDate.of(2026, 9, 8),
            ),
        )

        assertEquals("Gegenprüfung: 2 von 31 Tagen weichen ab, max. 1 Min — mögliche Korrektur", line)
    }

    @Test fun `DRIFT mit einem abweichenden Tag sagt weicht ab, nicht weichen ab`() {
        val line = verificationLine(
            verification(
                VerificationNote.DRIFT,
                confirmedBy = listOf(SourceId.PROXY_ABDUS),
                comparedDays = 31,
                differingDays = 1,
                maxAbsMinutes = 1,
            ),
        )

        assertEquals("Gegenprüfung: 1 von 31 Tagen weicht ab, max. 1 Min — mögliche Korrektur", line)
    }

    @Test fun `DRIFT ueber einen einzigen verglichenen Tag sagt von 1 Tag`() {
        val line = verificationLine(
            verification(
                VerificationNote.DRIFT,
                confirmedBy = listOf(SourceId.PROXY_ABDUS),
                comparedDays = 1,
                differingDays = 1,
                maxAbsMinutes = 2,
            ),
        )

        assertEquals("Gegenprüfung: 1 von 1 Tag weicht ab, max. 2 Min — mögliche Korrektur", line)
    }

    @Test fun `CONFLICT_OVERRIDDEN benennt die kuerzere Abdeckung`() {
        val line = verificationLine(
            verification(
                VerificationNote.CONFLICT_OVERRIDDEN,
                chosen = SourceId.PROXY_ABDUS,
                confirmedBy = listOf(SourceId.PROXY_ABDUS, SourceId.EZANVAKTI),
                comparedDays = 31,
                differingDays = 31,
                maxAbsMinutes = 14,
            ),
        )

        assertEquals(
            "Gegenprüfung: Jahresabruf widersprach beiden Kontrollquellen — " +
                "geprüfter 31-Tage-Stand übernommen, Abdeckung daher kürzer",
            line,
        )
    }

    @Test fun `CONFLICT_UNRESOLVED nennt die groesste Abweichung`() {
        val line = verificationLine(
            verification(
                VerificationNote.CONFLICT_UNRESOLVED,
                comparedDays = 31,
                differingDays = 31,
                maxAbsMinutes = 14,
            ),
        )

        assertEquals("Gegenprüfung: Quellen uneinig (max. 14 Min) — Zeiten unbestätigt", line)
    }

    @Test fun `UNVERIFIED_SINGLE sagt, dass die Gegenpruefung nicht moeglich war`() {
        val line = verificationLine(verification(VerificationNote.UNVERIFIED_SINGLE))

        assertEquals("Gegenprüfung: nicht möglich — nur eine Quelle erreichbar", line)
    }

    // --- fetchErrorSummary

    @Test fun `ohne Fehler gibt es keine Fehlerzeile`() {
        val summary = fetchErrorSummary(
            listOf(
                SourceResult(SourceId.DIRECT, emptyMap()),
                SourceResult(SourceId.PROXY_ABDUS, emptyMap()),
            ),
        )

        assertNull(summary)
    }

    @Test fun `alle drei Fehler erscheinen in SourceId-Reihenfolge mit Klartextnamen`() {
        val summary = fetchErrorSummary(
            listOf(
                SourceResult(SourceId.EZANVAKTI, emptyMap(), error = "HTTP 404"),
                SourceResult(SourceId.DIRECT, emptyMap(), error = "HTTP 503"),
                SourceResult(SourceId.PROXY_ABDUS, emptyMap(), error = "Zeitüberschreitung"),
            ),
        )

        assertEquals(
            "Direktabruf: HTTP 503 · Proxy: Zeitüberschreitung · ezanvakti: HTTP 404",
            summary,
        )
    }

    @Test fun `nur die gescheiterten Kandidaten werden genannt`() {
        val summary = fetchErrorSummary(
            listOf(
                SourceResult(SourceId.DIRECT, emptyMap(), error = "HTTP 503"),
                SourceResult(SourceId.PROXY_ABDUS, mapOf(), error = null),
            ),
        )

        assertEquals("Direktabruf: HTTP 503", summary)
    }

    @Test fun `eine leere Kandidatenliste ergibt keine Fehlerzeile`() {
        assertNull(fetchErrorSummary(emptyList()))
    }
}
