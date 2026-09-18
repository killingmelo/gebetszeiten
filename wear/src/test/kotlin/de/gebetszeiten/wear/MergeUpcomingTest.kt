package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [mergeUpcoming] ist die reine Verschmelzungslogik hinter
 * `WearPrayer.upcoming` — herausgezogen, weil die Uhr kein Robolectric hat
 * und `upcoming` selbst (Context-verdrahtet ueber `daily()`) deshalb sonst
 * ganz ungetestet bliebe. Fix-Runde 1 zu Aufgabe 16 bemaengelte genau das:
 * kein Test deckte ab, dass `upcoming` bei zwei fehlenden Tagen leer ist.
 */
class MergeUpcomingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun tag(date: String): DailyPrayerTimes {
        val d = LocalDate.parse(date)
        fun t(hhmm: String) = ZonedDateTime.of(d, java.time.LocalTime.parse(hhmm), zone)
        return DailyPrayerTimes(
            date = d,
            zone = zone,
            fajr = t("04:53"),
            sunrise = t("06:39"),
            dhuhr = t("13:18"),
            asr = t("16:50"),
            maghrib = t("19:47"),
            isha = t("21:18"),
        )
    }

    @Test fun `beide Tage fehlen - leere Liste statt Absturz`() {
        val now = ZonedDateTime.of(LocalDate.parse("2026-09-18"), java.time.LocalTime.parse("12:00"), zone)
        assertEquals(emptyList<Pair<Prayer, ZonedDateTime>>(), mergeUpcoming(null, null, now, count = 6))
    }

    @Test fun `heute fehlt, morgen liegt vor - morgen wird trotzdem geliefert`() {
        val now = ZonedDateTime.of(LocalDate.parse("2026-09-18"), java.time.LocalTime.parse("12:00"), zone)
        val morgen = tag("2026-09-19")
        val ergebnis = mergeUpcoming(null, morgen, now, count = 6)
        assertTrue("morgen sollte Fajr liefern", ergebnis.any { it.first == Prayer.FAJR })
        assertTrue(ergebnis.all { it.second.toLocalDate() == LocalDate.parse("2026-09-19") })
    }

    @Test fun `Sonnenaufgang ist kein Gebet und wird ausgefiltert`() {
        val now = ZonedDateTime.of(LocalDate.parse("2026-09-18"), java.time.LocalTime.parse("00:00"), zone)
        val heute = tag("2026-09-18")
        val ergebnis = mergeUpcoming(heute, null, now, count = 6)
        assertTrue(ergebnis.none { it.first == Prayer.SUNRISE })
    }

    @Test fun `vergangene Zeiten des Tages werden ausgefiltert`() {
        val now = ZonedDateTime.of(LocalDate.parse("2026-09-18"), java.time.LocalTime.parse("18:00"), zone)
        val heute = tag("2026-09-18")
        // Nur heute, kein morgen - sonst liefert morgens Fajr/Dhuhr/Asr
        // (nach `now`) falsch-positiv dieselben Namen wie die gefilterten
        // heutigen.
        val ergebnis = mergeUpcoming(heute, null, now, count = 6)
        // Fajr/Dhuhr/Asr (alle vor 18:00) duerfen nicht mehr vorkommen.
        assertTrue(ergebnis.none { it.first == Prayer.FAJR || it.first == Prayer.DHUHR || it.first == Prayer.ASR })
        assertEquals(listOf(Prayer.MAGHRIB, Prayer.ISHA), ergebnis.map { it.first })
    }

    @Test fun `count begrenzt die Ergebnisliste`() {
        val now = ZonedDateTime.of(LocalDate.parse("2026-09-18"), java.time.LocalTime.parse("00:00"), zone)
        val heute = tag("2026-09-18")
        val morgen = tag("2026-09-19")
        val ergebnis = mergeUpcoming(heute, morgen, now, count = 2)
        assertEquals(2, ergebnis.size)
    }

    @Test fun `ein Gebet genau auf now wird ausgefiltert - nicht nur vergangene`() {
        // Mutation `isAfter` -> `!isBefore` liesse den Grenzfall "genau jetzt"
        // durchrutschen (beide Formen stimmen fuer Zeiten VOR und NACH now
        // ueberein, nur bei GENAUER Gleichheit weichen sie ab) - dieser Fall
        // deckt genau das ab (Fix-Runde 2, Minor 2).
        val heute = tag("2026-09-18")
        val now = heute.maghrib
        val ergebnis = mergeUpcoming(heute, null, now, count = 6)
        assertEquals(listOf(Prayer.ISHA), ergebnis.map { it.first })
    }

    @Test fun `Reihenfolge bleibt chronologisch ueber die Tagesgrenze`() {
        val now = ZonedDateTime.of(LocalDate.parse("2026-09-18"), java.time.LocalTime.parse("21:00"), zone)
        val heute = tag("2026-09-18")
        val morgen = tag("2026-09-19")
        val ergebnis = mergeUpcoming(heute, morgen, now, count = 3)
        assertEquals(listOf(Prayer.ISHA, Prayer.FAJR, Prayer.DHUHR), ergebnis.map { it.first })
        assertTrue(ergebnis.zip(ergebnis.drop(1)).all { (a, b) -> !a.second.isAfter(b.second) })
    }
}
