package de.gebetszeiten.core.prayertimes

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Dense day-by-day verification against 31 consecutive days of OFFICIAL Diyanet
 * data for Nürnberg (location_id 11024), 7 June – 7 July 2026, obtained via the
 * prayertimes.api.abdus.dev proxy (a 1:1 scrape of namazvakitleri.diyanet.gov.tr).
 *
 * Columns: date, fajr, sunrise, dhuhr, asr, maghrib, isha.
 */
class OfficialDiyanetWindowTest {

    private val nuremberg = GeoLocation(49.4521, 11.0767)
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private val reference = """
        2026-06-07 03:35 05:04 13:20 17:36 21:25 22:45
        2026-06-08 03:34 05:04 13:20 17:36 21:26 22:45
        2026-06-09 03:34 05:03 13:20 17:36 21:27 22:46
        2026-06-10 03:34 05:03 13:20 17:37 21:28 22:46
        2026-06-11 03:34 05:03 13:20 17:37 21:28 22:47
        2026-06-12 03:34 05:02 13:21 17:38 21:29 22:47
        2026-06-13 03:34 05:02 13:21 17:38 21:30 22:48
        2026-06-14 03:34 05:02 13:21 17:38 21:30 22:48
        2026-06-15 03:34 05:02 13:21 17:38 21:31 22:49
        2026-06-16 03:34 05:02 13:21 17:39 21:31 22:49
        2026-06-17 03:34 05:02 13:22 17:39 21:32 22:49
        2026-06-18 03:34 05:02 13:22 17:39 21:32 22:50
        2026-06-19 03:34 05:02 13:22 17:40 21:32 22:50
        2026-06-20 03:35 05:02 13:22 17:40 21:33 22:50
        2026-06-21 03:35 05:02 13:23 17:40 21:33 22:51
        2026-06-22 03:35 05:02 13:23 17:40 21:33 22:51
        2026-06-23 03:35 05:03 13:23 17:40 21:33 22:51
        2026-06-24 03:36 05:03 13:23 17:41 21:33 22:51
        2026-06-25 03:36 05:03 13:23 17:41 21:33 22:51
        2026-06-26 03:36 05:04 13:24 17:41 21:33 22:51
        2026-06-27 03:37 05:04 13:24 17:41 21:33 22:51
        2026-06-28 03:37 05:05 13:24 17:41 21:33 22:51
        2026-06-29 03:37 05:05 13:24 17:41 21:33 22:51
        2026-06-30 03:38 05:06 13:24 17:41 21:33 22:51
        2026-07-01 03:38 05:06 13:25 17:42 21:33 22:51
        2026-07-02 03:39 05:07 13:25 17:42 21:33 22:51
        2026-07-03 03:39 05:08 13:25 17:42 21:32 22:51
        2026-07-04 03:40 05:08 13:25 17:42 21:32 22:51
        2026-07-05 03:40 05:09 13:25 17:42 21:32 22:51
        2026-07-06 03:41 05:10 13:26 17:42 21:31 22:50
        2026-07-07 03:41 05:11 13:26 17:42 21:31 22:50
    """.trimIndent()

    private fun diff(expected: LocalTime, actual: java.time.ZonedDateTime): Int {
        return abs((actual.hour * 60 + actual.minute) - (expected.hour * 60 + expected.minute))
    }

    @Test
    fun `engine matches 31 days of official Diyanet within tolerance`() {
        val maxDiff = IntArray(6)
        var worst = ""
        reference.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val p = line.split(" ")
            val date = LocalDate.parse(p[0])
            val expected = (1..6).map { LocalTime.parse(p[it]) }
            val t = DiyanetPrayerTimesCalculator.calculate(nuremberg, date, berlin)
            val actual = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
            for (i in 0..5) {
                val d = diff(expected[i], actual[i])
                if (d > maxDiff[i]) { maxDiff[i] = d }
                if (d >= 4) worst += "\n$date idx=$i exp=${expected[i]} act=${actual[i].toLocalTime()} (${d}m)"
            }
        }
        val labels = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
        val report = labels.indices.joinToString(", ") { "${labels[it]}=${maxDiff[it]}m" }
        println("MAXDEV $report")
        if (worst.isNotEmpty()) println("OUTLIERS >=4m:$worst")

        // Solar prayers must be tight; takdir-derived Fajr/Isha allow a few min.
        assertTrue("sunrise max ${maxDiff[1]}m", maxDiff[1] <= 2)
        assertTrue("dhuhr max ${maxDiff[2]}m", maxDiff[2] <= 2)
        assertTrue("asr max ${maxDiff[3]}m", maxDiff[3] <= 2)
        assertTrue("maghrib max ${maxDiff[4]}m", maxDiff[4] <= 2)
        assertTrue("fajr max ${maxDiff[0]}m", maxDiff[0] <= 4)
        assertTrue("isha max ${maxDiff[5]}m", maxDiff[5] <= 4)
    }
}
