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

    /** Ein Zeitplan, der an [conflictDays] Tagen ab [from] um 14 Minuten
     *  abweicht — weit ueber MINOR_DRIFT, der Vergleich ist also ein
     *  CONFLICT. Damit laesst sich das VERHAELTNIS abweichender zu
     *  verglichenen Tagen einstellen. */
    private fun planWithConflict(
        days: Int,
        conflictDays: Int,
        from: LocalDate = day0,
    ): Map<LocalDate, SixTimes> =
        plan(days, from) + (0 until conflictDays).associate {
            from.plusDays(it.toLong()) to six(isha = "21:07")
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

    // --- Ueberstimmen NUR bei systematischem Widerspruch

    @Test fun `A3d ein einziger abweichender Tag ueberstimmt den Jahresabruf NICHT`() {
        // Der Fall, den der Nutzer entschieden hat: EIN Tag um drei Minuten
        // ist die Form einer Diyanet-Korrektur, nicht die eines kaputten
        // Parsers. Wuerde er ueberstimmen, verloere der Ort ~370 Tage
        // Abdeckung — ausserhalb Deutschlands ohne gebuendelte Reserve.
        val korrigiert = plan(days = 31) +
            mapOf(day0.plusDays(10) to six(isha = "20:56")) // 3 Min -> CONFLICT

        val outcome = resolve(direct(plan(days = 400)), proxy(korrigiert), ezan(korrigiert))

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(31, outcome.verification.comparedDays)
        assertEquals(1, outcome.verification.differingDays)
        // Der Direktabruf behaelt sein VOLLES Fenster.
        assertEquals(400, outcome.schedule.size)
    }

    @Test fun `A3e ein systematischer Widerspruch ueberstimmt weiterhin`() {
        // Dieselben 31 Tage wie im Nutzerfall, aber ALLE weichen ab — das ist
        // die Form „der Parser liegt daneben".
        val abweichend = plan(days = 31, times = six(isha = "20:56"))

        val outcome = resolve(direct(plan(days = 400)), proxy(abweichend), ezan(abweichend))

        assertEquals(VerificationNote.CONFLICT_OVERRIDDEN, outcome.verification.note)
        assertEquals(SourceId.PROXY_ABDUS, outcome.verification.chosen)
        assertEquals(31, outcome.schedule.size)
    }

    @Test fun `A3f ein zu kleines Prueffenster kippt kein Jahr`() {
        // Drei gemeinsame Tage sagen nichts ueber einen Jahresplan aus, auch
        // wenn alle drei abweichen.
        val abweichend = plan(days = 3, times = six(isha = "21:07"))

        val outcome = resolve(direct(plan(days = 400)), proxy(abweichend), ezan(abweichend))

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertEquals(400, outcome.schedule.size)
    }

    @Test fun `A3g genau minConflictDays verglichene Tage genuegen noch`() {
        // Die Fenster-Grenze, festgenagelt: sieben Tage sind gerade noch
        // aussagekraeftig. Mutation `>=` -> `>` in `isSystematic` kippt das
        // hier auf CONFLICT_UNRESOLVED.
        val abweichend = plan(days = 7, times = six(isha = "21:07"))

        val outcome = resolve(direct(plan(days = 400)), proxy(abweichend), ezan(abweichend))

        assertEquals(VerificationNote.CONFLICT_OVERRIDDEN, outcome.verification.note)
        assertEquals(SourceId.PROXY_ABDUS, outcome.verification.chosen)
    }

    @Test fun `A3h ein Tag unter minConflictDays genuegt nicht mehr`() {
        val abweichend = plan(days = 6, times = six(isha = "21:07"))

        val outcome = resolve(direct(plan(days = 400)), proxy(abweichend), ezan(abweichend))

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
    }

    @Test fun `A3i genau die Haelfte abweichender Tage genuegt noch`() {
        // Die Haelfte-Grenze, festgenagelt: 4 von 8. Mutation
        // `differingDays * 2 >= comparedDays` -> `>` kippt das hier.
        val abweichend = planWithConflict(days = 8, conflictDays = 4)

        val outcome = resolve(direct(plan(days = 400)), proxy(abweichend), ezan(abweichend))

        assertEquals(VerificationNote.CONFLICT_OVERRIDDEN, outcome.verification.note)
        assertEquals(8, outcome.verification.comparedDays)
        assertEquals(4, outcome.verification.differingDays)
    }

    @Test fun `A3j knapp unter der Haelfte genuegt nicht mehr`() {
        // 3 von 8 — noch immer ein CONFLICT (14 Min), aber nicht mehr die
        // Form eines kaputten Parsers.
        val abweichend = planWithConflict(days = 8, conflictDays = 3)

        val outcome = resolve(direct(plan(days = 400)), proxy(abweichend), ezan(abweichend))

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertEquals(400, outcome.schedule.size)
    }

    @Test fun `A3k ein systematischer Pruefer allein ueberstimmt nicht`() {
        // Beide Pruefer sind untereinander EINIG und widersprechen dem
        // Direktabruf — aber nur der Proxy tut es systematisch: der
        // Direktabruf liegt nur in den ersten zehn Tagen daneben. Der
        // ezanvakti-Vergleich sieht 22 Tage und nur 10 abweichende.
        val jahr = plan(days = 10, from = day0, times = six(isha = "21:07")) +
            plan(days = 390, from = day0.plusDays(10))

        val outcome = resolve(
            direct(jahr),
            proxy(plan(days = 10, from = day0)),
            ezan(plan(days = 22, from = day0)),
        )

        assertEquals(VerificationNote.CONFLICT_UNRESOLVED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertTrue(outcome.verification.confirmedBy.isEmpty())
        assertEquals(400, outcome.schedule.size)
    }

    @Test fun `eine eigene minConflictDays-Schwelle wirkt bis in das Quorum durch`() {
        val abweichend = plan(days = 3, times = six(isha = "21:07"))
        val candidates = listOf(direct(plan(days = 400)), proxy(abweichend), ezan(abweichend))

        assertEquals(
            VerificationNote.CONFLICT_UNRESOLVED,
            resolveQuorum(candidates, locationId, now).note(),
        )
        assertEquals(
            VerificationNote.CONFLICT_OVERRIDDEN,
            resolveQuorum(candidates, locationId, now, minConflictDays = 3).note(),
        )
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

    @Test fun `bei doppeltem SourceId gewinnt der erste Eintrag, der geliefert hat`() {
        // Unter zwei Eintraegen mit Zeitplan gewinnt der erste — nicht der mit
        // mehr Tagen. Der zweite verschwindet vollstaendig.
        val outcome = resolve(proxy(plan(days = 31)), proxy(plan(days = 35)))

        assertEquals(VerificationNote.UNVERIFIED_SINGLE, outcome.verification.note)
        assertEquals(31, outcome.schedule.size)
    }

    @Test fun `ein leerer Fehlereintrag verdeckt keinen erfolgreichen Abruf derselben Quelle`() {
        // Der leere Eintrag steht VORNE. Wuerde schlicht der erste gewinnen,
        // gaelte der Direktabruf als gescheitert, obwohl er geliefert hat —
        // die App wuerde einen ganzen Jahresplan wegwerfen und den 31-Tage-
        // Stand nehmen, ohne dass ein Mensch es je erfaehrt.
        val outcome = resolve(
            SourceResult(SourceId.DIRECT, emptyMap(), error = "HTTP 503"),
            SourceResult(SourceId.DIRECT, plan(days = 400)),
            proxy(plan(days = 31)),
        )

        assertEquals(VerificationNote.VERIFIED, outcome.verification.note)
        assertEquals(SourceId.DIRECT, outcome.verification.chosen)
        assertEquals("der Jahresplan darf nicht verlorengehen", 400, outcome.schedule.size)
    }

    @Test fun `minConflictDays und maxDriftDays haengen zusammen - wer eines anhebt, muss das andere nachziehen`() {
        // Die beiden Vorgabewerte stehen in verschiedenen Funktionen und
        // koennen im Code nicht gekoppelt werden. Dieser Test ist die
        // Kopplung: der Vorgabewert von minConflictDays muss so gross sein,
        // dass die HAELFTE davon mehr Tage verlangt, als maxDriftDays
        // durchgehen laesst.
        //
        // Sonst entsteht eine Lage, die je nach Fenstergroesse einmal
        // "moegliche Korrektur" und einmal "kaputter Parser" heisst: bei
        // minConflictDays = 6 waeren "3 von 6 abweichend" systematisch,
        // obwohl drei abweichende Tage der Zahl nach noch innerhalb der
        // Drift-Toleranz liegen.
        val minConflictDays = defaultMinConflictDays()
        val maxDriftDays = defaultMaxDriftDays()

        // ceil(minConflictDays / 2) — die kleinste Zahl abweichender Tage,
        // die im kleinsten erlaubten Fenster als systematisch durchgeht.
        val kleinsteSystematischeAbweichung = (minConflictDays + 1) / 2
        assertTrue(
            "minConflictDays=$minConflictDays ist zu klein fuer maxDriftDays=$maxDriftDays: " +
                "$kleinsteSystematischeAbweichung abweichende Tage gaelten als systematisch, " +
                "liegen aber noch in der Drift-Toleranz. Erwartet: minConflictDays >= ${2 * maxDriftDays + 1}",
            kleinsteSystematischeAbweichung > maxDriftDays,
        )
    }

    /** Liest den Vorgabewert, statt die 7 hier zu wiederholen — sonst pruefte
     *  der Test seine eigene Kopie und nicht den echten Wert. */
    private fun defaultMinConflictDays(): Int {
        // Binaere Suche waere Uebertreibung: die Grenze wird direkt gemessen.
        // Bei genau `n` verglichenen, alle abweichenden Tagen ueberstimmt das
        // Quorum genau dann, wenn n >= minConflictDays.
        for (n in 1..64) {
            val outcome = resolve(
                direct(plan(days = 400, times = six(isha = "21:07"))),
                proxy(plan(days = n)),
                ezan(plan(days = n)),
            )
            if (outcome.verification.note == VerificationNote.CONFLICT_OVERRIDDEN) return n
        }
        error("kein Fenster bis 64 Tage ueberstimmt — minConflictDays unplausibel gross")
    }

    /** Analog gemessen: bei genau `n` abweichenden Tagen in einem grossen
     *  Fenster kippt MINOR_DRIFT nach CONFLICT, sobald n > maxDriftDays. */
    private fun defaultMaxDriftDays(): Int {
        for (n in 1..64) {
            val outcome = resolve(
                direct(plan(days = 31)),
                proxy(planWithDrift(days = 31, driftFrom = day0, driftDays = n)),
            )
            if (outcome.verification.note != VerificationNote.DRIFT) return n - 1
        }
        error("keine Drift-Grenze bis 64 Tage gefunden — maxDriftDays unplausibel gross")
    }

    private fun QuorumOutcome.note() = verification.note
}
