package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {

    // Wie im RecentPlacesTest: die Koordinaten leiten sich deterministisch vom
    // Namen ab — derselbe Name ergibt denselben Ort (nötig für die Dedup-Tests),
    // verschiedene Namen ergeben (praktisch immer) verschiedene Orte.
    private fun city(name: String, lat: Double = 40.0 + name.sumOf { it.code } / 1000.0, region: String? = "SAKARYA") =
        City(name, "TR", lat, 30.0, region)

    private fun fav(name: String, addedEpochMs: Long = 1_000L) = Favorite(city(name), addedEpochMs)

    // --- Serialisierung ---

    @Test fun `Rundreise durch Serialisierung erhaelt Reihenfolge und Zeitstempel`() {
        val list = listOf(fav("Nürnberg", 111L), fav("Regensburg", 222L), fav("İstanbul", 333L))
        assertEquals(list, parseFavorites(serializeFavorites(list)))
    }

    @Test fun `Rundreise ueberlebt fehlende Region und Sonderzeichen im Namen`() {
        val ohneRegion = Favorite(City("Ortsname", "TR", 1.5, 2.5, null), 42L)
        val sonderzeichen = Favorite(City("Kahramanmaraş · Çağlayancerit", "TR", 37.7, 37.3, "K. MARAŞ"), 43L)
        val list = listOf(ohneRegion, sonderzeichen)
        assertEquals(list, parseFavorites(serializeFavorites(list)))
    }

    @Test fun `null und Muell ergeben eine leere Liste`() {
        assertEquals(emptyList<Favorite>(), parseFavorites(null))
        assertEquals(emptyList<Favorite>(), parseFavorites(""))
        assertEquals(emptyList<Favorite>(), parseFavorites("kaputt\tzeile"))
    }

    @Test fun `Tabs im Ortsnamen und in der Region zerstoeren die Serialisierung nicht`() {
        val odd = Favorite(City("Bad\tName", "DE", 49.0, 11.0, "Bad\tRegion"), 7L)
        val parsed = parseFavorites(serializeFavorites(listOf(odd))).single()
        assertEquals("BadName", parsed.city.name)
        assertEquals("BadRegion", parsed.city.region)
        assertEquals(7L, parsed.addedEpochMs)
    }

    @Test fun `eine kaputte Zeile wird still verworfen, die uebrigen ueberleben`() {
        val gut = listOf(fav("Nürnberg", 111L), fav("Regensburg", 222L))
        val text = serializeFavorites(gut).lines().toMutableList().apply { add(1, "voellig\tkaputt") }
            .joinToString("\n")
        assertEquals(gut, parseFavorites(text))
    }

    @Test fun `eine Zeile mit unlesbarem Zeitstempel wird verworfen`() {
        val text = "Nürnberg\tDE\t49.45\t11.08\tBAYERN\tkeineZahl"
        assertEquals(emptyList<Favorite>(), parseFavorites(text))
    }

    // --- withFavorite: Ablage, keine Historie ---

    @Test fun `neuer Favorit kommt ans Ende`() {
        val result = withFavorite(listOf(fav("A"), fav("B")), city("C"), nowEpochMs = 999L)
        assertEquals(listOf("A", "B", "C"), result.map { it.city.name })
        assertEquals(999L, result.last().addedEpochMs)
    }

    @Test fun `derselbe Ort zweimal bleibt ein Eintrag mit dem urspruenglichen Zeitstempel`() {
        val start = listOf(fav("A", 100L), fav("B", 200L))
        val result = withFavorite(start, city("B"), nowEpochMs = 999L)
        assertEquals(start, result)
        assertEquals(200L, result.single { it.city.name == "B" }.addedEpochMs)
    }

    // --- Identität über Koordinaten, nicht über den Namen (Esenköy gibt es
    // in Yalova UND in Aydın — gleicher Name, verschiedene Orte). ---

    @Test fun `gleichnamige Orte mit verschiedenen Koordinaten sind zwei Favoriten`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        val result = withFavorite(listOf(Favorite(yalova, 100L)), aydin, nowEpochMs = 200L)
        assertEquals(2, result.size)
        assertEquals(listOf(Favorite(yalova, 100L), Favorite(aydin, 200L)), result)
    }

    @Test fun `volle Liste nimmt nichts Neues auf und verdraengt niemanden`() {
        val voll = (1..10).map { fav("Ort$it", it.toLong()) }
        val result = withFavorite(voll, city("Elfter"), nowEpochMs = 999L)
        assertEquals(voll, result)
    }

    @Test fun `bei voller Liste bleibt ein bereits vorhandener Favorit unveraendert`() {
        val voll = (1..10).map { fav("Ort$it", it.toLong()) }
        assertEquals(voll, withFavorite(voll, city("Ort3"), nowEpochMs = 999L))
    }

    // --- withoutFavorite / isFavorite ---

    @Test fun `withoutFavorite entfernt genau den einen ueber die Koordinaten`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        val start = listOf(Favorite(yalova, 100L), fav("Regensburg", 150L), Favorite(aydin, 200L))
        val result = withoutFavorite(start, aydin)
        assertEquals(listOf(Favorite(yalova, 100L), fav("Regensburg", 150L)), result)
    }

    @Test fun `withoutFavorite fuer einen Nicht-Favoriten aendert nichts`() {
        val start = listOf(fav("A", 100L), fav("B", 200L))
        assertEquals(start, withoutFavorite(start, city("C")))
    }

    @Test fun `isFavorite unterscheidet zwei gleichnamige Orte`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        val list = listOf(Favorite(yalova, 100L))
        assertTrue(isFavorite(list, yalova))
        assertFalse(isFavorite(list, aydin))
        assertFalse(isFavorite(emptyList(), yalova))
    }
}
