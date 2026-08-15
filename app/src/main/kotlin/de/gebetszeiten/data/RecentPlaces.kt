package de.gebetszeiten.data

/**
 * Zuletzt gewählte Orte, damit ein Ortswechsel keinen Tastendruck kostet.
 * Serialisiert als Zeilen `name \t iso2 \t lat \t lng \t region` — geordnet,
 * anders als ein `stringSetPreferencesKey`.
 */
private const val SEP = '\t'

fun serializeRecentPlaces(places: List<City>): String =
    places.joinToString("\n") {
        // Tabs im Namen würden das Format sprengen.
        listOf(
            it.name.replace("\t", ""),
            it.country,
            it.latitude.toString(),
            it.longitude.toString(),
            it.region.orEmpty().replace("\t", ""),
        ).joinToString(SEP.toString())
    }

fun parseRecentPlaces(text: String?): List<City> =
    text?.lineSequence()?.mapNotNull { line ->
        val c = line.split(SEP)
        if (c.size != 5) return@mapNotNull null
        val lat = c[2].toDoubleOrNull() ?: return@mapNotNull null
        val lng = c[3].toDoubleOrNull() ?: return@mapNotNull null
        if (c[0].isBlank()) return@mapNotNull null
        City(c[0], c[1], lat, lng, c[4].ifBlank { null })
    }?.toList() ?: emptyList()

/** [added] nach vorn, Duplikate entfernt, auf [max] gekürzt.
 *  Identität über die Koordinaten, NICHT über den Namen: gleichnamige
 *  Orte gibt es wirklich (Esenköy in Yalova und in Aydın). */
fun withRecentPlace(existing: List<City>, added: City, max: Int = 5): List<City> =
    (listOf(added) + existing.filterNot {
        it.latitude == added.latitude && it.longitude == added.longitude
    }).take(max)

/** Chip-Beschriftung: der bloße Name, außer ein anderer Eintrag der Liste
 *  heißt genauso — dann mit Region, damit die Chips unterscheidbar bleiben. */
fun recentPlaceLabel(place: City, all: List<City>): String =
    if (all.count { it.name == place.name } > 1 && !place.region.isNullOrBlank()) {
        "${place.name} · ${place.region}"
    } else {
        place.name
    }
