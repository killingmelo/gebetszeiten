package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** Titel-Zustand der Hauptansicht; Fixture: Nürnberg, 28.10.2026. */
class HeroStateTest {
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

    private fun at(now: ZonedDateTime, next: ZonedDateTime? = nextFajr, isToday: Boolean = true) =
        heroState(times, karaha, nafl, next, now, isToday)

    @Test fun `at1320IsDhuhrWith78MinutesUntilAsr`() {
        val s = at(t(13, 20))
        assertEquals(Moment.DHUHR, s.moment)
        assertEquals(Duration.ofMinutes(78), s.remaining)
        assertEquals(Upcoming(Prayer.ASR, t(14, 38)), s.next)
        assertEquals(PhotoKey.ZUHR, s.photo)
    }

    @Test fun `at0300IsIshaOfPreviousDayUntilFajr`() {
        val s = at(t(3, 0))
        assertEquals(Moment.ISHA, s.moment)
        assertEquals(Duration.between(t(3, 0), t(5, 10)), s.remaining)
        assertEquals(Upcoming(Prayer.FAJR, t(5, 10)), s.next)
        assertEquals(PhotoKey.ISHA, s.photo)
    }

    @Test fun `at2140IsIshaUntilTomorrowFajr`() {
        val s = at(t(21, 40))
        assertEquals(Moment.ISHA, s.moment)
        assertEquals(Duration.ofHours(7).plusMinutes(31), s.remaining)
        assertEquals(Upcoming(Prayer.FAJR, nextFajr), s.next)
    }

    @Test fun `afterIshaWithoutNextFajrHasNoRemaining`() {
        val s = at(t(21, 40), next = null)
        assertEquals(Moment.ISHA, s.moment)
        assertNull(s.remaining)
        assertNull(s.next)
    }

    @Test fun `justAfterSunriseIsShuruqWithMakruh`() {
        val s = at(t(7, 0))
        assertEquals(Moment.SHURUQ, s.moment)
        assertEquals(PhotoKey.SHURUQ, s.photo)
        assertEquals(Duration.between(t(7, 0), t(12, 5)), s.remaining)
        assertEquals(Upcoming(Prayer.DHUHR, t(12, 5)), s.next)
        assertNotNull(s.makruh)
        assertTrue(s.makruh!!.state.active)
        assertEquals(karaha.sunriseEnd, s.makruh!!.end)
    }

    @Test fun `inDuhaWindowIsDuha`() {
        val s = at(t(9, 0))
        assertEquals(Moment.DUHA, s.moment)
        assertEquals(PhotoKey.SHURUQ, s.photo)
        assertEquals(Upcoming(Prayer.DHUHR, t(12, 5)), s.next)
        assertNull(s.makruh)
    }

    @Test fun `fifteenMinutesBeforeZenithShowsUpcomingMakruh`() {
        val near = at(t(11, 31))
        assertNotNull(near.makruh)
        assertFalse(near.makruh!!.state.active)
        assertEquals(karaha.zevalEnd, near.makruh!!.end)
        // Die bevorstehende Zeile nennt den Beginn („Makrūh ab 11:45“).
        assertEquals(karaha.zevalStart, near.makruh!!.start)
        assertNull(at(t(11, 20)).makruh)
    }

    @Test fun `fajrWindowRunsUntilSunrise`() {
        val s = at(t(5, 30))
        assertEquals(Moment.FAJR, s.moment)
        assertEquals(PhotoKey.FAJR, s.photo)
        assertEquals(Duration.ofMinutes(80), s.remaining)
        assertEquals(Upcoming(Prayer.DHUHR, t(12, 5)), s.next)
    }

    @Test fun `asrAndMaghribHaveNextObligatoryPrayer`() {
        val asr = at(t(15, 0))
        assertEquals(Moment.ASR, asr.moment)
        assertEquals(Upcoming(Prayer.MAGHRIB, t(17, 9)), asr.next)
        val maghrib = at(t(17, 30))
        assertEquals(Moment.MAGHRIB, maghrib.moment)
        assertEquals(PhotoKey.MAGHRIB, maghrib.photo)
        assertEquals(Upcoming(Prayer.ISHA, t(18, 37)), maghrib.next)
    }

    @Test fun `otherDayHasNoLiveParts`() {
        val s = at(t(7, 0), isToday = false)
        assertEquals(Moment.FAJR, s.moment)
        assertEquals(PhotoKey.FAJR, s.photo)
        assertNull(s.remaining)
        assertNull(s.makruh)
        assertNull(s.next)
    }

    @Test fun `at1150IsZawalUntilDhuhr`() {
        val s = at(t(11, 50))
        assertEquals(Moment.ZAWAL, s.moment)
        assertEquals(PhotoKey.ZUHR, s.photo)
        assertEquals(Duration.ofMinutes(15), s.remaining)
        assertEquals(Upcoming(Prayer.DHUHR, t(12, 5)), s.next)
        assertNotNull(s.makruh)
        assertTrue(s.makruh!!.state.active)
        assertEquals(karaha.zevalEnd, s.makruh!!.end)
    }

    @Test fun `zawalBeginsExactlyAtZevalStartAndEndsAtDhuhr`() {
        // Ḍuḥā endet dort, wo die Zawāl-Zeit beginnt.
        assertEquals(nafl.duhaEnd, karaha.zevalStart)
        assertEquals(Moment.DUHA, at(karaha.zevalStart.minusMinutes(1)).moment)
        assertEquals(Moment.ZAWAL, at(karaha.zevalStart).moment)
        assertEquals(t(11, 45), karaha.zevalStart)
        assertEquals(Moment.DHUHR, at(t(12, 5)).moment)
    }
}
