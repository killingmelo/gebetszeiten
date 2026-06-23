package de.gebetszeiten.prayer

import de.gebetszeiten.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DataCreditTest {
    @Test fun officialPicksOfficialString() {
        assertEquals(R.string.data_credit_official, dataCreditRes(official = true))
    }
    @Test fun calculatedPicksCalculatedString() {
        assertEquals(R.string.data_credit_calculated, dataCreditRes(official = false))
    }
}
