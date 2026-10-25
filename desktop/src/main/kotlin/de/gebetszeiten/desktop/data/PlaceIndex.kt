package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import java.io.IOException
import java.io.InputStream

/**
 * Der Ortsindex fuer die Ortssuche: `official/locations-world.tsv`, dessen
 * deutsche Orte (gleiche diyanetId in `official/locations-de.tsv`) den dortigen
 * deutschen Namen tragen („Nürnberg" statt „NURNBERG"). Fehlt eine Ressource,
 * ist die Liste entsprechend leer bzw. ungemischt, kein Absturz.
 */
fun loadPlaces(open: (String) -> InputStream?): List<DiyanetPlace> {
    fun <T> read(path: String, parse: (Sequence<String>) -> List<T>): List<T> = try {
        open(path)?.bufferedReader(Charsets.UTF_8)?.useLines { parse(it) }.orEmpty()
    } catch (e: IOException) {
        emptyList()
    }
    return withGermanNames(
        read("official/locations-world.tsv") { parseDiyanetPlaces(it) },
        read("official/locations-de.tsv") { parseOfficialLocations(it) },
    )
}

/** Ersetzt den Namen jedes Orts, dessen diyanetId in [de] steht, durch den deutschen. */
fun withGermanNames(places: List<DiyanetPlace>, de: List<OfficialLocation>): List<DiyanetPlace> {
    val names = de.associate { it.diyanetId to it.name }
    return places.map { p -> names[p.diyanetId]?.let { p.copy(name = it) } ?: p }
}
