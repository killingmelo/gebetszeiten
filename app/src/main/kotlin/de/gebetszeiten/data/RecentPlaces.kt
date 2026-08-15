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

/** [added] nach vorn, Duplikate (Name + Land) entfernt, auf [max] gekürzt. */
fun withRecentPlace(existing: List<City>, added: City, max: Int = 5): List<City> =
    (listOf(added) + existing.filterNot { it.name == added.name && it.country == added.country })
        .take(max)
