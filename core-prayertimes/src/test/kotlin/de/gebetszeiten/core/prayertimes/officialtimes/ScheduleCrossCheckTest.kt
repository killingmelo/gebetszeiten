package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleCrossCheckTest {

    private val day0 = LocalDate.of(2026, 9, 6)

    /** Ein plausibler Nuernberger Tag; jedes Feld einzeln ueberschreibbar,
     *  damit die Sechs-Felder-Faelle je genau eine Zeit verruecken. */
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

    /** [days] aufeinanderfolgende Tage ab [from], alle mit [times]. */
    private fun plan(
        days: Int,
        from: LocalDate = day0,
        times: SixTimes = six(),
    ): Map<LocalDate, SixTimes> =
        (0 until days).associate { from.plusDays(it.toLong()) to times }

    @Test fun `leere Schnittmenge ergibt NO_OVERLAP und keine Zahlen`() {
        val a = plan(days = 5, from = day0)
        val b = plan(days = 5, from = day0.plusDays(10))

        val result = crossCheck(a, b)

        assertEquals(Verdict.NO_OVERLAP, result.verdict)
        assertEquals(0, result.comparedDays)
        assertEquals(0, result.differingDays)
        assertEquals(0, result.maxAbsMinutes)
        assertNull(result.firstDiff)
    }

    @Test fun `ein leerer Zeitplan ergibt NO_OVERLAP`() {
        val result = crossCheck(plan(days = 5), emptyMap())

        assertEquals(Verdict.NO_OVERLAP, result.verdict)
        assertEquals(0, result.comparedDays)
    }

    @Test fun `identische Zeitplaene unterschiedlicher Laenge ergeben AGREE ueber die Schnittmenge`() {
        // Die Jahresseite liefert ~400 Tage, die Proxy-Quellen ~31. Ohne
        // Schnittmengen-Beschraenkung waere das ein Konflikt.
        val jahr = plan(days = 400)
        val monat = plan(days = 31)

        val result = crossCheck(jahr, monat)

        assertEquals(Verdict.AGREE, result.verdict)
        assertEquals(31, result.comparedDays)
        assertEquals(0, result.differingDays)
        assertEquals(0, result.maxAbsMinutes)
        assertNull(result.firstDiff)
    }

    @Test fun `ein Tag um eine Minute ergibt MINOR_DRIFT`() {
        val a = plan(days = 31)
        val b = plan(days = 31) + (day0.plusDays(4) to six(asr = "16:40"))

        val result = crossCheck(a, b)

        assertEquals(Verdict.MINOR_DRIFT, result.verdict)
        assertEquals(31, result.comparedDays)
        assertEquals(1, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
        assertEquals(day0.plusDays(4), result.firstDiff)
    }

    @Test fun `vier Tage um eine Minute ergeben CONFLICT - die Tage-Grenze`() {
        // Wenige Minuten, aber zu viele Tage: die FORM eines systematischen
        // Parser-Fehlers, nicht die einer Diyanet-Korrektur.
        val a = plan(days = 31)
        val b = plan(days = 31) + (0 until 4).associate {
            day0.plusDays(it.toLong()) to six(asr = "16:40")
        }

        val result = crossCheck(a, b)

        assertEquals(Verdict.CONFLICT, result.verdict)
        assertEquals(4, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
    }

    @Test fun `ein Tag um drei Minuten ergibt CONFLICT - die Minuten-Grenze`() {
        val a = plan(days = 31)
        val b = plan(days = 31) + (day0 to six(isha = "20:56"))

        val result = crossCheck(a, b)

        assertEquals(Verdict.CONFLICT, result.verdict)
        assertEquals(1, result.differingDays)
        assertEquals(3, result.maxAbsMinutes)
    }

    @Test fun `genau maxDriftDays Tage um genau maxDriftMinutes ist noch MINOR_DRIFT`() {
        // Nagelt fest, in welche Richtung die Grenzen fallen: <= ist drin.
        val a = plan(days = 31)
        val b = plan(days = 31) + (0 until 3).associate {
            day0.plusDays(it.toLong()) to six(isha = "20:55")
        }

        val result = crossCheck(a, b, maxDriftDays = 3, maxDriftMinutes = 2)

        assertEquals(Verdict.MINOR_DRIFT, result.verdict)
        assertEquals(3, result.differingDays)
        assertEquals(2, result.maxAbsMinutes)
    }

    @Test fun `eigene Schwellen werden beachtet`() {
        val a = plan(days = 31)
        val b = plan(days = 31) + (0 until 3).associate {
            day0.plusDays(it.toLong()) to six(isha = "20:55")
        }

        assertEquals(Verdict.CONFLICT, crossCheck(a, b, maxDriftDays = 2, maxDriftMinutes = 2).verdict)
        assertEquals(Verdict.CONFLICT, crossCheck(a, b, maxDriftDays = 3, maxDriftMinutes = 1).verdict)
    }

    // --- Jedes der sechs Felder einzeln: sechs Faelle, damit beim
    // Vergleichen kein Feld vergessen wird.

    @Test fun `Abweichung in fajr wird erkannt`() {
        val result = crossCheck(plan(days = 1), plan(days = 1, times = six(fajr = "04:55")))
        assertEquals(1, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
    }

    @Test fun `Abweichung in sunrise wird erkannt`() {
        val result = crossCheck(plan(days = 1), plan(days = 1, times = six(sunrise = "06:24")))
        assertEquals(1, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
    }

    @Test fun `Abweichung in dhuhr wird erkannt`() {
        val result = crossCheck(plan(days = 1), plan(days = 1, times = six(dhuhr = "13:03")))
        assertEquals(1, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
    }

    @Test fun `Abweichung in asr wird erkannt`() {
        val result = crossCheck(plan(days = 1), plan(days = 1, times = six(asr = "16:40")))
        assertEquals(1, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
    }

    @Test fun `Abweichung in maghrib wird erkannt`() {
        val result = crossCheck(plan(days = 1), plan(days = 1, times = six(maghrib = "19:32")))
        assertEquals(1, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
    }

    @Test fun `Abweichung in isha wird erkannt`() {
        val result = crossCheck(plan(days = 1), plan(days = 1, times = six(isha = "20:54")))
        assertEquals(1, result.differingDays)
        assertEquals(1, result.maxAbsMinutes)
    }

    @Test fun `ein Tag mit zwei abweichenden Feldern zaehlt als EIN abweichender Tag`() {
        val b = plan(days = 1, times = six(fajr = "04:55", isha = "20:54"))

        val result = crossCheck(plan(days = 1), b)

        assertEquals(1, result.differingDays)
        assertEquals(1, result.comparedDays)
    }

    @Test fun `maxAbsMinutes ist das Maximum ueber alle Tage und Felder`() {
        val a = plan(days = 31)
        val b = plan(days = 31) +
            (day0 to six(fajr = "04:55")) +
            (day0.plusDays(1) to six(fajr = "04:56"))

        val result = crossCheck(a, b)

        assertEquals(2, result.differingDays)
        assertEquals(2, result.maxAbsMinutes)
    }

    @Test fun `firstDiff ist der chronologisch fruehste abweichende Tag, nicht der zuerst eingefuegte`() {
        val a = plan(days = 31)
        // Bewusst in umgekehrter Reihenfolge aufgebaut: der spaetere Tag
        // wird ZUERST eingefuegt.
        val b = LinkedHashMap<LocalDate, SixTimes>().apply {
            put(day0.plusDays(9), six(asr = "16:40"))
            put(day0.plusDays(2), six(asr = "16:40"))
            for (i in 0 until 31) putIfAbsent(day0.plusDays(i.toLong()), six())
        }

        val result = crossCheck(a, b)

        assertEquals(Verdict.MINOR_DRIFT, result.verdict)
        assertEquals(2, result.differingDays)
        assertEquals(day0.plusDays(2), result.firstDiff)
    }

    @Test fun `Minutenabstand zirkulaer - 23-59 gegen 00-01 sind zwei Minuten, kein Konflikt`() {
        // Naiv gerechnet waeren das 1438 Minuten und damit ein Konflikt,
        // obwohl es der Tagesuebergang und zwei Minuten sind.
        val a = plan(days = 1, times = six(isha = "23:59"))
        val b = plan(days = 1, times = six(isha = "00:01"))

        val result = crossCheck(a, b)

        assertEquals(Verdict.MINOR_DRIFT, result.verdict)
        assertEquals(2, result.maxAbsMinutes)
    }

    @Test fun `zirkulaer rettet keine Zwoelf-Stunden-Verwechslung`() {
        // AM/PM-Fehler bleiben auch zirkulaer bei 12 h und damit ein Konflikt.
        val a = plan(days = 1, times = six(dhuhr = "13:02"))
        val b = plan(days = 1, times = six(dhuhr = "01:02"))

        val result = crossCheck(a, b)

        assertEquals(Verdict.CONFLICT, result.verdict)
        assertEquals(720, result.maxAbsMinutes)
    }
}
