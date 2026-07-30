package de.gebetszeiten.wear

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import de.gebetszeiten.core.prayertimes.officialtimes.WearSyncContract
import kotlinx.coroutines.runBlocking

/**
 * Empfängt den amtlichen Zeiten-Cache vom Handy (DataItem
 * [WearSyncContract.PATH]), materialisiert ihn in [WearOfficialCache]
 * und stößt Tile, Complication und Vibrations-Kette an. Dünner
 * IO-Wrapper — die Logik steckt in [SyncDecision] (JVM-getestet).
 * runBlocking ist hier ok: onDataChanged läuft auf einem
 * Binder-Hintergrund-Thread (gleiches Muster wie CityPickerActivity).
 */
class WearSyncListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != WearSyncContract.PATH) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val payload = SyncDecision.Payload(
                scheduleText = map.getString(WearSyncContract.KEY_SCHEDULE) ?: continue,
                lat = map.getDouble(WearSyncContract.KEY_LAT),
                lng = map.getDouble(WearSyncContract.KEY_LNG),
                city = map.getString(WearSyncContract.KEY_CITY) ?: continue,
            )
            runBlocking { apply(payload) }
        }
    }

    private suspend fun apply(payload: SyncDecision.Payload) {
        if (SyncDecision.parse(payload) == null) return
        val synced = WearOfficialCache.syncedLocation(applicationContext)
        val adopt = SyncDecision.shouldAdoptLocation(payload, synced?.first, synced?.second)
        WearOfficialCache.store(applicationContext, payload.scheduleText, payload.lat, payload.lng, adopt)
        if (adopt) WearSettings.save(applicationContext, payload.city, payload.lat, payload.lng)
        // Neue Zeiten/neuer Ort: Vibrations-Kette neu armieren, Tile und
        // Complication einmalig auffrischen (danach wieder Zero-Wakeup).
        WearVibration.reschedule(applicationContext)
        TileService.getUpdater(this).requestUpdate(PrayerTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, PrayerComplicationService::class.java))
            .requestUpdateAll()
    }
}
