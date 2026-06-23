package de.gebetszeiten.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatesTest {

    @Test fun validLatitudes() {
        assertFalse(Coordinates.latError("49.4521"))
        assertFalse(Coordinates.latError("0"))
        assertFalse(Coordinates.latError("-90"))
        assertFalse(Coordinates.latError("90"))
    }

    @Test fun invalidLatitudes() {
        assertTrue(Coordinates.latError("90.0001"))
        assertTrue(Coordinates.latError("-90.5"))
        assertTrue(Coordinates.latError("999"))
        assertTrue(Coordinates.latError("abc"))
        assertTrue(Coordinates.latError(""))
        assertTrue(Coordinates.latError("-"))
    }

    @Test fun whitespaceIsTrimmedBeforeParsing() {
        assertFalse(Coordinates.latError(" 90 "))
        assertFalse(Coordinates.lngError("  11.0767 "))
        assertTrue(Coordinates.latError(" abc "))
    }

    @Test fun validLongitudes() {
        assertFalse(Coordinates.lngError("11.0767"))
        assertFalse(Coordinates.lngError("-180"))
        assertFalse(Coordinates.lngError("180"))
    }

    @Test fun invalidLongitudes() {
        assertTrue(Coordinates.lngError("180.1"))
        assertTrue(Coordinates.lngError("-180.1"))
        assertTrue(Coordinates.lngError("-200"))
        assertTrue(Coordinates.lngError("xyz"))
        assertTrue(Coordinates.lngError(""))
    }

    @Test fun bothValidReflectsBothFields() {
        assertTrue(Coordinates.bothValid("49.4521", "11.0767"))
        assertFalse(Coordinates.bothValid("999", "11.0767"))
        assertFalse(Coordinates.bothValid("49.4521", "999"))
        assertFalse(Coordinates.bothValid("", ""))
    }
}
