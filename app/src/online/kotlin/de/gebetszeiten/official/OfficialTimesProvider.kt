package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate

/** Online flavor: official times come from Diyanet (direct, proxy fallback). */
object OfficialTimesProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher = CompositeDiyanetFetcher.create(context)

    /** Frisch geholte amtliche Zeiten zur Uhr replizieren (wirft nie). */
    suspend fun syncToWear(context: Context, schedule: Map<LocalDate, SixTimes>, settings: AppSettings) =
        WearCacheSync.create(context).push(schedule, settings.latitude, settings.longitude, settings.city)
}
