package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schlusspruefung, Important 2: `IslamicWindows.nafl` ist die einzige Stelle,
 * die das Tahajjud-Fenster aus `nextFajr` ableitet. Fehlt der Folgetag (letzter
 * abgedeckter Tag, gescheiterter Abruf ohne Reserve), darf das NICHT die
 * ganze Naht zum Kippen bringen - HEUTE hat weiterhin amtliche Zeiten, nur das
 * Tahajjud-Fenster (das den Folgetag braucht) entfaellt, statt eine Zeit zu
 * erfinden. `MainActivity.kt` haengt die ganze Heute-Karte an dieselbe
 * Entscheidung; dieser Test haelt die Naht selbst fest, ohne Compose.
 */
class IslamicWindowsNaflTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2027, 12, 31, h, m, 0, 0, zone)

    private val times = DailyPrayerTimes(
        date = t(0, 0).toLocalDate(),
        zone = zone,
        fajr = t(6, 2),
        sunrise = t(7, 58),
        dhuhr = t(12, 4),
        asr = t(14, 32),
        maghrib = t(16, 21),
        isha = t(17, 54),
    )

    @Test fun `times vorhanden, nextFajr fehlt - Duha und Awwabin bleiben, Tahajjud entfaellt`() {
        val nafl = IslamicWindows.nafl(times, nextFajr = null)

        assertNotNull(nafl.duhaStart)
        assertNotNull(nafl.duhaEnd)
        assertEquals(times.maghrib, nafl.awwabinStart)
        assertEquals(times.isha, nafl.awwabinEnd)

        assertNull("Ohne den Folgetag darf kein Tahajjud-Start erfunden werden", nafl.tahajjudStart)
        assertNull("Ohne den Folgetag darf kein Tahajjud-Ende erfunden werden", nafl.tahajjudEnd)
    }

    @Test fun `times und nextFajr vorhanden - Tahajjud-Fenster wird abgeleitet`() {
        val nextFajr = t(6, 4).plusDays(1)
        val nafl = IslamicWindows.nafl(times, nextFajr)

        assertEquals(nextFajr, nafl.tahajjudEnd)
        assertNotNull(nafl.tahajjudStart)
        // Letztes Drittel der Nacht liegt zwischen Maghrib und dem Folgetag-Fajr.
        assertTrue(nafl.tahajjudStart!!.isAfter(times.maghrib))
        assertTrue(nafl.tahajjudStart!!.isBefore(nextFajr))
    }
}
