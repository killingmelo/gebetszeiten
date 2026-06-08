package de.gebetszeiten.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/** Receives the location pushed by the phone and persists it on the watch. */
class LocationSyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val lat = map.getDouble("lat")
            val lng = map.getDouble("lng")
            // onDataChanged runs off the main thread, so blocking here is fine.
            runBlocking { WearSettings.save(applicationContext, lat, lng) }
        }
    }

    private companion object {
        const val PATH = "/gebetszeiten/location"
    }
}
