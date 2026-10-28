package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HijriTextTest {
    @Test fun usesDmgTransliterationForDhuLHigga() {
        assertEquals("23. Ḏū l-Ḥiǧǧa 1447", hijriText(LocalDate.of(2026, 6, 9), 0))
    }

    @Test fun shortFormDropsYear() {
        assertEquals("23. Ḏū l-Ḥiǧǧa", hijriTextShort(LocalDate.of(2026, 6, 9), 0))
    }

    @Test fun jumadaUlaInDmg() {
        assertEquals("17. Ǧumādā l-ūlā 1448", hijriText(LocalDate.of(2026, 10, 28), 0))
    }

    @Test fun offsetShiftsDay() {
        assertTrue(hijriText(LocalDate.of(2026, 6, 9), -1).startsWith("22. Ḏū l-Ḥiǧǧa"))
    }
}
