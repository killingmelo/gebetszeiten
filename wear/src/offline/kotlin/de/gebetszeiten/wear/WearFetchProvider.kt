package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher

/** Offline-Flavor der Uhr: kein Netzcode, beweisbar. */
object WearFetchProvider {
    const val isOnline = false
    @Suppress("UNUSED_PARAMETER")
    fun fetcher(context: Context): OfficialTimesFetcher? = null
}
