package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.TextNormalize
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace

/**
 * Ortssuche im Diyanet-Ortsindex. Vergleich ueber [TextNormalize] (Umlaute,
 * tuerkische Zeichen, Gross/klein egal): „nurnberg" findet NUERNBERG, „isparta"
 * findet ISPARTA. Treffer am Anfang des Namens oder eines Wortes kommen vor
 * Treffern mittendrin, jede Gruppe alphabetisch. Unter zwei Zeichen: leer.
 */
fun searchPlaces(all: List<DiyanetPlace>, query: String, limit: Int = 20): List<DiyanetPlace> {
    val q = TextNormalize.normalize(query)
    if (q.length < 2) return emptyList()
    class Hit(val place: DiyanetPlace, val name: String, val prefix: Boolean)
    val hits = ArrayList<Hit>()
    for (p in all) {
        val n = TextNormalize.normalize(p.name)
        val at = n.indexOf(q)
        if (at < 0) continue
        val wordStart = at == 0 || !n[at - 1].isLetterOrDigit()
        hits += Hit(p, n, wordStart)
    }
    return hits
        .sortedWith(compareBy<Hit>({ !it.prefix }, { it.name }, { it.place.province }))
        .take(limit)
        .map { it.place }
}
