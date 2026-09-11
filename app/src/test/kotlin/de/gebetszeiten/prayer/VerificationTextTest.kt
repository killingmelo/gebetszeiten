package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.SourceResult
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

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

    @Test fun `GAP_FILLED sagt, dass gefuellt wurde - und nennt keine Zahl, die etwas anderes meint`() {
        assertEquals(
            "Gegenprüfung: Jahresabruf deckt die aktuellen Tage nicht ab — " +
                "Zeiten dafür aus zwei übereinstimmenden Kontrollquellen (31 Tage verglichen)",
            verificationLine(verification(VerificationNote.GAP_FILLED, comparedDays = 31)),
        )
    }

    @Test fun `GAP_FILLED bei genau einem verglichenen Tag bleibt richtiges Deutsch`() {
        assertEquals(
            "Gegenprüfung: Jahresabruf deckt die aktuellen Tage nicht ab — " +
                "Zeiten dafür aus zwei übereinstimmenden Kontrollquellen (1 Tag verglichen)",
            verificationLine(verification(VerificationNote.GAP_FILLED, comparedDays = 1)),
        )
    }

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
                "geprüfter Kontrollstand übernommen, Abdeckung daher kürzer",
            line,
        )
    }

    @Test fun `CONFLICT_OVERRIDDEN nennt keine Tageszahl - comparedDays ist das Konfliktfenster`() {
        // `comparedDays` ist die Groesse der Schnittmenge, nicht die
        // Abdeckung des uebernommenen Stands: real 31 verglichene Tage bei
        // 51 ausgelieferten. Der Satz behauptet daher keine Zahl.
        val line = verificationLine(
            verification(
                VerificationNote.CONFLICT_OVERRIDDEN,
                chosen = SourceId.PROXY_ABDUS,
                confirmedBy = listOf(SourceId.PROXY_ABDUS, SourceId.EZANVAKTI),
                comparedDays = 1,
                differingDays = 1,
                maxAbsMinutes = 14,
            ),
        )

        assertEquals(
            "Gegenprüfung: Jahresabruf widersprach beiden Kontrollquellen — " +
                "geprüfter Kontrollstand übernommen, Abdeckung daher kürzer",
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

    @Test fun `CONFLICT_UNRESOLVED ohne gemeinsame Tage sagt nicht max 0 Min`() {
        // Zwei Pruefer mit disjunkten Datumsbereichen (Monatswechsel):
        // „uneinig (max. 0 Min)" liest sich als „sie sind sich einig" und
        // ist damit schlimmer als keine Meldung.
        val line = verificationLine(
            verification(
                VerificationNote.CONFLICT_UNRESOLVED,
                chosen = SourceId.PROXY_ABDUS,
                comparedDays = 0,
                differingDays = 0,
                maxAbsMinutes = 0,
            ),
        )

        assertEquals("Gegenprüfung: keine gemeinsamen Tage — Zeiten unbestätigt", line)
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

    @Test fun `wer geliefert hat, ist kein Fehlschlag - auch wenn unterwegs etwas schiefging`() {
        // Leerer Zeitplan + error = gescheitert (KDoc von SourceResult). Ein
        // Kandidat MIT Zeiten hat geliefert und womoeglich gewonnen; ihn als
        // Fehlschlag zu melden waere falsch.
        val summary = fetchErrorSummary(
            listOf(
                SourceResult(SourceId.DIRECT, someSchedule, error = "HTTP 503 auf Blatt 7"),
                SourceResult(SourceId.PROXY_ABDUS, emptyMap(), error = "Zeitüberschreitung"),
            ),
        )

        assertEquals("Proxy: Zeitüberschreitung", summary)
    }

    @Test fun `ein Teilerfolg allein ergibt keine Fehlerzeile`() {
        val summary = fetchErrorSummary(
            listOf(SourceResult(SourceId.DIRECT, someSchedule, error = "HTTP 503 auf Blatt 7")),
        )

        assertNull(summary)
    }

    @Test fun `hat eine Quelle geworfen, steht ihr Grund im Kopf - nicht das Literal`() {
        // Der Fehlertext eines leer ausgegangenen Abrufs. Ohne diesen Zweig
        // schriebe die App wieder "Keine amtlichen Zeiten erhalten", obwohl
        // sie den Grund kennt — und das Blatt sagte nur, DASS etwas
        // schiefging.
        assertEquals(
            "Direktabruf: HTTP 503 · Proxy: Zeitüberschreitung",
            emptyResultError("Direktabruf: HTTP 503 · Proxy: Zeitüberschreitung"),
        )
    }

    @Test fun `hat keine Quelle geworfen, bleibt das Literal richtig`() {
        // `null` heisst: keine Quelle ist gescheitert, es kam nur nichts an
        // (kein Diyanet-Standort aufloesbar, oder leere Antworten). Ein
        // leerer Text waere hier eine Statuszeile ohne Auskunft.
        assertEquals(
            "Keine amtlichen Zeiten erhalten (Standort oder Netz)",
            emptyResultError(null),
        )
    }

    private val someSchedule: Map<LocalDate, SixTimes> = mapOf(
        LocalDate.of(2026, 9, 6) to SixTimes(
            fajr = LocalTime.parse("04:54"),
            sunrise = LocalTime.parse("06:23"),
            dhuhr = LocalTime.parse("13:02"),
            asr = LocalTime.parse("16:39"),
            maghrib = LocalTime.parse("19:31"),
            isha = LocalTime.parse("20:53"),
        ),
    )
}
