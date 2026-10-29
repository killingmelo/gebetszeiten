package de.gebetszeiten.desktop.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReadingsTest {

    private val tsv = listOf(
        "anlass\tarabisch\tdeutsch\tquelle\tbuch_id\tseite\thinweis",
        "cuma\tنص ١\tText 1 ¶ zweiter Absatz\tQuelle A\t1\t2\t",
        "cuma\tنص ٢\tText 2\tQuelle B\t1\t3\tṣaḥīḥ",
        "kaputt",
        "berat\t\t\tohne Text\t1\t1\t",
    )

    @Test
    fun parsesAndSkipsBrokenLines() {
        val r = Readings.parse(tsv)
        assertEquals(2, r.size)
        val first = r.pick(Occasion.CUMA, LocalDate.of(2026, 6, 12))!!
        assertEquals(true, first.german.contains("\n") || first.note == "ṣaḥīḥ")
        assertNull(r.pick(Occasion.BERAT, LocalDate.of(2026, 6, 12)))
    }

    @Test
    fun rotatesDailyStableWithinDay() {
        val r = Readings.parse(tsv)
        val d = LocalDate.of(2026, 6, 12)
        assertEquals(r.pick(Occasion.CUMA, d), r.pick(Occasion.CUMA, d))
        assertNotEquals(r.pick(Occasion.CUMA, d), r.pick(Occasion.CUMA, d.plusDays(1)))
    }

    @Test
    fun honorificLigaturesAreSpelledOut() {
        val r = Readings.parse(listOf("h", "cuma\tقال أبو هريرة ﵁ قال ﷺ\tx\tq\t1\t1\t"))
        assertEquals("قال أبو هريرة رضي الله عنه قال ﷺ", r.pick(Occasion.CUMA, LocalDate.of(2026, 1, 1))!!.arabic)
    }

    @Test
    fun bundledCollectionCoversEveryOccasion() {
        val r = Readings.load { javaClass.classLoader.getResourceAsStream(it) }
        val d = LocalDate.of(2026, 1, 1)
        for (o in Occasion.entries) {
            val reading = r.pick(o, d)
            assertNotEquals("kein Text fuer $o", null, reading)
            assertEquals("Ligatur in $o", -1, reading!!.arabic.indexOfFirst { it in '﵀'..'﵏' || it == '﷿' })
        }
    }

    @Test
    fun missingResourceIsEmpty() {
        assertEquals(0, Readings.load { null }.size)
    }
}
