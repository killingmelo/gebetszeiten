package de.gebetszeiten.core.prayertimes

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Full-year regression against the OFFICIAL Diyanet 2026 table for Nürnberg
 * (district 11024), bundled at resources/diyanet_nurnberg_2026.csv (the official
 * yearly export from namazvakitleri.diyanet.gov.tr).
 *
 * Guards the calibrated high-latitude rule: solar prayers must stay within a
 * minute or two every day; Fajr/Isha use the true 18°/17° angle outside the
 * summer white-nights window and takdir inside it, so their mean error is small
 * with a few-minute residual confined to the late-April / early-August
 * transition weeks (the aqrab al-ayyâm fuzziness inherent to Diyanet's method).
 */
class YearCompareTest {

    private val loc = GeoLocation(49.4521, 11.0767)
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private data class Off(val fajr: LocalTime, val sun: LocalTime, val dhuhr: LocalTime, val asr: LocalTime, val maghrib: LocalTime, val isha: LocalTime)

    private fun load(): Map<LocalDate, Off> {
        val txt = javaClass.getResourceAsStream("/diyanet_nurnberg_2026.csv")!!.bufferedReader().readText()
        val out = LinkedHashMap<LocalDate, Off>()
        txt.lineSequence().drop(1).filter { it.isNotBlank() }.forEach { line ->
            val p = line.split(",")
            out[LocalDate.parse(p[0])] = Off(
                LocalTime.parse(p[1]), LocalTime.parse(p[2]), LocalTime.parse(p[3]),
                LocalTime.parse(p[4]), LocalTime.parse(p[5]), LocalTime.parse(p[6]),
            )
        }
        return out
    }

    private fun mins(t: java.time.ZonedDateTime) = t.hour * 60 + t.minute
    private fun mins(t: LocalTime) = t.hour * 60 + t.minute

    @Test
    fun `engine matches the official Diyanet year within calibrated tolerances`() {
        val official = load()
        val n = official.size
        assertTrue("expected ~365 reference days, got $n", n >= 360)

        val names = listOf("Fajr", "Sun", "Dhuhr", "Asr", "Maghrib", "Isha")
        val maxD = IntArray(6); val sumD = IntArray(6); val over3 = IntArray(6)
        for ((date, off) in official) {
            val e = DiyanetPrayerTimesCalculator.calculate(loc, date, zone)
            val eng = listOf(e.fajr, e.sunrise, e.dhuhr, e.asr, e.maghrib, e.isha)
            val ref = listOf(off.fajr, off.sun, off.dhuhr, off.asr, off.maghrib, off.isha)
            for (i in 0..5) {
                val d = abs(mins(eng[i]) - mins(ref[i]))
                if (d > maxD[i]) maxD[i] = d
                sumD[i] += d
                if (d > 3) over3[i]++
            }
        }
        val report = (0..5).joinToString(", ") {
            "%s(max %dm, mean %.2f, >3m %d)".format(names[it], maxD[it], sumD[it].toDouble() / n, over3[it])
        }
        println("YEAR-COMPARE 2026 Nürnberg: $report")

        // Solar prayers: tight every day.
        for (i in listOf(1, 2, 3, 4)) {
            assertTrue("${names[i]} max ${maxD[i]}m must be <= 2", maxD[i] <= 2)
        }
        // Fajr/Isha: small mean; bounded transition-week residual.
        assertTrue("Fajr mean ${sumD[0].toDouble() / n} must be <= 3.0", sumD[0].toDouble() / n <= 3.0)
        assertTrue("Isha mean ${sumD[5].toDouble() / n} must be <= 3.5", sumD[5].toDouble() / n <= 3.5)
        assertTrue("Fajr max ${maxD[0]}m must be <= 25", maxD[0] <= 25)
        assertTrue("Isha max ${maxD[5]}m must be <= 22", maxD[5] <= 22)
        assertTrue("Fajr days>3m ${over3[0]} must be <= 70", over3[0] <= 70)
        assertTrue("Isha days>3m ${over3[5]} must be <= 70", over3[5] <= 70)
    }
}
