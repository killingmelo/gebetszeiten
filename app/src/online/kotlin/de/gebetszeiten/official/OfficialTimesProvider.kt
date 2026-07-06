package de.gebetszeiten.official

import android.content.Context

/** Online flavor: official times come from the Diyanet proxy. */
object OfficialTimesProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher = DiyanetProxyFetcher(context)
}
