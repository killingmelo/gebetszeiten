package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WearCacheMigrationTest {

    // Echtes Format: von ScheduleText.serialize geschrieben (Leerzeichen-
    // getrennt) — der einzige Schreiber des alten Ein-Ort-Rumpfs war
    // WearCacheSync.push (Phone-Seite), immer ueber genau diese Funktion.
    // Ein tab-getrenntes Fixture wuerde ein Format testen, das nie entsteht.
    private val tag: LocalDate = LocalDate.parse("2026-09-12")
    private val zeiten = SixTimes(
        fajr = LocalTime.parse("04:53"),
        sunrise = LocalTime.parse("06:39"),
        dhuhr = LocalTime.parse("13:18"),
        asr = LocalTime.parse("16:50"),
        maghrib = LocalTime.parse("19:47"),
        isha = LocalTime.parse("21:18"),
    )
    private val altStand = ScheduleText.serialize(mapOf(tag to zeiten))

    @Test fun `alter Ein-Ort-Stand bekommt eine Kopfzeile`() {
        val neu = migrateLegacySchedule(altStand, 49.4521, 11.0767)
        assertNotNull(neu)
        assertTrue("Kopfzeile fehlt: $neu", neu!!.startsWith("#49.4521|11.0767|"))
        assertTrue("Die Tageszeile ist verloren gegangen", neu.contains(altStand.trim()))
    }

    @Test fun `migrierter Stand liefert am gespeicherten Ort die echten Zeiten zurueck`() {
        val neu = migrateLegacySchedule(altStand, 49.4521, 11.0767)
        val entries = CacheStore.split(neu)

        val treffer = CacheStore.select(entries, 49.4521, 11.0767)
        assertNotNull("Ortsabgleich hat den migrierten Eintrag nicht gefunden", treffer)
        assertEquals(zeiten, ScheduleText.parseDay(treffer!!.body, tag))
    }

    @Test fun `ein anderer Ort bekommt keine fremden Zeiten`() {
        val neu = migrateLegacySchedule(altStand, 49.4521, 11.0767)
        val entries = CacheStore.split(neu)

        // Istanbul liegt weit ausserhalb der ~1 km stampMatches-Toleranz.
        assertNull(CacheStore.select(entries, 41.0082, 28.9784))
    }

    @Test fun `ohne Stempel gibt es nichts zu uebernehmen`() {
        assertNull(migrateLegacySchedule(altStand, null, null))
    }

    @Test fun `leerer Altstand ergibt null`() {
        assertNull(migrateLegacySchedule(null, 49.4521, 11.0767))
    }
}
