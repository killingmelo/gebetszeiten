package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Amtliche Zeiten mit Quellen-Kette: Diyanet-Jahresseite direkt (ganzes
 * Jahr), bei Fehler/leer der Community-Proxy (31 Tage). Die Standort-ID
 * kommt aus dem DE-Bundle (id-genau), sonst aus dem Cache (letzter
 * erfolgreicher Abruf am selben Ort), sonst per Proxy-Namenssuche.
 * Wirft nie — leeres Ergebnis heißt: Aufrufer bleibt bei Bundle/Berechnung.
 */
class CompositeDiyanetFetcher(
    private val resolveId: suspend (AppSettings) -> Int?,
    private val direct: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val proxy: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val log: (String, Exception) -> Unit = { msg, e ->
        android.util.Log.w("DiyanetFetch", msg, e)
    },
) : OfficialTimesFetcher {

    override suspend fun fetch(settings: AppSettings): FetchResult {
        val id = try {
            resolveId(settings)
        } catch (e: Exception) {
            log("Standort-Aufloesung fehlgeschlagen", e)
            null
        } ?: return FetchResult(emptyMap(), null)
        val schedule = attempt("Direktabruf", id, direct)
            .ifEmpty { attempt("Proxy-Abruf", id, proxy) }
        return FetchResult(schedule, id.takeIf { schedule.isNotEmpty() })
    }

    private suspend fun attempt(
        label: String,
        id: Int,
        source: suspend (Int) -> Map<LocalDate, SixTimes>,
    ): Map<LocalDate, SixTimes> = try {
        source(id)
    } catch (e: Exception) {
        log("$label fehlgeschlagen (id=$id)", e)
        emptyMap()
    }

    companion object {
        fun create(context: Context): CompositeDiyanetFetcher {
            val proxyFetcher = DiyanetProxyFetcher()
            return CompositeDiyanetFetcher(
                resolveId = { settings ->
                    BundledOfficialSource.nearestLocation(context, settings.latitude, settings.longitude)?.diyanetId
                        ?: OfficialTimesCache(context).cachedLocationId(settings.latitude, settings.longitude)
                        ?: withContext(Dispatchers.IO) { proxyFetcher.resolveLocationId(settings.city) }
                },
                direct = DiyanetDirectFetcher()::fetchYear,
                proxy = proxyFetcher::fetchById,
            )
        }
    }
}
