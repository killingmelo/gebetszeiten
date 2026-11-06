package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.TimeOffsets
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.Moment
import de.gebetszeiten.prayer.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Zeitzeilen und Sprungziele; Fixture: amtliche Zeiten Nürnberg, 05.11.2026. */
class LehrZeitenTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int, day: Int = 5) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)
    private val times = DailyPrayerTimes(
        date = t(0, 0).toLocalDate(), zone = zone,
        fajr = t(5, 21), sunrise = t(7, 3), dhuhr = t(12, 4), asr = t(14, 27), maghrib = t(16, 56), isha = t(18, 25),
    )
    private val nextFajr = t(5, 22, day = 6)
    private val day = VerstehenDay(times, IslamicWindows.karaha(times), IslamicWindows.nafl(times, nextFajr), nextFajr)
    private val none = TimeOffsets.NONE

    @Test fun `amtliche Zeile nennt Ort und Spanne`() {
        assertEquals("Heute in Nürnberg 12:04 bis 14:27", zeitzeile(Teil.ZUHR, day, "Nürnberg", none))
        assertEquals("Heute in Nürnberg 05:21 bis 07:03", zeitzeile(Teil.FAJR, day, "Nürnberg", none))
        assertEquals("Heute in Nürnberg 18:25 bis 05:22", zeitzeile(Teil.ISHA, day, "Nürnberg", none))
    }

    @Test fun `gerechnete Zeile ist gekennzeichnet`() {
        val z = zeitzeile(Teil.DUHA, day, "Nürnberg", none)!!
        assertTrue(z, z.startsWith("Heute etwa "))
        assertTrue(z, z.endsWith(", von der App gerechnet"))
        GERECHNET.forEach { teil ->
            val zeile = zeitzeile(teil, day, "Nürnberg", none)!!
            assertTrue(zeile, zeile.endsWith(", von der App gerechnet"))
        }
    }

    @Test fun `Zawal endet bei Zuhr`() {
        assertEquals("Heute etwa 11:44 bis 12:04, von der App gerechnet", zeitzeile(Teil.ZAWAL, day, "x", none))
    }

    @Test fun `Isha ohne Fagr von morgen hat kein Ende`() {
        assertEquals("Heute in Nürnberg ab 18:25", zeitzeile(Teil.ISHA, day.copy(nextFajr = null), "Nürnberg", none))
    }

    @Test fun `ohne Tag keine Zeile`() {
        Teil.entries.forEach { assertNull(zeitzeile(it, null, "Nürnberg", none)) }
    }

    @Test fun `Verschiebung steht hinter der Uhrzeit`() {
        val off = none.with(Prayer.DHUHR, 5)
        // Die Zeiten des Tages sind schon verschoben (Ẓuhr 12:09).
        val verschoben = day.copy(times = times.copy(dhuhr = t(12, 9)))
        assertEquals("Heute in Nürnberg 12:09 (+5) bis 14:27", zeitzeile(Teil.ZUHR, verschoben, "Nürnberg", off))
        assertEquals("Heute in Nürnberg 05:21 bis 07:03", zeitzeile(Teil.FAJR, verschoben, "Nürnberg", off))
        // Das Ende von ʿAṣr ist der Beginn von Maġrib, nicht von Ẓuhr.
        assertFalse(zeitzeile(Teil.ASR, verschoben, "Nürnberg", off)!!.contains("(+"))
    }

    @Test fun `gerechnete Zeilen tragen keine Verschiebungsmarke`() {
        val off = none.with(Prayer.DHUHR, 5)
        val verschoben = day.copy(times = times.copy(dhuhr = t(12, 9)))
        GERECHNET.forEach { teil ->
            val z = zeitzeile(teil, verschoben, "x", off)!!
            assertFalse(z, z.contains("(+"))
        }
    }

    @Test fun `Isha ohne Ende traegt die Marke von Isha`() {
        val off = none.with(Prayer.ISHA, 3)
        assertEquals("Heute in x ab 18:25 (+3)", zeitzeile(Teil.ISHA, day.copy(nextFajr = null), "x", off))
    }

    @Test fun `kein Gedanken oder Halbgeviertstrich`() {
        val off = TimeOffsets(fajr = -5, dhuhr = 5, isha = 3)
        Teil.entries.forEach { teil ->
            listOf(day, day.copy(nextFajr = null), null).forEach { d ->
                val z = zeitzeile(teil, d, "Nürnberg", off) ?: return@forEach
                assertFalse(z, z.contains('–') || z.contains('—'))
            }
        }
    }

    // Unabhängig von LEHRE: Kapitel 0 bis 4 sind die Gebete, 5 ist „Freiwillig und verpönt“.
    private val teileJeKapitel: Map<Int, Set<Teil>> = mapOf(
        0 to setOf(Teil.FAJR), 1 to setOf(Teil.ZUHR), 2 to setOf(Teil.ASR), 3 to setOf(Teil.MAGHRIB), 4 to setOf(Teil.ISHA),
        5 to setOf(Teil.DUHA, Teil.AWWABIN, Teil.TAHAJJUD, Teil.SHURUQ, Teil.ZAWAL, Teil.ISFIRAR),
    )

    private fun pruefe(z: Ziel) {
        assertTrue("Kapitel ${z.kapitel}", z.kapitel in 0..5)
        assertTrue("$z", z.teil in teileJeKapitel.getValue(z.kapitel))
    }

    @Test fun `jeder Abschnitt und jeder Moment hat ein stimmiges Ziel`() {
        Section.entries.forEach { pruefe(zielVon(it)) }
        Moment.entries.forEach { pruefe(zielVonMoment(it)) }
        assertEquals(Ziel(0, Teil.FAJR), zielVon(Section.JAMAA))
        assertEquals(Ziel(5, Teil.SHURUQ), zielVon(Section.SHURUQ_MAKRUH))
        assertEquals(Ziel(5, Teil.ISFIRAR), zielVon(Section.ISFIRAR_MAKRUH))
        assertEquals(Ziel(5, Teil.TAHAJJUD), zielVon(Section.TAHAJJUD))
        assertEquals(Ziel(5, Teil.ZAWAL), zielVonMoment(Moment.ZAWAL))
        assertEquals(Ziel(1, Teil.ZUHR), zielVonMoment(Moment.DHUHR))
        assertNotNull(zielVonMoment(Moment.DUHA))
    }
}
