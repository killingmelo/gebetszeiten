package de.gebetszeiten.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Prüft das echte gebündelte Asset — insbesondere den Anlass der
 *  cities500-Erweiterung: Esenköy (Yalova) muss offline findbar sein. */
class CitiesAssetTest {

    private val entries by lazy {
        val aliases = File("src/main/assets/city-aliases.tsv").useLines { parseCityAliases(it) }
        File("src/main/assets/cities.tsv").useLines { parseCities(it, aliases) }
    }

    private fun assertFindable(query: String, country: String, lat: Double, lng: Double, tol: Double = 0.15) {
        val hits = searchEntries(entries, query, limit = 12)
        assertTrue(
            "'$query' → ${hits.map { "${it.name}/${it.country}" }} enthält keinen Treffer bei $lat/$lng",
            hits.any { it.country == country && abs(it.latitude - lat) <= tol && abs(it.longitude - lng) <= tol },
        )
    }

    @Test fun esenkoyYalovaIsFindable() {
        assertFindable("esenköy", "TR", 40.617, 28.957)
        assertFindable("Esenkoy", "TR", 40.617, 28.957)
    }

    @Test fun cinarcikIsFindable() {
        assertFindable("çınarcık", "TR", 40.643, 29.121)
    }

    @Test fun majorCitiesStillRankFirst() {
        // Populationssortierung: die Metropole muss vor gleichnamigen
        // Kleinorten kommen (Asset-Reihenfolge = Rang).
        assertTrue(searchEntries(entries, "istanbul", 12).first().let {
            it.country == "TR" && abs(it.latitude - 41.01) < 0.3
        })
        assertTrue(searchEntries(entries, "nürnberg", 12).first().name == "Nürnberg")
    }

    @Test fun assetIsSubstantial() {
        assertTrue("nur ${entries.size} Orte — cities500-Extrakt erwartet", entries.size > 200_000)
    }
}
