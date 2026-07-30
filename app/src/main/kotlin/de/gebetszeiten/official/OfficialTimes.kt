package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate

/**
 * Fetches exact official Diyanet times for a location. Only the `online`
 * product flavor provides a real implementation; the `offline` flavor supplies
 * none (see the flavor-specific [OfficialTimesProvider]).
 */
interface OfficialTimesFetcher {
    /** Zeiten für so viele Tage, wie die Quelle hergibt, plus die Diyanet-ID,
     *  mit der sie geholt wurden (wird für Folge-Refreshes persistiert). */
    suspend fun fetch(settings: AppSettings): FetchResult
}

data class FetchResult(
    val schedule: Map<LocalDate, SixTimes>,
    val locationId: Int?,
)
