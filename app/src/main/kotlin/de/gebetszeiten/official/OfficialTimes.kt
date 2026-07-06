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
    /** Returns date → times for as many upcoming days as the source offers. */
    suspend fun fetch(settings: AppSettings): Map<LocalDate, SixTimes>
}
