package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate

/** Offline flavor: no online source — the app is provably network-free. */
object OfficialTimesProvider {
    const val isOnline = false
    fun fetcher(context: Context): OfficialTimesFetcher? = null

    /** Offline gibt es nichts zu syncen (kein Online-Cache, kein gms). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun syncToWear(context: Context, schedule: Map<LocalDate, SixTimes>, settings: AppSettings) = Unit
}
