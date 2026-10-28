package de.gebetszeiten.alarm

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.prayer.IslamicWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Das Widget wechselt seinen Titel zu Beginn von Ḍuḥā und der Zawāl-Zeit.
 * Ohne Karāha-Anzeige weckt dort sonst nichts die App, und das Widget hielte
 * „Ḍuḥā“ durch die ganze Makrūh-Zeit.
 */
class WidgetTitleBoundariesTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 28, h, m, 0, 0, zone)
    private val times = DailyPrayerTimes(
        date = LocalDate.of(2026, 10, 28),
        zone = zone,
        fajr = t(5, 10),
        sunrise = t(6, 50),
        dhuhr = t(12, 5),
        asr = t(14, 38),
        maghrib = t(17, 9),
        isha = t(18, 37),
    )
    private val karaha = IslamicWindows.karaha(times)
    private val nafl = IslamicWindows.nafl(times, null)
    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli() + BOUNDARY_SETTLE_MS

    @Test fun `Karaha aus und Widget liegt - Beginn Duha und Zawal sind Grenzen`() {
        val b = widgetTitleBoundaries(karaha, nafl, showKaraha = false, widgetPlaced = true)
        assertTrue("Beginn Ḍuḥā fehlt", ms(nafl.duhaStart) in b)
        assertTrue("Beginn Zawāl fehlt", ms(karaha.zevalStart) in b)
    }

    @Test fun `Karaha aus ohne Widget - keine Grenzen`() {
        val b = widgetTitleBoundaries(karaha, nafl, showKaraha = false, widgetPlaced = false)
        assertTrue(ms(nafl.duhaStart) !in b && ms(karaha.zevalStart) !in b)
    }

    @Test fun `Karaha an - keine doppelten Grenzen`() {
        val karahaGrenzen = Karaha.boundaries(Karaha.windows(times))
            .map { it.toInstant().toEpochMilli() + BOUNDARY_SETTLE_MS }
        // Die Karāha-Grenzen decken beide Zeitpunkte schon ab ...
        assertTrue(ms(nafl.duhaStart) in karahaGrenzen && ms(karaha.zevalStart) in karahaGrenzen)
        // ... also kommt hier nichts dazu.
        val alle = karahaGrenzen + widgetTitleBoundaries(karaha, nafl, showKaraha = true, widgetPlaced = true)
        assertEquals(alle.size, alle.toSet().size)
    }
}
