package de.gebetszeiten.official

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.WearSyncContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Repliziert den amtlichen Zeiten-Cache als DataItem an die Uhr
 * (Zustands-Sync mit garantierter Zustellung, last-value-wins).
 * Wirft nie — ein fehlgeschlagener Sync ist folgenlos, der nächste
 * Refresh versucht es erneut. Leere Zeitpläne werden nie gepusht,
 * damit der letzte gute Stand auf der Uhr bleibt.
 */
class WearCacheSync(
    private val put: suspend (schedule: String, lat: Double, lng: Double, city: String, updatedEpochMs: Long?) -> Unit,
    private val log: (String, Exception) -> Unit = { msg, e ->
        android.util.Log.w("WearCacheSync", msg, e)
    },
) {

    /** [updatedEpochMs] ist der Zeitpunkt, zu dem DAS TELEFON [schedule]
     *  zuletzt erfolgreich abgerufen hat (`OfficialTimesCache.updatedEpochMs`
     *  am Aufrufer) — bewusst NICHT `System.currentTimeMillis()` an dieser
     *  Stelle: das waere der Sende-, nicht der Abrufzeitpunkt, und die Uhr
     *  braucht genau Letzteren fuer ihren Freshness-Vergleich (siehe
     *  `WearSyncContract.KEY_UPDATED`). */
    suspend fun push(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, city: String, updatedEpochMs: Long?) {
        if (schedule.isEmpty()) return
        try {
            put(ScheduleText.serialize(schedule), lat, lng, city, updatedEpochMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Wear-Sync fehlgeschlagen", e)
        }
    }

    companion object {
        fun create(context: Context) = WearCacheSync(
            put = { schedule, lat, lng, city, updatedEpochMs ->
                val request = PutDataMapRequest.create(WearSyncContract.PATH).apply {
                    dataMap.putString(WearSyncContract.KEY_SCHEDULE, schedule)
                    dataMap.putDouble(WearSyncContract.KEY_LAT, lat)
                    dataMap.putDouble(WearSyncContract.KEY_LNG, lng)
                    dataMap.putString(WearSyncContract.KEY_CITY, city)
                    // Fehlt bewusst, wenn null (siehe KDoc an
                    // `WearSyncContract.KEY_UPDATED`) — kein Platzhalterwert,
                    // der auf der Uhr wie ein echter Zeitstempel aussaehe.
                    if (updatedEpochMs != null) dataMap.putLong(WearSyncContract.KEY_UPDATED, updatedEpochMs)
                }.asPutDataRequest()
                withContext(Dispatchers.IO) {
                    Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
                }
            },
        )
    }
}
