package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HijriTextTest {
    @Test fun usesInternationalTransliterationForDhulHijja() {
        assertEquals("23. Dhul-Hijja 1447", hijriText(LocalDate.of(2026, 6, 9), 0))
    }

    @Test fun shortFormDropsYear() {
        assertEquals("23. Dhul-Hijja", hijriTextShort(LocalDate.of(2026, 6, 9), 0))
    }

    @Test fun offsetShiftsDay() {
        assertTrue(hijriText(LocalDate.of(2026, 6, 9), -1).startsWith("22. Dhul-Hijja"))
    }
}
