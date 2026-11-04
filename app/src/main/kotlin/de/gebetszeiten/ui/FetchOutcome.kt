package de.gebetszeiten.ui

/**
 * Was nach „Jetzt abrufen“ gemeldet wird. Ohne diese Meldung lief der Abruf
 * stumm: kein Laden, kein Ergebnis, und „Heute“ las die Zeiten erst beim
 * nächsten Minutentakt neu.
 */
sealed interface FetchOutcome {
    /** Für den Ort liegen jetzt Zeiten vor. */
    data class Loaded(val place: String) : FetchOutcome

    /** Keine Zeiten bekommen; [reason] ist der Fehlergrund aus dem Cache, falls bekannt. */
    data class Failed(val reason: String?) : FetchOutcome
}

/** Reine Entscheidung, damit sie ohne Gerät prüfbar ist. */
internal fun fetchOutcome(hasTimesToday: Boolean, place: String, lastError: String?): FetchOutcome =
    if (hasTimesToday) {
        FetchOutcome.Loaded(place)
    } else {
        FetchOutcome.Failed(lastError?.trim()?.ifEmpty { null })
    }
