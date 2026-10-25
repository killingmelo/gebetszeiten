package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class PlaceSearchTest {

    private fun place(id: Int, name: String, province: String = "X", cc: String = "DE") =
        DiyanetPlace(id, name, province, cc, 50.0, 10.0)

    private val places = listOf(
        place(1, "NÜRNBERG", "BAYERN"),
        place(2, "STARNBERG", "BAYERN"),
        place(3, "ISPARTA", "ISPARTA", "TR"),
        place(4, "ŞİŞLİ", "İSTANBUL", "TR"),
        place(5, "BERLIN", "BERLIN"),
    )

    @Test
    fun findsNuernbergWithoutUmlaut() {
        val hits = searchPlaces(places, "nurnberg").map { it.displayName() }
        assertTrue(hits.toString(), "Nürnberg" in hits)
    }

    @Test
    fun turkishDotlessI() {
        assertEquals(listOf("Isparta"), searchPlaces(places, "isparta").map { it.displayName() })
        assertEquals(listOf("Isparta"), searchPlaces(places, "ISPARTA").map { it.displayName() })
        assertEquals(listOf("Isparta"), searchPlaces(places, "ısparta").map { it.displayName() })
        assertEquals(listOf(4), searchPlaces(places, "sisli").map { it.diyanetId })
    }

    @Test
    fun prefixBeforeInfix() {
        // „nberg" steht in beiden mittendrin; „ber" am Anfang nur bei Berlin.
        val hits = searchPlaces(places, "ber").map { it.diyanetId }
        assertEquals(5, hits.first())
        assertTrue(hits.containsAll(listOf(1, 2)))
        // Beide Gruppen alphabetisch: Nürnberg vor Starnberg.
        assertEquals(listOf(5, 1, 2), hits)
    }

    @Test
    fun wordStartCountsAsPrefix() {
        val list = listOf(place(1, "BAD AIBLING"), place(2, "KAIBLING"))
        assertEquals(listOf(1, 2), searchPlaces(list, "aib").map { it.diyanetId })
    }

    @Test
    fun shortQueryEmpty() {
        assertTrue(searchPlaces(places, "").isEmpty())
        assertTrue(searchPlaces(places, "n").isEmpty())
        assertTrue(searchPlaces(places, " n ").isEmpty())
    }

    @Test
    fun limitApplies() {
        val many = (1..50).map { place(it, "ORT$it") }
        assertEquals(20, searchPlaces(many, "ort").size)
        assertEquals(5, searchPlaces(many, "ort", limit = 5).size)
    }

    private val bs = 92.toChar() // Rueckstrich, ohne Escape-Aerger

    private fun win(vararg parts: String) = parts.joinToString(bs.toString())

    @Test
    fun autostartRunValueIsQuotedPath() {
        val exe = win("C:", "Programme", "Gebetszeiten", "Gebetszeiten.exe")
        assertEquals("\"$exe\"", autostartRunValue(Path.of(exe)))
    }

    @Test
    fun onlyPackagedExeCountsAsInstalled() {
        assertTrue(isPackagedExe(win("C:", "Users", "x", "AppData", "Local", "Gebetszeiten", "Gebetszeiten.exe")))
        assertTrue(isPackagedExe(win("c:", "gebetszeiten.EXE")))
        assertFalse(isPackagedExe(win("C:", "Program Files", "Java", "bin", "java.exe")))
        assertFalse(isPackagedExe(null))
    }
}

class PlaceIndexTest {
    private val deList = listOf(
        OfficialLocation(11024, "N" + 252.toChar() + "rnberg", 49.45, 11.08, "t0"),
    )

    @Test
    fun germanNameReplacesAssetName() {
        val places = listOf(
            DiyanetPlace(11024, "NURNBERG", "BAYERN", "DE", 49.45, 11.08),
            DiyanetPlace(9541, "ISPARTA", "ISPARTA", "TR", 37.76, 30.55),
        )
        val merged = withGermanNames(places, deList)
        assertEquals("N" + 252.toChar() + "rnberg", merged[0].name)
        assertEquals("N" + 252.toChar() + "rnberg", merged[0].displayName())
        assertEquals(places[1], merged[1])
        // Die Suche findet den Umlaut-Namen weiter ohne Umlaut.
        assertEquals(listOf(11024), searchPlaces(merged, "nurnberg").map { it.diyanetId })
    }
}
