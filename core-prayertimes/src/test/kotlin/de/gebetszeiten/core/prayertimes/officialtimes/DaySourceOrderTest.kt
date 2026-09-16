package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Quellenreihenfolge war bis hierhin von KEINEM Test gedeckt: sie stand
 * im Rumpf von PrayerProvider.daily, und der braucht einen Context.
 */
class DaySourceOrderTest {

    @Test fun `online an, Notausgang aus - Cache, dann Bundle, keine Berechnung`() {
        assertEquals(
            listOf(DaySource.ONLINE_CACHE, DaySource.BUNDLED_TABLE),
            daySourceOrder(useOnline = true, calculationFillsGaps = false),
        )
    }

    @Test fun `online an, Notausgang an - die Berechnung kommt ZULETZT`() {
        assertEquals(
            listOf(DaySource.ONLINE_CACHE, DaySource.BUNDLED_TABLE, DaySource.CALCULATION),
            daySourceOrder(useOnline = true, calculationFillsGaps = true),
        )
    }

    @Test fun `online aus - der Cache wird nicht einmal gefragt`() {
        assertEquals(
            listOf(DaySource.BUNDLED_TABLE),
            daySourceOrder(useOnline = false, calculationFillsGaps = false),
        )
    }

    @Test fun `online aus, Notausgang an - Bundle vor Berechnung`() {
        assertEquals(
            listOf(DaySource.BUNDLED_TABLE, DaySource.CALCULATION),
            daySourceOrder(useOnline = false, calculationFillsGaps = true),
        )
    }

    @Test fun `amtliche Quellen stehen IMMER vor der Berechnung`() {
        listOf(true, false).forEach { online ->
            val order = daySourceOrder(useOnline = online, calculationFillsGaps = true)
            assertEquals("Die Berechnung muss der letzte Eintrag sein", DaySource.CALCULATION, order.last())
        }
    }
}
