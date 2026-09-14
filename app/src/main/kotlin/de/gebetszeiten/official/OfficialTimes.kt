package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.SourceResult
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import java.time.LocalDate

/**
 * Fetches exact official Diyanet times for a location. Only the `online`
 * product flavor provides a real implementation; the `offline` flavor supplies
 * none (see the flavor-specific [OfficialTimesProvider]).
 */
interface OfficialTimesFetcher {
    /** Zeiten für so viele Tage, wie die Quelle hergibt, plus die Diyanet-ID,
     *  mit der sie geholt wurden (wird für Folge-Refreshes persistiert).
     *
     *  Vier einfache Parameter statt eines `AppSettings` — die Netzschicht
     *  darf keinen app-Typ kennen (Vorbereitung des Modulschnitts).
     *  [preferredLocationId] ist die zuletzt bekannte Diyanet-ID fuer
     *  [lat]/[lng] (typischerweise aus dem Cache des Aufrufers); sie zaehlt
     *  in der Aufloesungskette NACH Bundle und Koordinatenindex, aber VOR
     *  der Namenssuche. */
    suspend fun fetch(
        lat: Double,
        lng: Double,
        city: String,
        preferredLocationId: Int?,
    ): FetchResult
}

/**
 * Was ein Abruf ergeben hat.
 *
 * [verification] wandert in den Cache-Kopf (`OfficialTimesCache.putAll`) und
 * von dort in die Statuszeile; [errorSummary] wird zum `lastError`, wenn
 * KEINE Quelle etwas geliefert hat — auf dem Erfolgspfad ausdruecklich
 * nicht, ein gelungener Abruf ist kein Fehler (siehe
 * `PrayerProvider.refreshOfficial`).
 *
 * [Verification] ist ein reiner Datentyp aus `core-prayertimes` (kein Netz,
 * keine Uhr, kein Android) — deshalb darf er hier im geteilten Quellsatz
 * stehen, ohne den `NoNetworkInSharedCodeTest` zu verletzen.
 */
data class FetchResult(
    val schedule: Map<LocalDate, SixTimes>,
    val locationId: Int?,
    /** Das Prueferzeugnis zum gelieferten Zeitplan; `null`, wenn gar nicht
     *  erst abgerufen wurde (kein aufloesbarer Standort). */
    val verification: Verification? = null,
    /** Was schiefging, Quelle fuer Quelle; `null`, wenn keine scheiterte.
     *  Wird vom Fetcher selbst nicht mehr befuellt (das braeuchte
     *  `fetchErrorSummary` aus dem app-Modul) — der Aufrufer baut den Text
     *  aus [candidates]. Bleibt als Feld erhalten, damit die Schnittstelle
     *  unveraendert bleibt. */
    val errorSummary: String? = null,
    /** Jeder Quellenversuch, Erfolg wie Fehlschlag — Rohmaterial, aus dem
     *  der Aufrufer (z. B. `PrayerProvider.refreshOfficial`) den deutschen
     *  Fehlertext baut. */
    val candidates: List<SourceResult> = emptyList(),
)
