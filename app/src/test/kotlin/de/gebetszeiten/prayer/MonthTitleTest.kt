package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthTitleTest {
    @Test fun germanMonthAndYear() {
        assertEquals("Juni 2026", monthTitle(YearMonth.of(2026, 6)))
        assertEquals("Januar 2026", monthTitle(YearMonth.of(2026, 1)))
    }
}
