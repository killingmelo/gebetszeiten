package de.gebetszeiten.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.gebetszeiten.data.AppSettings

/**
 * Publishes the chosen location to a paired Wear OS device via the Data Layer.
 * This is a purely local (Bluetooth / Play services) transfer — no internet.
 * Fire-and-forget: if no watch is connected the put simply never delivers.
 */
object WatchSync {

    const val PATH = "/gebetszeiten/location"

    fun pushLocation(context: Context, settings: AppSettings) {
        try {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putDouble("lat", settings.latitude)
                dataMap.putDouble("lng", settings.longitude)
                dataMap.putString("city", settings.city)
                // Changing field so an unchanged location still re-syncs.
                dataMap.putLong("ts", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request)
        } catch (e: Exception) {
            // Wearable API unavailable (e.g. no Play services) — ignore.
        }
    }
}
