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
            // Zweites Netz (Fix-Runde 4, Important 2): [WearSyncApplier.apply]
            // faengt seit dieser Runde selbst alles ab, aber hier laeuft kein
            // Coroutine-Scope mit Rettungs-Handler, sondern ein `runBlocking`
            // auf einem Binder-Thread — was hier entkaeme, ginge an den
            // Default-Handler und damit in den Prozessabsturz. Ausserdem darf
            // ein kaputtes DataItem nicht die uebrigen Ereignisse dieser
            // Zustellung mitreissen, deshalb `continue` statt Abbruch.
            // Kein Sonderzweig fuer CancellationException: hier gibt es keinen
            // aeusseren Job, an den man sie weiterreichen koennte — ein
            // erneutes Werfen waere genau der Absturz, den dieser `try`
            // verhindern soll.
            try {
                runBlocking { WearSyncApplier.apply(applicationContext, payload) }
            } catch (e: Exception) {
                android.util.Log.w("WearSyncListener", "Sync-Ereignis liess sich nicht verarbeiten", e)
            }
        }
    }
}
