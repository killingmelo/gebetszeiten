package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.Moment
import de.gebetszeiten.prayer.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Reiter von „Zeiten verstehen“; Fixture: amtliche Zeiten Nürnberg, 05.11.2026. */
class VerstehenTabsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int, day: Int = 5) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)
    private val times = DailyPrayerTimes(
        date = t(0, 0).toLocalDate(), zone = zone,
        fajr = t(5, 21), sunrise = t(7, 3), dhuhr = t(12, 4), asr = t(14, 27), maghrib = t(16, 56), isha = t(18, 25),
    )
    private val nextFajr = t(5, 22, day = 6)
    private val day = VerstehenDay(times, IslamicWindows.karaha(times), IslamicWindows.nafl(times, nextFajr), nextFajr)

    @Test fun `jeder Abschnitt steht in genau einem Reiter`() {
        val all = VERSTEHEN_TABS.flatMap { listOf(it.main) + it.sides }
        assertEquals(Section.entries.toSet(), all.toSet())
        assertEquals(all.size, all.toSet().size)
        // Jeder Abschnitt hat Inhalt in VERSTEHEN.
        all.forEach { s -> VERSTEHEN.single { it.section == s } }
    }

    @Test fun `Nebenabschnitte stehen beim Gebet wie auf Heute`() {
        assertEquals(0, tabIndexOf(Section.JAMAA))
        assertEquals(1, tabIndexOf(Section.DUHA))
        assertEquals(3, tabIndexOf(Section.ISFIRAR_MAKRUH))
        assertEquals(4, tabIndexOf(Section.AWWABIN))
        assertEquals(5, tabIndexOf(Section.TAHAJJUD))
        assertEquals("head", itemKeyOf(Section.ASR))
        assertEquals("side-ISFIRAR_MAKRUH", itemKeyOf(Section.ISFIRAR_MAKRUH))
        // Makrūh-Nebenzeiten unter „Worauf achten“, freiwillige Gebete unter „Dazu gehört“.
        assertEquals(true, isCautionSide(Section.ISFIRAR_MAKRUH))
        assertEquals(false, isCautionSide(Section.AWWABIN))
    }

    @Test fun `Verstehen oeffnet beim laufenden Abschnitt`() {
        assertEquals(1, tabOfMoment(Moment.DUHA))
        assertEquals(2, tabOfMoment(Moment.ZAWAL))
        assertEquals(5, tabOfMoment(Moment.ISHA))
    }

    @Test fun `Kacheln zeigen Beginn und Ende von heute`() {
        assertEquals(TabSpan("05:21", "07:03"), tabSpan(0, day))
        assertEquals(TabSpan("07:03", "07:48"), tabSpan(1, day))
        assertEquals(TabSpan("12:04", "14:27"), tabSpan(2, day))
        assertEquals(TabSpan("18:25", "05:22"), tabSpan(5, day))
        assertNull(tabSpan(5, day.copy(nextFajr = null)).end)
    }

    @Test fun `Nebenabschnitte tragen ihre Spanne`() {
        assertEquals("07:48 bis 11:44", sideTime(Section.DUHA, day))
        assertEquals("16:16 bis 16:56", sideTime(Section.ISFIRAR_MAKRUH, day))
        assertEquals("16:56 bis 18:25", sideTime(Section.AWWABIN, day))
        assertNull(sideTime(Section.JAMAA, day))
    }
}
