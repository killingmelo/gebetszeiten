package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CityAliasesTest {

    @Test
    fun parsesAliasesNormalizedAndGrouped() {
        val map = parseCityAliases(
            sequenceOf(
                "Nürnberg\tNuremberg\tDE\t1",
                "München\tMunich\tDE\t1",
                "Münih\tMunich\tDE",
                "kaputt-ohne-tabs",
            ),
        )
        assertEquals(listOf("nurnberg"), map.getValue("Nuremberg|DE").normAliases)
        assertEquals("Nürnberg", map.getValue("Nuremberg|DE").displayName)
        assertEquals(listOf("munchen", "munih"), map.getValue("Munich|DE").normAliases)
        assertEquals("München", map.getValue("Munich|DE").displayName)
        assertEquals(2, map.size)
    }

    /** Jeder Alias im committeten Asset muss auf eine existierende
     *  (Name, Land)-Zeile in cities.tsv zeigen — fängt Tippfehler und
     *  Datenbank-Umbenennungen (z. B. GeoNames-Updates). */
    @Test
    fun everyBundledAliasResolvesToAKnownCity() {
        val known = File("src/main/assets/cities.tsv").useLines { lines ->
            lines.mapNotNull { line ->
                val c = line.split('\t')
                if (c.size < 5) null else "${c[0]}|${c[2]}"
            }.toHashSet()
        }
        val unresolved = File("src/main/assets/city-aliases.tsv").useLines { lines ->
            lines.filter { it.isNotBlank() }.mapNotNull { line ->
                val c = line.split('\t')
                if (c.size < 3) return@mapNotNull "defekte Zeile: $line"
                if ("${c[1]}|${c[2]}" !in known) line else null
            }.toList()
        }
        assertTrue("Aliasse ohne Ziel in cities.tsv: $unresolved", unresolved.isEmpty())
    }
}
