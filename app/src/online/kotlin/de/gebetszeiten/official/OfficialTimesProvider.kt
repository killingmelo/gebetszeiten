package de.gebetszeiten.official

import android.content.Context

/** Online flavor: official times come from Diyanet (direct, proxy fallback). */
object OfficialTimesProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher = CompositeDiyanetFetcher.create(context)
}
