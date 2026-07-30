package de.gebetszeiten.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import de.gebetszeiten.core.prayertimes.officialtimes.WearSyncContract
import kotlinx.coroutines.runBlocking

/**
 * Empfängt den amtlichen Zeiten-Cache vom Handy (DataItem
 * [WearSyncContract.PATH]), materialisiert ihn in [WearOfficialCache]
 * und stößt Tile, Complication und Vibrations-Kette an — beides über
 * [WearSyncApplier], den auch der Nachhol-Pfad beim App-Start nutzt.
 * Dünner IO-Wrapper — die Logik steckt in [SyncDecision] (JVM-getestet).
 * runBlocking ist hier ok: onDataChanged läuft auf einem
 * Binder-Hintergrund-Thread (gleiches Muster wie CityPickerActivity).
 */
class WearSyncListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != WearSyncContract.PATH) continue
            val payload = WearSyncApplier.payloadOf(event.dataItem) ?: continue
            runBlocking { WearSyncApplier.apply(applicationContext, payload) }
        }
    }
}
