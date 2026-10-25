package de.gebetszeiten.core.prayertimes.display

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DisplayTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 25)

    private fun at(h: Int, m: Int, d: LocalDate = date): ZonedDateTime =
        ZonedDateTime.of(d, java.time.LocalTime.of(h, m), zone)

    private fun day(d: LocalDate) = DailyPrayerTimes(
        date = d, zone = zone,
        fajr = at(5, 30, d), sunrise = at(7, 20, d), dhuhr = at(12, 10, d),
        asr = at(15, 0, d), maghrib = at(17, 30, d), isha = at(19, 0, d),
    )

    @Test fun steps() {
        assertEquals("2+ Std", remainingStepShort(Duration.ofMinutes(150)))
        assertEquals("20+ Min", remainingStepShort(Duration.ofMinutes(27)))
        assertEquals("7 Min", remainingStepShort(Duration.ofMinutes(7)))
        assertEquals("", remainingStepShort(Duration.ofSeconds(30)))
        assertEquals("jetzt", remainingStepLabel(Duration.ofSeconds(-5)))
        assertEquals("noch 7 Min", remainingStepLabel(Duration.ofMinutes(7)))
        assertTrue(isUrgent(Duration.ZERO)); assertFalse(isUrgent(Duration.ofMinutes(10)))
    }

    @Test fun karahaTexts() {
        val w = Karaha.Window(Karaha.Kind.ZEVAL, at(13, 1), at(13, 21))
        assertEquals(KarahaLine("⚠️ Karaha bis 13:21", true), karahaLine(Karaha.Status.Active(w)))
        assertEquals(KarahaLine("⚠️ Karaha ab 13:01", false), karahaLine(Karaha.Status.Soon(w)))
        assertNull(karahaLine(Karaha.Status.None))
    }

    @Test fun nextPrayerSkipsSunrise() {
        val up = nextPrayer(day(date), day(date.plusDays(1)), at(6, 0))
        assertEquals(UpcomingPrayer(Prayer.DHUHR, at(12, 10)), up)
    }

    @Test fun nextPrayerAfterIshaIsTomorrowsFajr() {
        val tomorrow = day(date.plusDays(1))
        val up = nextPrayer(day(date), tomorrow, at(21, 0))
        assertEquals(UpcomingPrayer(Prayer.FAJR, tomorrow.fajr), up)
    }

    @Test fun nextPrayerAfterIshaWithoutTomorrowIsNull() {
        assertNull(nextPrayer(day(date), null, at(21, 0)))
    }

    @Test fun germanNames() {
        assertEquals("Asr", Prayer.ASR.germanName())
        assertEquals("Sonnenaufgang", Prayer.SUNRISE.germanName())
        assertEquals("Fajr", Prayer.FAJR.germanName())
        assertEquals("Dhuhr", Prayer.DHUHR.germanName())
        assertEquals("Maghrib", Prayer.MAGHRIB.germanName())
        assertEquals("Isha", Prayer.ISHA.germanName())
    }

    @Test fun boundariesCountAndSettle() {
        val target = 1_700_000_000_000L
        val b = displayStepBoundaries(target, target - 3 * 3_600_000L)
        assertEquals(9 + 5 + 3, b.size)
        assertEquals(500L, BOUNDARY_SETTLE_MS)
        assertEquals(1_000L, BOUNDARY_MIN_LEAD_MS)
        assertTrue(b.all { (target - (it - 500L)) % 60_000L == 0L })
    }

    @Test fun nextBoundarySkipsTooCloseOnes() {
        val now = 1_000_000L
        assertEquals(now + 2_000L, nextDisplayBoundary(listOf(now + 500L, now + 2_000L, now + 9_000L), now))
        assertNull(nextDisplayBoundary(listOf(now + 500L), now))
    }
}
