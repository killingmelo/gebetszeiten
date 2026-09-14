package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.net.CompositeDiyanetFetcher
import java.time.LocalDate

/** Online flavor: official times come from Diyanet (direct, proxy fallback). */
object OfficialTimesProvider {
    const val isOnline = true

    /** `bundledLocationId` bleibt hier: das DE-Bundle (`BundledOfficialSource`)
     *  ist App-seitig, `:net-diyanet` kennt nur `:core-prayertimes`. */
    fun fetcher(context: Context): OfficialTimesFetcher = CompositeDiyanetFetcher.create(context) { lat, lng ->
        BundledOfficialSource.nearestLocation(context, lat, lng)?.diyanetId
    }

    /** Frisch geholte amtliche Zeiten zur Uhr replizieren (wirft nie). */
    suspend fun syncToWear(context: Context, schedule: Map<LocalDate, SixTimes>, settings: AppSettings) =
        WearCacheSync.create(context).push(schedule, settings.latitude, settings.longitude, settings.city)
}
