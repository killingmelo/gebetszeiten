package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPlacesTest {

    private fun city(name: String) = City(name, "TR", 40.0, 30.0, "SAKARYA")

    @Test fun `Rundreise durch Serialisierung erhaelt die Reihenfolge`() {
        val list = listOf(city("Serdivan"), city("Adapazarı"))
        assertEquals(list, parseRecentPlaces(serializeRecentPlaces(list)))
    }

    @Test fun `null und Muell ergeben eine leere Liste`() {
        assertEquals(emptyList<City>(), parseRecentPlaces(null))
        assertEquals(emptyList<City>(), parseRecentPlaces(""))
        assertEquals(emptyList<City>(), parseRecentPlaces("kaputt\tzeile"))
    }

    @Test fun `neuester Ort steht vorn`() {
        val result = withRecentPlace(listOf(city("A"), city("B")), city("C"))
        assertEquals(listOf("C", "A", "B"), result.map { it.name })
    }

    @Test fun `erneute Wahl dedupliziert statt zu verdoppeln`() {
        val result = withRecentPlace(listOf(city("A"), city("B")), city("B"))
        assertEquals(listOf("B", "A"), result.map { it.name })
    }

    @Test fun `laenger als max wird abgeschnitten`() {
        val start = listOf(city("A"), city("B"), city("C"), city("D"), city("E"))
        val result = withRecentPlace(start, city("F"))
        assertEquals(5, result.size)
        assertEquals("F", result.first().name)
        assertEquals(listOf("F", "A", "B", "C", "D"), result.map { it.name })
    }

    @Test fun `Tabs im Ortsnamen zerstoeren die Serialisierung nicht`() {
        val odd = City("Bad\tName", "DE", 49.0, 11.0, null)
        assertEquals(listOf("BadName"), parseRecentPlaces(serializeRecentPlaces(listOf(odd))).map { it.name })
    }
}
