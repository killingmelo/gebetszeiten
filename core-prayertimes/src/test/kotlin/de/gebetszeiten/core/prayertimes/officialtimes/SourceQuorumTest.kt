package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SourceQuorumTest {

    private val day0 = LocalDate.of(2026, 9, 6)
    private val now = 1_786_000_000_000L // fester Zeitpunkt, keine Systemuhr
    private val locationId = 4711

    private fun six(
        fajr: String = "04:54",
        sunrise: String = "06:23",
        dhuhr: String = "13:02",
        asr: String = "16:39",
        maghrib: String = "19:31",
        isha: String = "20:53",
    ) = SixTimes(
        fajr = LocalTime.parse(fajr),
        sunrise = LocalTime.parse(sunrise),
        dhuhr = LocalTime.parse(dhuhr),
        asr = LocalTime.parse(asr),
        maghrib = LocalTime.parse(maghrib),
        isha = LocalTime.parse(isha),
    )

    private fun plan(
        days: Int,
        from: LocalDate = day0,
        times: SixTimes = six(),
    ): Map<LocalDate, SixTimes> =
        (0 until days).associate { from.plusDays(it.toLong()) to times }

    /** Ein Zeitplan, der an [driftDays] Tagen ab [driftFrom] um eine Minute abweicht. */
    private fun planWithDrift(
        days: Int,
        driftFrom: LocalDate,
        driftDays: Int,
    ): Map<LocalDate, SixTimes> =
        plan(days) + (0 until driftDays).associate {
            driftFrom.plusDays(it.toLong()) to six(asr = "16:40")
        }

    private fun direct(schedule: Map<LocalDate, SixTimes>) = SourceResult(SourceId.DIRECT, schedule)
    private fun proxy(schedule: Map<LocalDate, SixTimes>) = SourceResult(SourceId.PROXY_ABDUS, schedule)
    private fun ezan(schedule: Map<LocalDate, SixTimes>) = SourceResult(SourceId.EZANVAKTI, schedule)

    private fun resolve(vararg candidates: SourceResult) =
        resolveQuorum(candidates.toList(), locationId = locationId, nowEpochMs = now)

    // --- Fall A: der Direktabruf hat geliefert

    @Test fun `A1 ein zustimmender Pruefer bestaetigt den Direktabruf - VERIFIED mit vollem Fenster`() {
        val outcome = resolve(direct(plan(days = 400)), proxy(plan(days = 31)))

        assertEquals(VerificationNote.VERIFIED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertEquals(listOf(SourceId.PROXY_ABDUS), outcome.verification.confirmedBy)
        assertEquals(31, outcome.verification.comparedDays)
        assertEquals(0, outcome.verification.differingDays)
        assertEquals(0, outcome.verification.maxAbsMinutes)
        assertNull(outcome.verification.firstDiff)
        // Volles Fenster: der Direktabruf bleibt, nicht auf 31 Tage gekuerzt.
        assertEquals(400, outcome.schedule.size)
        assertEquals(locationId, outcome.locationId)
    }

    @Test fun `A2 nur Drift statt Zustimmung - DRIFT, der Direktabruf bleibt`() {
        val outcome = resolve(
            direct(plan(days = 31)),
            proxy(planWithDrift(days = 31, driftFrom = day0.plusDays(1), driftDays = 1)),
        )

        assertEquals(VerificationNote.DRIFT, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertEquals(listOf(SourceId.PROXY_ABDUS), outcome.verification.confirmedBy)
        assertEquals(1, outcome.verification.differingDays)
        assertEquals(1, outcome.verification.maxAbsMinutes)
        assertEquals(day0.plusDays(1), outcome.verification.firstDiff)
    }

    @Test fun `A3 zwei einige Pruefer schlagen den widersprechenden Jahresabruf - CONFLICT_OVERRIDDEN`() {
        val outcome = resolve(
            direct(plan(days = 400, times = six(isha = "21:07"))),
            proxy(plan(days = 31)),
            ezan(plan(days = 35)),
        )

        assertEquals(VerificationNote.CONFLICT_OVERRIDDEN, outcome.verification.note)
        // Mehr Tage gewinnt.
        assertEquals(SourceId.EZANVAKTI, outcome.verification.chosen)
        assertEquals(listOf(SourceId.PROXY_ABDUS, SourceId.EZANVAKTI), outcome.verification.confirmedBy)
        assertEquals(14, outcome.verification.maxAbsMinutes)
        // Der widersprechende Jahresabruf ergaenzt NICHTS: die Abdeckung ist
        // die der Pruefer, nicht 400 Tage.
        assertEquals(35, outcome.schedule.size)
        assertEquals(locationId, outcome.locationId)
    }

    @Test fun `A3b bei Gleichstand der Tage gewinnt der kleinere SourceId-ordinal`() {
        val outcome = resolve(
            direct(plan(days = 400, times = six(isha = "21:07"))),
            proxy(plan(days = 31)),
            ezan(plan(days = 31)),
        )

        assertEquals(VerificationNote.CONFLICT_OVERRIDDEN, outcome.verification.note)
        assertEquals(SourceId.PROXY_ABDUS, outcome.verification.chosen)
    }

    @Test fun `A4 ein einzelner widersprechender Pruefer entscheidet nichts - CONFLICT_UNRESOLVED`() {
        val outcome = resolve(
            direct(plan(days = 31)),
            proxy(plan(days = 31, times = six(isha = "21:07"))),
        )

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(14, outcome.verification.maxAbsMinutes)
        assertEquals(locationId, outcome.locationId)
    }

    @Test fun `A4b zwei Pruefer, die auch untereinander uneinig sind - CONFLICT_UNRESOLVED`() {
        val outcome = resolve(
            direct(plan(days = 31)),
            proxy(plan(days = 31, times = six(isha = "20:56"))),
            ezan(plan(days = 31, times = six(isha = "21:07"))),
        )

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
    }

    @Test fun `A3c die Zahlen bei CONFLICT_OVERRIDDEN stammen vom SCHLIMMSTEN Vergleich`() {
        // Ungleiche Abstaende, damit der Test „schlimmster" von „mildester"
        // unterscheidet: der Jahresabruf liegt in den ersten 31 Tagen 7 Min
        // daneben, danach 14 Min. Der Proxy sieht nur die ersten 31 Tage
        // (7 Min), ezanvakti reicht in den zweiten Abschnitt (14 Min).
        // Untereinander sind die beiden Pruefer auf ihren elf gemeinsamen
        // Tagen einig.
        val outcome = resolve(
            direct(
                plan(days = 31, from = day0, times = six(isha = "21:00")) +
                    plan(days = 369, from = day0.plusDays(31), times = six(isha = "21:07")),
            ),
            proxy(plan(days = 31, from = day0)),
            ezan(plan(days = 31, from = day0.plusDays(20))),
        )

        assertEquals(VerificationNote.CONFLICT_OVERRIDDEN, outcome.verification.note)
        // Nicht 7 Min: die Anzeige soll den schlimmsten Widerspruch nennen.
        assertEquals(14, outcome.verification.maxAbsMinutes)
        // Aus DEMSELBEN Vergleich: der ezanvakti-Vergleich beginnt an
        // day0+20, der mildere Proxy-Vergleich an day0.
        assertEquals(day0.plusDays(20), outcome.verification.firstDiff)
    }

    @Test fun `A4d zwei widersprechende Pruefer ohne gemeinsame Tage schlagen den Jahresabruf NICHT`() {
        // Der teuerste Fehlgriff dieser Datei: zwei Quellen, die einander
        // nie beruehrt haben, gelten nicht als gegenseitige Bestaetigung
        // und duerfen den Jahresabruf nicht verwerfen.
        val outcome = resolve(
            direct(plan(days = 400, times = six(isha = "21:07"))),
            proxy(plan(days = 31, from = day0)),
            ezan(plan(days = 31, from = day0.plusDays(60))),
        )

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        // Der Jahresabruf bleibt in voller Laenge stehen.
        assertEquals(400, outcome.schedule.size)
    }

    @Test fun `A4c die Zahlen bei CONFLICT_UNRESOLVED stammen vom SCHLIMMSTEN Vergleich`() {
        val outcome = resolve(
            direct(plan(days = 31)),
            proxy(plan(days = 31, times = six(isha = "20:56"))), // 3 Min
            ezan(plan(days = 31, times = six(isha = "21:07"))), // 14 Min
        )

        // Nicht 3 Min: die Anzeige soll den schlimmsten Widerspruch nennen.
        assertEquals(14, outcome.verification.maxAbsMinutes)
    }

    @Test fun `A5 gar kein Pruefer - UNVERIFIED_SINGLE`() {
        val outcome = resolve(direct(plan(days = 400)))

        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(0, outcome.verification.comparedDays)
        assertEquals(0, outcome.verification.differingDays)
        assertEquals(0, outcome.verification.maxAbsMinutes)
        assertNull(outcome.verification.firstDiff)
        assertEquals(400, outcome.schedule.size)
        assertEquals(locationId, outcome.locationId)
    }

    @Test fun `A5b ein Pruefer ohne Schnittmenge ist kein beurteilbarer Pruefer - UNVERIFIED_SINGLE`() {
        val outcome = resolve(
            direct(plan(days = 5)),
            proxy(plan(days = 5, from = day0.plusDays(10))),
        )

        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        // Charakterisierung: aus einer nicht beurteilbaren Quelle wird auch
        // NICHT ergaenzt — sonst mischte die App ungepruefte Zeiten hinein.
        assertEquals(5, outcome.schedule.size)
    }

    @Test fun `A6 ein gescheiterter Direktabruf mit leerem Zeitplan gilt als nicht geliefert`() {
        val outcome = resolve(
            SourceResult(SourceId.DIRECT, emptyMap(), error = "HTTP 503"),
            proxy(plan(days = 31)),
        )

        // Fall B: nur ein Pruefer.
        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(SourceId.PROXY_ABDUS, outcome.verification.chosen)
    }

    // --- Fall B: der Direktabruf hat nicht geliefert

    @Test fun `B1 zwei einige Pruefer - VERIFIED, der mit mehr Tagen gewinnt`() {
        val outcome = resolve(proxy(plan(days = 31)), ezan(plan(days = 35)))

        assertEquals(VerificationNote.VERIFIED, outcome.verification.note)
        assertEquals(SourceId.EZANVAKTI, outcome.verification.chosen)
        assertEquals(listOf(SourceId.PROXY_ABDUS), outcome.verification.confirmedBy)
        assertEquals(31, outcome.verification.comparedDays)
        assertEquals(35, outcome.schedule.size)
        assertEquals(locationId, outcome.locationId)
    }

    @Test fun `B1b zwei Pruefer mit kleiner Abweichung - DRIFT, nicht VERIFIED`() {
        // Die Drift steht in der Verification; „bestaetigt" zu melden waere
        // eine Ueberbehauptung, obwohl die Quellen nachweislich abweichen.
        val outcome = resolve(
            proxy(plan(days = 31)),
            ezan(planWithDrift(days = 35, driftFrom = day0.plusDays(2), driftDays = 2)),
        )

        assertEquals(VerificationNote.DRIFT, outcome.verification.note)
        assertEquals(SourceId.EZANVAKTI, outcome.verification.chosen)
        assertEquals(listOf(SourceId.PROXY_ABDUS), outcome.verification.confirmedBy)
        assertEquals(31, outcome.verification.comparedDays)
        assertEquals(2, outcome.verification.differingDays)
        assertEquals(1, outcome.verification.maxAbsMinutes)
        assertEquals(day0.plusDays(2), outcome.verification.firstDiff)
    }

    @Test fun `B2b zwei Pruefer ohne gemeinsame Tage bestaetigen einander nicht`() {
        // Realfall Monatswechsel: der Proxy haengt noch im alten Monat.
        // Ohne Schnittmenge belegt keine Quelle die andere.
        val outcome = resolve(
            proxy(plan(days = 31, from = day0)),
            ezan(plan(days = 31, from = day0.plusDays(60))),
        )

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.PROXY_ABDUS, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(0, outcome.verification.comparedDays)
        assertEquals(0, outcome.verification.maxAbsMinutes)
        // Aus der nicht beurteilbaren Quelle wird nichts ergaenzt.
        assertEquals(31, outcome.schedule.size)
    }

    @Test fun `B2 zwei uneinige Pruefer - CONFLICT_UNRESOLVED, der mit mehr Tagen gewinnt`() {
        val outcome = resolve(
            proxy(plan(days = 31)),
            ezan(plan(days = 35, times = six(isha = "21:07"))),
        )

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.EZANVAKTI, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(14, outcome.verification.maxAbsMinutes)
        // Aus der widersprechenden Quelle wird nichts ergaenzt.
        assertEquals(35, outcome.schedule.size)
    }

    @Test fun `B3 genau ein Pruefer - UNVERIFIED_SINGLE`() {
        val outcome = resolve(proxy(plan(days = 31)))

        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(SourceId.PROXY_ABDUS, outcome.verification.chosen)
        assertEquals(0, outcome.verification.comparedDays)
        assertEquals(31, outcome.schedule.size)
        assertEquals(locationId, outcome.locationId)
    }

    @Test fun `B4 keine Quelle liefert - NONE mit leerem Zeitplan und locationId null`() {
        val outcome = resolveQuorum(emptyList(), locationId = locationId, nowEpochMs = now)

        assertEquals(VerificationNote.NONE, outcome.verification.note)
        assertNull(outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(0, outcome.verification.comparedDays)
        assertEquals(0, outcome.verification.differingDays)
        assertEquals(0, outcome.verification.maxAbsMinutes)
        assertNull(outcome.verification.firstDiff)
        assertTrue(outcome.schedule.isEmpty())
        assertNull(outcome.locationId)
        assertEquals(now, outcome.verification.checkedEpochMs)
    }

    @Test fun `B4b nur gescheiterte Abrufe - ebenfalls NONE`() {
        val outcome = resolve(
            SourceResult(SourceId.DIRECT, emptyMap(), error = "HTTP 503"),
            SourceResult(SourceId.PROXY_ABDUS, emptyMap(), error = "Zeitüberschreitung"),
        )

        assertEquals(VerificationNote.NONE, outcome.verification.note)
        assertNull(outcome.locationId)
    }

    // --- Vereinigung der Tage

    @Test fun `die Vereinigung ergaenzt fehlende Tage aus einer zustimmenden Quelle, der Gewinner gewinnt`() {
        // Gewinner: 7 Tage ab day0+2. Spender: 5 Tage ab day0, an den drei
        // gemeinsamen Tagen um eine Minute abweichend (MINOR_DRIFT).
        val winner = plan(days = 7, from = day0.plusDays(2))
        val donor = plan(days = 5, from = day0, times = six(asr = "16:40"))

        val outcome = resolve(proxy(donor), ezan(winner))

        assertEquals(SourceId.EZANVAKTI, outcome.verification.chosen)
        // 0..8, also neun Tage: zwei ergaenzte plus die sieben des Gewinners.
        assertEquals(9, outcome.schedule.size)
        // Ergaenzter Tag: die Zeiten des Spenders.
        assertEquals(LocalTime.parse("16:40"), outcome.schedule[day0]?.asr)
        // Gemeinsamer Tag: die Zeiten des GEWINNERS.
        assertEquals(LocalTime.parse("16:39"), outcome.schedule[day0.plusDays(2)]?.asr)
    }

    @Test fun `aus einer widersprechenden Quelle wird NICHTS ergaenzt`() {
        val outcome = resolve(
            direct(plan(days = 5)),
            proxy(plan(days = 10, times = six(isha = "21:07"))),
        )

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(5, outcome.schedule.size)
        assertNull(outcome.schedule[day0.plusDays(7)])
    }

    // --- Welche Zahlen in Verification stehen

    @Test fun `bei VERIFIED zaehlt der zustimmende Vergleich mit den MEISTEN Tagen`() {
        val outcome = resolve(
            direct(plan(days = 400)),
            proxy(plan(days = 31)),
            ezan(plan(days = 10)),
        )

        assertEquals(VerificationNote.VERIFIED, outcome.verification.note)
        assertEquals(31, outcome.verification.comparedDays)
        assertEquals(listOf(SourceId.PROXY_ABDUS, SourceId.EZANVAKTI), outcome.verification.confirmedBy)
    }

    @Test fun `bei DRIFT zaehlt der Vergleich mit den MEISTEN abweichenden Tagen`() {
        val outcome = resolve(
            direct(plan(days = 31)),
            proxy(planWithDrift(days = 31, driftFrom = day0.plusDays(1), driftDays = 1)),
            ezan(planWithDrift(days = 31, driftFrom = day0.plusDays(5), driftDays = 3)),
        )

        assertEquals(VerificationNote.DRIFT, outcome.verification.note)
        assertEquals(3, outcome.verification.differingDays)
        // Aus DEMSELBEN Vergleich, nicht gemischt: der fruehste abweichende
        // Tag des ezanvakti-Vergleichs, nicht der des Proxy-Vergleichs.
        assertEquals(day0.plusDays(5), outcome.verification.firstDiff)
        assertEquals(listOf(SourceId.PROXY_ABDUS, SourceId.EZANVAKTI), outcome.verification.confirmedBy)
    }

    @Test fun `checkedEpochMs wird durchgereicht, nicht aus der Systemuhr geholt`() {
        val stamp = 1_234_567_890L
        val outcome = resolveQuorum(
            listOf(direct(plan(days = 31)), proxy(plan(days = 31))),
            locationId = locationId,
            nowEpochMs = stamp,
        )

        assertEquals(stamp, outcome.verification.checkedEpochMs)
    }

    @Test fun `eigene Schwellen wirken bis in das Quorum durch`() {
        val candidates = listOf(
            direct(plan(days = 31)),
            proxy(planWithDrift(days = 31, driftFrom = day0, driftDays = 1)),
        )

        assertEquals(
            VerificationNote.DRIFT,
            resolveQuorum(candidates, locationId, now, maxDriftDays = 3, maxDriftMinutes = 2).note(),
        )
        assertEquals(
            VerificationNote.CONFLICT_UNRESOLVED,
            resolveQuorum(candidates, locationId, now, maxDriftDays = 0, maxDriftMinutes = 0).note(),
        )
    }

    // --- Deduplizierung: ein SourceId zaehlt genau einmal

    @Test fun `ein doppelter Direktabruf bestaetigt sich nicht selbst`() {
        // Task 11 setzt die Kandidatenliste nebenlaeufig zusammen. Ein
        // zweimal eingetragener SourceId darf nicht zum eigenen Pruefer
        // werden — sonst meldete die Zeile „bestaetigt durch 1 Quelle",
        // obwohl nur eine Quelle geantwortet hat.
        val schedule = plan(days = 31)

        val outcome = resolve(direct(schedule), direct(schedule))

        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(0, outcome.verification.comparedDays)
    }

    @Test fun `ein doppelter Pruefer bestaetigt sich nicht selbst`() {
        val schedule = plan(days = 31)

        val outcome = resolve(proxy(schedule), proxy(schedule))

        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(SourceId.PROXY_ABDUS, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
    }

    @Test fun `bei doppeltem SourceId gewinnt der erste Eintrag`() {
        // Der zweite PROXY_ABDUS-Eintrag verschwindet vollstaendig, mit
        // seinen 35 Tagen — nicht der mit mehr Tagen gewinnt, sondern der
        // erste.
        val outcome = resolve(proxy(plan(days = 31)), proxy(plan(days = 35)))

        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(31, outcome.schedule.size)
    }

    private fun QuorumOutcome.note() = verification.note
}
