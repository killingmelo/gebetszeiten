package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPlacesTest {

    // Koordinaten leiten sich deterministisch vom Namen ab: derselbe Name
    // liefert immer dieselben Koordinaten (nötig für die Dedup-Tests),
    // verschiedene Namen liefern (praktisch immer) verschiedene Koordinaten
    // (nötig für die Namensgleichheit-Tests) — ohne dass jeder Aufruf sie
    // einzeln ausschreiben muss.
    private fun city(name: String, lat: Double = 40.0 + name.sumOf { it.code } / 1000.0, region: String? = "SAKARYA") =
        City(name, "TR", lat, 30.0, region)

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

    @Test fun `Tabs im Ortsnamen und in der Region zerstoeren die Serialisierung nicht`() {
        val odd = City("Bad\tName", "DE", 49.0, 11.0, "Bad\tRegion")
        val parsed = parseRecentPlaces(serializeRecentPlaces(listOf(odd))).single()
        assertEquals("BadName", parsed.name)
        assertEquals("BadRegion", parsed.region)
    }

    // --- Identität über Koordinaten, nicht über den Namen (Esenköy gibt es
    // in Yalova UND in Aydın — gleicher Name, verschiedene Orte). ---

    @Test fun `gleichnamige Orte mit verschiedenen Koordinaten bleiben beide erhalten`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        val result = withRecentPlace(listOf(yalova), aydin)
        assertEquals(2, result.size)
        assertEquals(listOf(aydin, yalova), result)
    }

    @Test fun `derselbe Ort erneut gewaehlt verdoppelt sich nicht, auch bei gleichnamigem Ort in der Liste`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        // aydin steht schon vorn drin; yalova wird erneut gewählt (gleiche
        // Koordinaten wie der vorhandene Eintrag) -> rutscht nach vorn,
        // aydin bleibt unangetastet erhalten statt verdoppelt zu werden.
        val result = withRecentPlace(listOf(aydin, yalova), yalova)
        assertEquals(listOf(yalova, aydin), result)
    }

    // --- Chip-Beschriftung: Name reicht, ausser er ist mehrdeutig. ---

    @Test fun `Label ist der blosse Name wenn er eindeutig ist`() {
        val all = listOf(city("Serdivan"), city("Adapazarı"))
        assertEquals("Serdivan", recentPlaceLabel(all[0], all))
    }

    @Test fun `Label bekommt die Region wenn der Name mehrdeutig ist`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        val all = listOf(yalova, aydin)
        assertEquals("Esenköy · YALOVA", recentPlaceLabel(yalova, all))
        assertEquals("Esenköy · AYDIN", recentPlaceLabel(aydin, all))
    }

    @Test fun `Label bleibt der blosse Name wenn mehrdeutig aber ohne Region`() {
        val a = City("Ortsname", "TR", 1.0, 1.0, null)
        val b = City("Ortsname", "TR", 2.0, 2.0, null)
        assertEquals("Ortsname", recentPlaceLabel(a, listOf(a, b)))
    }
}
