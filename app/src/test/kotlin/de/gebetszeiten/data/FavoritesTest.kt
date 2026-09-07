package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {

    // Wie im RecentPlacesTest: die Koordinaten leiten sich deterministisch vom
    // Namen ab — derselbe Name ergibt denselben Ort (nötig für die Dedup-Tests).
    // Das Raster ist bewusst grob (0,05° ≈ 5,5 km): die Ortsidentität hat ~1 km
    // Toleranz (`stampMatches`), verschiedene Namen müssen also spürbar weiter
    // auseinanderliegen, sonst wären sie unbeabsichtigt derselbe Ort.
    // (n % 90, n / 90 % 90) ist für Summen unter 8100 eindeutig.
    private fun city(name: String, region: String? = "SAKARYA"): City {
        val n = name.sumOf { it.code }
        return City(name, "TR", 35.0 + (n % 90) * 0.05, 26.0 + (n / 90 % 90) * 0.05, region)
    }

    private fun fav(name: String, addedEpochMs: Long = 1_000L) = Favorite(city(name), addedEpochMs)

    private val nuernberg = City("Nürnberg", "DE", 49.4521, 11.0767, "BAYERN")

    /** ~500 m nördlich von [nuernberg] — z. B. über die manuellen Koordinatenfelder
     *  getippt. Für den Zeiten-Cache derselbe Ort, also auch hier. */
    private val nuernbergVerschoben = City("Nürnberg", "DE", 49.4566, 11.0767, "BAYERN")

    /** ~4,8 km von [nuernberg] entfernt — ein anderer Ort. */
    private val nuernbergNachbarort = City("Nachbarort", "DE", 49.4951, 11.0767, "BAYERN")

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

    /** Pinnt Reihenfolge UND Bedeutung der sechs Spalten: eine konsistente
     *  Vertauschung in Serialisierung und Parser bliebe sonst unbemerkt,
     *  obwohl sie vom dokumentierten Zeilenformat abweicht. */
    @Test fun `das Zeilenformat ist auf sechs Spalten in fester Reihenfolge gepinnt`() {
        val nbg = Favorite(City("Nürnberg", "DE", 49.45, 11.08, "BAYERN"), 7L)
        assertEquals("Nürnberg\tDE\t49.45\t11.08\tBAYERN\t7", serializeFavorites(listOf(nbg)))
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

    /** Sechs Spalten, aber kein Name: ein namenloser Chip wäre nicht anwählbar. */
    @Test fun `eine vollstaendige Zeile ohne Ortsnamen wird verworfen`() {
        assertEquals(emptyList<Favorite>(), parseFavorites("\tDE\t49.45\t11.08\tBAYERN\t7"))
        assertEquals(emptyList<Favorite>(), parseFavorites("   \tDE\t49.45\t11.08\tBAYERN\t7"))
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

    // --- Ortsidentität mit der Toleranz des Caches (~1 km, `stampMatches`) ---

    @Test fun `ein um 500 Meter verschobener Ort wird kein zweiter Favorit`() {
        val start = listOf(Favorite(nuernberg, 100L))
        val result = withFavorite(start, nuernbergVerschoben, nowEpochMs = 999L)
        assertEquals(start, result)
        assertTrue(isFavorite(result, nuernberg))
        assertTrue(isFavorite(result, nuernbergVerschoben))
    }

    @Test fun `zwei Orte fuenf Kilometer auseinander bleiben zwei Favoriten`() {
        val start = listOf(Favorite(nuernberg, 100L))
        assertFalse(isFavorite(start, nuernbergNachbarort))
        val result = withFavorite(start, nuernbergNachbarort, nowEpochMs = 999L)
        assertEquals(listOf(Favorite(nuernberg, 100L), Favorite(nuernbergNachbarort, 999L)), result)
    }

    @Test fun `withoutFavorite entfernt auch bei leicht verschobenen Koordinaten den richtigen`() {
        val start = listOf(Favorite(nuernberg, 100L), Favorite(nuernbergNachbarort, 200L))
        assertEquals(
            listOf(Favorite(nuernbergNachbarort, 200L)),
            withoutFavorite(start, nuernbergVerschoben),
        )
    }

    // --- Identität über Koordinaten, nicht über den Namen (Esenköy gibt es
    // in Yalova UND in Aydın — gleicher Name, 330 km auseinander). ---

    @Test fun `gleichnamige Orte mit verschiedenen Koordinaten sind zwei Favoriten`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        val result = withFavorite(listOf(Favorite(yalova, 100L)), aydin, nowEpochMs = 200L)
        assertEquals(2, result.size)
        assertEquals(listOf(Favorite(yalova, 100L), Favorite(aydin, 200L)), result)
    }

    // --- die Zehner-Grenze von beiden Seiten ---

    @Test fun `neun Favoriten plus einer ergibt zehn`() {
        val neun = (1..9).map { fav("Ort$it", it.toLong()) }
        val result = withFavorite(neun, city("Zehnter"), nowEpochMs = 999L)
        assertEquals(neun + Favorite(city("Zehnter"), 999L), result)
    }

    @Test fun `volle Liste nimmt nichts Neues auf und verdraengt niemanden`() {
        val voll = (1..10).map { fav("Ort$it", it.toLong()) }
        val result = withFavorite(voll, city("Elfter"), nowEpochMs = 999L)
        assertEquals(voll, result)
    }

    // --- withoutFavorite / isFavorite ---

    @Test fun `withoutFavorite entfernt genau den einen ueber die Koordinaten`() {
        val yalova = City("Esenköy", "TR", 40.65, 29.25, "YALOVA")
        val aydin = City("Esenköy", "TR", 37.66, 27.79, "AYDIN")
        val start = listOf(Favorite(yalova, 100L), fav("Regensburg", 150L), Favorite(aydin, 200L))
        val result = withoutFavorite(start, aydin)
        assertEquals(listOf(Favorite(yalova, 100L), fav("Regensburg", 150L)), result)
    }

    @Test fun `ein Stern-Klick kostet nie mehr als einen Favoriten`() {
        // Die Toleranz von stampMatches ist nicht transitiv: A und B liegen
        // 1,9 km auseinander (zu Recht zwei Favoriten), der Punkt genau
        // dazwischen passt mit je 0,94 km zu BEIDEN. Ein `filterNot` in
        // withoutFavorite würde hier auf einen Tipp hin beide löschen.
        val a = City("Nürnberg-West", "DE", 49.4521, 11.0767, "BAYERN")
        val b = City("Nürnberg-Ost", "DE", 49.4521, 11.1027, "BAYERN")
        val dazwischen = City("Irgendwo", "DE", 49.4521, 11.0897, "BAYERN")
        val start = listOf(Favorite(a, 100L), Favorite(b, 200L))
        assertTrue(isFavorite(start, dazwischen))
        assertEquals(listOf(Favorite(b, 200L)), withoutFavorite(start, dazwischen))
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
