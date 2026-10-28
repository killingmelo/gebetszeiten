package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Tagesliste der Hauptansicht; Fixture: Nürnberg, 28.10.2026. */
class DayRowsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int, day: Int = 28) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)

    private val times = DailyPrayerTimes(
        date = t(0, 0).toLocalDate(),
        zone = zone,
        fajr = t(5, 10),
        sunrise = t(6, 50),
        dhuhr = t(12, 5),
        asr = t(14, 38),
        maghrib = t(17, 9),
        isha = t(18, 37),
    )
    private val nextFajr = t(5, 11, day = 29)
    private val karaha = IslamicWindows.karaha(times)
    private val nafl = IslamicWindows.nafl(times, nextFajr)

    private fun rows(
        now: ZonedDateTime,
        next: ZonedDateTime? = nextFajr,
        isToday: Boolean = true,
        showKaraha: Boolean = true,
        showCemaat: Boolean = false,
        offset: Int = 30,
    ) = dayRows(times, karaha, nafl, next, now, isToday, showKaraha, showCemaat, offset)

    private fun List<DayRow>.prayers() = filterIsInstance<DayRow.PrayerRow>()
    private fun List<DayRow>.sides() = filterIsInstance<DayRow.SideRow>()

    @Test fun `fullDayAlwaysListed`() {
        val p = rows(t(13, 20)).prayers().filter { !it.nextDay }.map { it.prayer }
        assertEquals(
            listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA),
            p,
        )
    }

    @Test fun `pastFlagsBefore1320`() {
        val byPrayer = rows(t(13, 20)).prayers().filter { !it.nextDay }.associateBy { it.prayer }
        assertTrue(byPrayer.getValue(Prayer.FAJR).past)
        assertTrue(byPrayer.getValue(Prayer.SUNRISE).past)
        assertTrue(byPrayer.getValue(Prayer.DHUHR).past)
        assertFalse(byPrayer.getValue(Prayer.ASR).past)
        assertFalse(byPrayer.getValue(Prayer.MAGHRIB).past)
        assertFalse(byPrayer.getValue(Prayer.ISHA).past)
        val duha = rows(t(13, 20)).sides().first { it.kind == Section.DUHA }
        assertTrue(duha.past)
    }

    @Test fun `nextDayFajrAppearsAfterIsha`() {
        val last = rows(t(21, 40)).last() as DayRow.PrayerRow
        assertEquals(Prayer.FAJR, last.prayer)
        assertTrue(last.nextDay)
        assertEquals(t(5, 11, day = 29), last.at)
        assertTrue(rows(t(13, 20)).prayers().none { it.nextDay })
    }

    @Test fun `noNextDayFajrOnLastCoveredDay`() {
        assertTrue(rows(t(21, 40), next = null).prayers().none { it.nextDay })
    }

    @Test fun `makruhRowsHiddenWhenSwitchedOff`() {
        val kinds = rows(t(13, 20), showKaraha = false).sides().map { it.kind }
        assertFalse(kinds.contains(Section.SHURUQ_MAKRUH))
        assertFalse(kinds.contains(Section.ZUHR))
        assertFalse(kinds.contains(Section.ISFIRAR_MAKRUH))
        assertTrue(kinds.contains(Section.DUHA))
        val on = rows(t(13, 20), showKaraha = true).sides().map { it.kind }
        assertTrue(on.containsAll(listOf(Section.SHURUQ_MAKRUH, Section.ZUHR, Section.ISFIRAR_MAKRUH)))
    }

    @Test fun `jamaaOnlyWhenEnabled`() {
        assertTrue(rows(t(13, 20), showCemaat = false).sides().none { it.kind == Section.JAMAA })
        val j = rows(t(13, 20), showCemaat = true).sides().single { it.kind == Section.JAMAA }
        assertEquals(t(6, 20), j.start)
        assertNull(j.end)
    }

    @Test fun `exactlyOneCurrentAt1320`() {
        val current = rows(t(13, 20)).prayers().filter { it.current }
        assertEquals(1, current.size)
        assertEquals(Prayer.DHUHR, current.single().prayer)
    }

    @Test fun `noCurrentRowForOtherDayOrPreviousNight`() {
        assertTrue(rows(t(13, 20), isToday = false).prayers().none { it.current || it.past })
        assertTrue(rows(t(3, 0)).prayers().none { it.current })
    }

    @Test fun `shuruqIsCurrentJustAfterSunrise`() {
        val current = rows(t(7, 0)).prayers().single { it.current }
        assertEquals(Prayer.SUNRISE, current.prayer)
    }

    @Test fun `noCurrentRowDuringZawal`() {
        // Zawāl ist makrūh und keine Gebetszeile; Ẓuhr beginnt erst 12:05.
        assertTrue(rows(t(11, 50)).prayers().none { it.current })
    }
}
