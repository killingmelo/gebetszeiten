package de.gebetszeiten.ui

import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Die kleinen Entscheidungen von „Heute“, die ohne Compose prüfbar sind. */
class HeuteLogicTest {

    @Test fun `kleiner Bildschirm bekommt ein flacheres Foto`() {
        assertEquals(16f / 9f, photoAspectFor(screenHeightDp = 640), 0.0001f)
        assertEquals(16f / 9f, photoAspectFor(screenHeightDp = 719), 0.0001f)
        assertEquals(10f / 7f, photoAspectFor(screenHeightDp = 720), 0.0001f)
        assertEquals(10f / 7f, photoAspectFor(screenHeightDp = 915), 0.0001f)
    }

    @Test fun `Abstand unter dem Foto schrumpft auf kleinem Bildschirm`() {
        assertEquals(12, heroGapDp(screenHeightDp = 640))
        assertEquals(20, heroGapDp(screenHeightDp = 915))
    }

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

    @Test fun `Šurūq heisst jetzt, nicht aktuelles Gebet`() {
        assertEquals(R.string.status_now, prayerStatusRes(Prayer.SUNRISE, current = true, faded = false))
        assertEquals(R.string.status_current, prayerStatusRes(Prayer.ASR, current = true, faded = false))
        assertEquals(R.string.status_past, prayerStatusRes(Prayer.FAJR, current = false, faded = true))
        assertNull(prayerStatusRes(Prayer.ISHA, current = false, faded = false))
    }
}
