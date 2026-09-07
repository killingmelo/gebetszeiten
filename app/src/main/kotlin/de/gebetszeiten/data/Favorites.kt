package de.gebetszeiten.data

import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches

/**
 * Dauerhaft gemerkte Orte — bewusst angelegt, im Gegensatz zu den flüchtigen
 * „zuletzt gewählten" Orten in `RecentPlaces.kt`. Beide Listen existieren
 * nebeneinander und wissen nichts voneinander; ein Ort darf in beiden stehen.
 * Serialisiert als Zeilen `name \t iso2 \t lat \t lng \t region \t addedEpochMs`.
 */
private const val SEP = '\t'

/** Obergrenze der Liste. Öffentlich, weil die Oberfläche sie kennen muss: nur
 *  so kann sie das stille Ablehnen von [withFavorite] in eine sichtbare
 *  Meldung übersetzen, statt den Stern wirkungslos verpuffen zu lassen. */
const val FAVORITES_MAX = 10

/** [addedEpochMs] liest heute niemand; es hält die Reihenfolge unabhängig von
 *  der Listenposition fest, damit spätere Umbauten sie nicht verlieren. */
data class Favorite(val city: City, val addedEpochMs: Long)

fun serializeFavorites(list: List<Favorite>): String =
    list.joinToString("\n") { f ->
        // Tabs im Namen würden das Format sprengen.
        listOf(
            f.city.name.replace("\t", ""),
            f.city.country,
            f.city.latitude.toString(),
            f.city.longitude.toString(),
            f.city.region.orEmpty().replace("\t", ""),
            f.addedEpochMs.toString(),
        ).joinToString(SEP.toString())
    }

fun parseFavorites(text: String?): List<Favorite> =
    text?.lineSequence()?.mapNotNull { line ->
        val c = line.split(SEP)
        if (c.size != 6) return@mapNotNull null
        val lat = c[2].toDoubleOrNull() ?: return@mapNotNull null
        val lng = c[3].toDoubleOrNull() ?: return@mapNotNull null
        val added = c[5].toLongOrNull() ?: return@mapNotNull null
        if (c[0].isBlank()) return@mapNotNull null
        Favorite(City(c[0], c[1], lat, lng, c[4].ifBlank { null }), added)
    }?.toList() ?: emptyList()

/** Identität über die Koordinaten, NICHT über den Namen: gleichnamige Orte
 *  gibt es wirklich (Esenköy in Yalova und in Aydın).
 *
 *  Verglichen wird mit `stampMatches` aus `core-prayertimes` — demselben
 *  Begriff von „derselbe Ort“, den der Zeiten-Cache benutzt (~1 km Toleranz).
 *  Zwei Gründe:
 *
 *  - Unterhalb von ~1 km liefert die App ohnehin dieselben Zeiten, sowohl über
 *    die amtliche Diyanet-Standort-ID als auch über die Berechnung. Zwei
 *    Favoriten zu führen, die sich nachweislich nicht unterscheiden können,
 *    wäre eine Unterscheidung ohne Unterschied — zwei Zeilen mit identischen
 *    Zeiten, die sich einen Cache-Eintrag teilen.
 *  - Der Nutzer denkt in Orten, nicht in Dezimalgraden. „Nürnberg ist
 *    gespeichert“ darf nicht davon abhängen, ob er Nürnberg über die Suche
 *    oder über die manuellen Koordinatenfelder ausgewählt hat; ein exakter
 *    `Double`-Vergleich ließe den Stern ohne erkennbaren Grund ausgehen.
 *
 *  `recentPlaces` bleibt bewusst beim exakten Vergleich: das ist eine Historie,
 *  kein Schlüssel in den Cache. */
private fun City.sameSpotAs(other: City): Boolean =
    stampMatches(latitude, longitude, other.latitude, other.longitude)

/** [added] hinten anhängen. Die Favoritenliste ist eine Ablage, keine Historie:
 *  ein schon vorhandener Favorit rutscht nicht nach vorn und behält seinen
 *  [Favorite.addedEpochMs]. Bei erreichtem [max] wird der neue Eintrag
 *  abgelehnt statt still einen alten zu verdrängen — was weg kann, entscheidet
 *  der Nutzer selbst. */
fun withFavorite(
    existing: List<Favorite>,
    added: City,
    nowEpochMs: Long,
    max: Int = FAVORITES_MAX,
): List<Favorite> =
    if (isFavorite(existing, added) || existing.size >= max) {
        existing
    } else {
        existing + Favorite(added, nowEpochMs)
    }

/** Entfernt GENAU EINEN Eintrag, nämlich den ersten am selben Ort wie
 *  [removed] (siehe `sameSpotAs`).
 *
 *  Bewusst nicht `filterNot`: die Toleranz von `stampMatches` ist nicht
 *  transitiv. Zwei Favoriten können 1,9 km auseinanderliegen — also zu Recht
 *  zwei Einträge sein — während ein Punkt genau dazwischen zu beiden passt.
 *  Ein `filterNot` würde dann auf einen Tipp hin BEIDE löschen. Ein Klick auf
 *  einen Stern darf niemals mehr als einen Favoriten kosten. */
fun withoutFavorite(existing: List<Favorite>, removed: City): List<Favorite> {
    val index = existing.indexOfFirst { it.city.sameSpotAs(removed) }
    return if (index < 0) existing else existing.filterIndexed { i, _ -> i != index }
}

fun isFavorite(list: List<Favorite>, city: City): Boolean =
    list.any { it.city.sameSpotAs(city) }
