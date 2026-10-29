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
        // 360×640: 16:9 wäre 184 dp, gedeckelt auf ein Viertel der Höhe.
        assertEquals(160, compactPhotoHeightDp(screenWidthDp = 360, screenHeightDp = 640))
        assertEquals(179, compactPhotoHeightDp(screenWidthDp = 360, screenHeightDp = 719))
        // Schmal und nicht ganz so klein: 16:9 ist schon flacher als ein Viertel.
        assertEquals(126, compactPhotoHeightDp(screenWidthDp = 256, screenHeightDp = 700))
        assertNull(compactPhotoHeightDp(screenWidthDp = 360, screenHeightDp = 720))
        assertNull(compactPhotoHeightDp(screenWidthDp = 412, screenHeightDp = 915))
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
