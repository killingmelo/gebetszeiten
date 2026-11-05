package de.gebetszeiten.ui

import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.prayer.DayRow
import de.gebetszeiten.prayer.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Die kleinen Entscheidungen von „Heute“, die ohne Compose prüfbar sind. */
class HeuteLogicTest {

    @Test fun `nach Mitternacht rueckt heute mit`() {
        val gestern = LocalDate.of(2026, 10, 28)
        val heute = gestern.plusDays(1)
        assertEquals(heute, rolledSelectedDate(selected = gestern, previousToday = gestern, today = heute))
    }

    @Test fun `ein anderer gewaehlter Tag bleibt nach Mitternacht stehen`() {
        val gestern = LocalDate.of(2026, 10, 28)
        val heute = gestern.plusDays(1)
        val gewaehlt = LocalDate.of(2026, 11, 3)
        assertEquals(gewaehlt, rolledSelectedDate(selected = gewaehlt, previousToday = gestern, today = heute))
    }

    @Test fun `nach links wischen ist morgen, nach rechts gestern`() {
        assertEquals(1L, swipeDayDelta(totalDx = -200f, thresholdPx = 150f))
        assertEquals(-1L, swipeDayDelta(totalDx = 200f, thresholdPx = 150f))
    }

    @Test fun `kurzes Wischen wechselt den Tag nicht`() {
        assertEquals(0L, swipeDayDelta(totalDx = -149f, thresholdPx = 150f))
        assertEquals(0L, swipeDayDelta(totalDx = 149f, thresholdPx = 150f))
        assertEquals(0L, swipeDayDelta(totalDx = 0f, thresholdPx = 150f))
    }

    @Test fun `genau an der Schwelle wechselt der Tag`() {
        assertEquals(1L, swipeDayDelta(totalDx = -150f, thresholdPx = 150f))
        assertEquals(-1L, swipeDayDelta(totalDx = 150f, thresholdPx = 150f))
    }

    @Test fun `Šurūq heisst jetzt, nicht aktuelles Gebet`() {
        assertEquals(R.string.status_now, prayerStatusRes(Prayer.SUNRISE, current = true, faded = false))
        assertEquals(R.string.status_current, prayerStatusRes(Prayer.ASR, current = true, faded = false))
        assertEquals(R.string.status_past, prayerStatusRes(Prayer.FAJR, current = false, faded = true))
        assertNull(prayerStatusRes(Prayer.ISHA, current = false, faded = false))
    }

    private val t0 = java.time.ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, java.time.ZoneId.of("Europe/Berlin"))

    private fun h(hh: Int, mm: Int) = t0.withHour(hh).withMinute(mm)

    @Test fun `Nebenzeiten stehen unter ihrem Gebet`() {
        val rows = listOf(
            DayRow.PrayerRow(Prayer.FAJR, h(5, 20), past = true, current = false),
            DayRow.SideRow(Section.JAMAA, h(6, 31), null, past = true),
            DayRow.PrayerRow(Prayer.SUNRISE, h(7, 1), past = true, current = false),
            DayRow.SideRow(Section.SHURUQ_MAKRUH, h(7, 1), h(7, 46), past = true),
            DayRow.SideRow(Section.DUHA, h(7, 46), h(11, 44), past = true),
            DayRow.SideRow(Section.ZUHR, h(11, 44), h(12, 4), past = true),
            DayRow.PrayerRow(Prayer.DHUHR, h(12, 4), past = true, current = false),
            DayRow.PrayerRow(Prayer.ASR, h(14, 28), past = true, current = true),
            DayRow.SideRow(Section.ISFIRAR_MAKRUH, h(16, 17), h(16, 57), past = false),
            DayRow.PrayerRow(Prayer.MAGHRIB, h(16, 57), past = false, current = false),
            DayRow.SideRow(Section.AWWABIN, h(16, 57), h(18, 25), past = false),
            DayRow.PrayerRow(Prayer.ISHA, h(18, 25), past = false, current = false),
        )
        val g = groupUnderPrayers(rows).associate { it.prayer.prayer to it.subs.map { s -> s.kind } }
        assertEquals(listOf(Section.JAMAA), g[Prayer.FAJR])
        assertEquals(listOf(Section.SHURUQ_MAKRUH, Section.DUHA), g[Prayer.SUNRISE])
        // Zawāl kommt vor Ẓuhr und gehört zu ihm, nicht zu Šurūq.
        assertEquals(listOf(Section.ZUHR), g[Prayer.DHUHR])
        assertEquals(listOf(Section.ISFIRAR_MAKRUH), g[Prayer.ASR])
        assertEquals(listOf(Section.AWWABIN), g[Prayer.MAGHRIB])
        assertEquals(emptyList<Section>(), g[Prayer.ISHA])

        // Kurzformen: beginnt mit dem Gebet „bis“, reicht zum nächsten „ab“, sonst beide Enden.
        assertEquals(SubForm.UNTIL, subFormOf(rows[3] as DayRow.SideRow, h(7, 1)))
        assertEquals(SubForm.SPAN, subFormOf(rows[4] as DayRow.SideRow, h(7, 1)))
        assertEquals(SubForm.SPAN, subFormOf(rows[5] as DayRow.SideRow, h(12, 4)))
        assertEquals(SubForm.FROM, subFormOf(rows[8] as DayRow.SideRow, h(14, 28)))
        assertEquals(SubForm.UNTIL, subFormOf(rows[10] as DayRow.SideRow, h(16, 57)))
        assertEquals(SubForm.AT, subFormOf(rows[1] as DayRow.SideRow, h(5, 20)))
    }

    @Test fun `jede Gebetszeile fuehrt zu ihrem Eintrag in Zeiten verstehen`() {
        assertEquals(de.gebetszeiten.prayer.Section.FAJR, explainSectionOf(Prayer.FAJR))
        assertEquals(de.gebetszeiten.prayer.Section.SHURUQ_MAKRUH, explainSectionOf(Prayer.SUNRISE))
        assertEquals(de.gebetszeiten.prayer.Section.ZUHR, explainSectionOf(Prayer.DHUHR))
        assertEquals(de.gebetszeiten.prayer.Section.ISHA, explainSectionOf(Prayer.ISHA))
    }
}
