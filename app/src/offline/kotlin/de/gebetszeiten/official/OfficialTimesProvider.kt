package de.gebetszeiten.official

import android.content.Context

/** Offline flavor: no online source — the app is provably network-free. */
object OfficialTimesProvider {
    const val isOnline = false
    fun fetcher(context: Context): OfficialTimesFetcher? = null
}
