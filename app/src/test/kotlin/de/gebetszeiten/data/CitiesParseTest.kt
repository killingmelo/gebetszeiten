package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CitiesParseTest {

    private val aliases = parseCityAliases(sequenceOf("Nürnberg\tNuremberg\tDE\t1"))

    @Test
    fun parsesSixColumnRowsWithRegion() {
        val entries = parseCities(
            sequenceOf(
                "Esenköy\tEsenkoey\tTR\t40.61695\t28.95713\tYalova",
                "kaputt\tzeile",
            ),
            aliases,
        )
        assertEquals(1, entries.size)
        val e = entries.single()
        assertEquals("Esenköy", e.name)
        assertEquals("Yalova", e.region)
        assertEquals("esenkoy", e.normName)
        // asciiname "Esenkoey" weicht ab → bleibt als eigenes Suchfeld erhalten.
        assertEquals("esenkoey", e.normAscii)
        assertEquals(40.61695, e.toCity().latitude, 1e-4)
    }

    @Test
    fun toleratesLegacyFiveColumnRows() {
        val entries = parseCities(sequenceOf("Berlin\tBerlin\tDE\t52.52\t13.40"), aliases)
        val e = entries.single()
        assertNull(e.region)
        // asciiname == name → kompaktiert zu null, Suche fällt auf normName zurück.
        assertNull(e.normAscii)
    }

    @Test
    fun aliasDisplayNameOnlyOnFirstOccurrence() {
        val entries = parseCities(
            sequenceOf(
                "Nuremberg\tNuremberg\tDE\t49.45\t11.08\tBayern",
                "Nuremberg\tNuremberg\tDE\t40.00\t-80.00\tPennsylvania",
            ),
            aliases,
        )
        assertEquals("Nürnberg", entries[0].name)
        assertEquals(listOf("nurnberg"), entries[0].normAliases)
        // Das zweite (kleinere) Nuremberg darf nicht umbenannt werden.
        assertEquals("Nuremberg", entries[1].name)
        assertTrue(entries[1].normAliases.isEmpty())
    }

    @Test
    fun searchPrefersPrefixOverSubstringAndHonorsLimit() {
        val entries = parseCities(
            sequenceOf(
                "Istanbul\tIstanbul\tTR\t41.01\t28.95\tİstanbul",
                "Esenköy\tEsenkoey\tTR\t40.62\t28.96\tYalova",
                "Esenler\tEsenler\tTR\t41.04\t28.88\tİstanbul",
                "Große Stadt\tGrosse Stadt\tDE\t50.0\t10.0\t",
            ),
            aliases,
        )
        val prefix = searchEntries(entries, "esen", limit = 12)
        assertEquals(listOf("Esenköy", "Esenler"), prefix.map { it.name })
        // Kein Präfix-Treffer → Substring-Fallback ("stanbul" in Istanbul).
        val substring = searchEntries(entries, "stanbul", limit = 12)
        assertEquals(listOf("Istanbul"), substring.map { it.name })
        assertEquals(1, searchEntries(entries, "esen", limit = 1).size)
        // Alias findet weiterhin ("nürnberg" → normalisiert "nurnberg").
        val alias = searchEntries(
            parseCities(sequenceOf("Nuremberg\tNuremberg\tDE\t49.45\t11.08\tBayern"), aliases),
            "nürn",
            limit = 12,
        )
        assertEquals(listOf("Nürnberg"), alias.map { it.name })
    }
}
