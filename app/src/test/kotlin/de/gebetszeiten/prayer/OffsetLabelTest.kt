package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.TimeOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OffsetLabelTest {
    private val names = mapOf(
        Prayer.FAJR to "Faǧr", Prayer.SUNRISE to "Šurūq", Prayer.DHUHR to "Ẓuhr",
        Prayer.ASR to "ʿAṣr", Prayer.MAGHRIB to "Maġrib", Prayer.ISHA to "ʿIšāʾ",
    )

    @Test fun `Vorzeichen mit echtem Minus, null bei 0`() {
        assertEquals("+5", offsetMark(5))
        assertEquals("−3", offsetMark(-3))
        assertNull(offsetMark(0))
        // Kein Gedanken- oder Halbgeviertstrich (Schreibregel).
        assertFalse(offsetMark(-3)!!.contains('–'))
        assertFalse(offsetMark(-3)!!.contains('—'))
    }

    @Test fun `Zusammenfassung nennt nur Verschobenes, in Tagesreihenfolge`() {
        val o = TimeOffsets(maghrib = -3, dhuhr = 5)
        assertEquals("Von dir verschoben: Ẓuhr +5 Min, Maġrib −3 Min", offsetSummary(o, { names.getValue(it) }))
        assertNull(offsetSummary(TimeOffsets.NONE, { names.getValue(it) }))
    }
}
