package de.gebetszeiten.core.prayertimes.officialtimes

/**
 * Der Kopf fuer einen GELUNGENEN Abruf an einem Ort, an dem [existing]
 * schon liegen kann (`null`, wenn nicht).
 *
 * Was zum ORT gehoert, wird uebernommen: das Versuchsprotokoll
 * (`lastAttemptEpochMs`, `lastError`) — frueher fasste `putAll` die
 * Versuchs-Schluessel ebenfalls nicht an, und `recordAttempt` schreibt es
 * unmittelbar danach ohnehin neu.
 *
 * Was zum ZEITPLAN gehoert, kommt vom neuen Abruf und wird ERSETZT: das
 * Prueferzeugnis. Es beschreibt den Abruf, der DIESE Zeiten brachte —
 * uebernaehme man das alte, klebte eine Pruefnotiz an einem Zeitplan, den
 * sie nie gesehen hat („bestätigt" ueber ungeprueft ersetzte Zeiten). Ein
 * Abruf ohne Zeugnis setzt es folgerichtig auf `null`.
 *
 * Reine Funktion, damit genau diese Unterscheidung im Test steht und nicht
 * nur im Kommentar. Liegt in `core`, damit App und Desktop dieselbe Regel
 * benutzen.
 */
fun refreshedHeader(
    existing: CacheHeader?,
    lat: Double,
    lng: Double,
    locationId: Int?,
    updatedEpochMs: Long,
    verification: Verification?,
) = newHeader(
    lat = lat,
    lng = lng,
    locationId = locationId,
    updatedEpochMs = updatedEpochMs,
    lastAttemptEpochMs = existing?.lastAttemptEpochMs,
    lastError = existing?.lastError,
    verification = verification,
)

/**
 * Zeitstempel und Fehlergrund eines Abrufversuchs fuer (lat,lng) in
 * [entries] eintragen — [error] = null heisst Erfolg. Das ist genau die
 * Transformation, die `OfficialTimesCache.recordAttempt` in ihrem
 * `update { }` ausfuehrt.
 *
 * Gibt es fuer den Ort einen Eintrag, wird nur dessen Kopf angefasst; der
 * Zeitplan bleibt unberuehrt. Sonst entsteht ein Eintrag mit leerem
 * Zeitplan, der die Statuszeile am Leben haelt und `DueLocation.hopeless`
 * traegt (Begruendung bei `OfficialTimesCache.recordAttempt`).
 *
 * [pinnedCoords] und [maxUnpinned] wie bei [CacheStore.put].
 */
fun CacheStore.withAttempt(
    entries: List<RawEntry>,
    error: String?,
    nowEpochMs: Long,
    lat: Double,
    lng: Double,
    pinnedCoords: List<Pair<Double, Double>>,
    maxUnpinned: Int,
): List<RawEntry> {
    // Index statt Referenzvergleich: dieselbe "nur der erste
    // Treffer"-Semantik wie `CacheStore.select`, aber ohne die
    // Annahme, dass genau dieses Listenelement zurueckkommt. Gaebe
    // `select` je einen kopierten Wert zurueck, taete diese Funktion
    // sonst stillschweigend nichts. Die Ortsidentitaet selbst steht
    // nur in `CacheStore` — hier nachgebaut, wuerde sie still
    // abdriften, sobald sie sich dort aendert.
    val index = indexOf(entries, lat, lng)
    if (index >= 0) {
        // Nur den Kopf anfassen — der Zeitplan bleibt unberuehrt.
        return entries.mapIndexed { i, entry ->
            if (i == index) {
                entry.copy(
                    header = entry.header.copy(lastAttemptEpochMs = nowEpochMs, lastError = error),
                )
            } else {
                entry
            }
        }
    }
    // `updatedEpochMs = nowEpochMs`, damit der frisch angelegte
    // Eintrag unter den leeren Eintraegen der juengste ist:
    // `put` haelt von denen nur den juengsten. Mit 0 flaege der
    // eben angelegte sofort selbst wieder raus, sobald schon ein
    // anderer leerer Eintrag da ist — und die Statuszeile haette
    // an diesem Ort weiterhin kein "Letzter Abruf"/"Fehler" zu
    // zeigen. Einen Jahresplan verdraengt er nicht: leere
    // Eintraege haben in `put` ihre eigene Grenze.
    return put(
        entries,
        CacheEntry(
            header = newHeader(
                lat = lat,
                lng = lng,
                locationId = null,
                updatedEpochMs = nowEpochMs,
                lastAttemptEpochMs = nowEpochMs,
                lastError = error,
            ),
            schedule = emptyMap(),
        ),
        pinnedCoords = pinnedCoords,
        maxUnpinned = maxUnpinned,
    )
}

/** firstDate/lastDate werden von [CacheStore] aus dem Zeitplan
 *  abgeleitet — hier bewusst null, damit sie nie doppelt gefuehrt sind.
 *  Dateiprivat: gepruefte Naht ist [refreshedHeader], nicht dieser
 *  Bausatz. */
private fun newHeader(
    lat: Double,
    lng: Double,
    locationId: Int?,
    updatedEpochMs: Long,
    lastAttemptEpochMs: Long?,
    lastError: String?,
    verification: Verification? = null,
) = CacheHeader(
    latitude = lat,
    longitude = lng,
    locationId = locationId,
    firstDate = null,
    lastDate = null,
    updatedEpochMs = updatedEpochMs,
    lastAttemptEpochMs = lastAttemptEpochMs,
    lastError = lastError,
    verification = verification,
)
