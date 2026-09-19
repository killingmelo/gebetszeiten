package de.gebetszeiten.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import de.gebetszeiten.core.prayertimes.officialtimes.WearSyncContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Wendet einen vom Handy gesyncten Payload an: speichern, ggf. Ort
 * uebernehmen, Vibrations-Kette neu armieren, Tile und Complication
 * auffrischen. Duenner Orchestrator ohne eigene Entscheidungen — die
 * Logik steckt in [SyncDecision] (JVM-getestet).
 *
 * Zwei Eintrittspunkte: [apply] fuer neu eintreffende DataItems
 * ([WearSyncListenerService]) und [replayExisting] fuer den Fall, dass
 * die Uhr-App neu installiert bzw. ihre Daten geloescht wurden — dann
 * ist das DataItem unveraendert, `onDataChanged` feuert nie und die Uhr
 * bliebe ohne Nachholen dauerhaft leer.
 */
object WearSyncApplier {

    /**
     * Wendet [payload] an — AUSSER ein frischerer eigener Stand am
     * SYNC-ORT unterlaeuft ihn (siehe [SyncDecision.shouldApply]). Seit
     * Aufgabe 6 ruft `MainActivity.onStart` `refreshWearOfficial` und den
     * Sync-Nachholpfad UNABHAENGIG voneinander an — hat der eigene Abruf in
     * diesem Wettlauf schon geschrieben, bevor dieser Sync verarbeitet
     * wird, waere ein bedingungsloses Ueberschreiben ein Rueckschritt.
     *
     * Die eigentliche Entscheidung UND der Schreibvorgang liegen bewusst
     * beide in [WearOfficialCache.applySync] (EINE Transaktion, siehe dort)
     * — nicht hier: ein Lesen hier draussen, gefolgt von einem Schreiben
     * dort drinnen, liesse genau das Fenster offen, das Fix-Runde 1 dieser
     * Aufgabe uebersehen hatte (ein zwischen Lesen und Schreiben
     * abgeschlossener eigener Abruf waere blind ueberschrieben worden).
     *
     * **Wirft nie ausser CancellationException** (Fix-Runde 4, Important 2).
     * Bis dahin war nur der SCHLUSS gekapselt ([notifyWearOfficialRefreshed]
     * faengt selbst ab), alles davor stand ohne `try`:
     * [WearOfficialCache.syncedLocation] (`store.data.first()`),
     * [WearOfficialCache.applySync] und [WearSettings.save] (beide
     * `store.edit{}`) — keine davon faengt intern, eine `IOException` aus dem
     * DataStore kam also ganz heraus. Der eine Aufrufer ([replayExisting])
     * kapselte das selbst, der andere NICHT: [WearSyncListenerService] ruft
     * diese Funktion per `runBlocking` auf einem Binder-Thread auf, wo eine
     * entkommene Ausnahme unbehandelt an den Default-Handler geht — also in
     * den Prozessabsturz.
     */
    suspend fun apply(context: Context, payload: SyncDecision.Payload) {
        // Schon abgelegt? Dann muss das Neuzeichnen unten AUCH dann laufen,
        // wenn erst danach etwas scheitert (z. B. `WearSettings.save` beim
        // Ortswechsel) — dieselbe Regel wie in `notifyWearOfficialRefreshed`:
        // was bereits geschrieben ist, darf nicht unsichtbar bleiben, nur
        // weil ein spaeterer Schritt scheitert.
        var abgelegt = false
        try {
            val synced = WearOfficialCache.syncedLocation(context)
            val adopt = SyncDecision.shouldAdoptLocation(payload, synced?.first, synced?.second)
            if (!WearOfficialCache.applySync(context, payload, adopt)) return
            abgelegt = true
            if (adopt) WearSettings.save(context, payload.city, payload.lat, payload.lng)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("WearSyncApplier", "Sync liess sich nicht anwenden", e)
            if (!abgelegt) return
        }
        // Neue Zeiten/neuer Ort: Vibrations-Kette neu armieren, Tile und
        // Complication einmalig auffrischen (danach wieder Zero-Wakeup) —
        // derselbe geteilte Weg wie nach einem eigenen Abruf
        // (`WearRefresh.launchWearRefresh`) und der Notausgang-Toggle
        // (Fix-Runde 3, Important 2). Bis hierher standen die drei Anstoesse
        // von Hand, OHNE `try`: eine `IOException` beim Neubewerten der
        // Vibrationskette riss das Neuzeichnen mit, und weil [apply] von
        // [WearSyncListenerService] per `runBlocking` auf einem
        // Binder-Thread aufgerufen wird, waere die Ausnahme dort ganz
        // entkommen. [notifyWearOfficialRefreshed] faengt beide Schritte
        // in getrennten `try`-Bloecken selbst ab.
        notifyWearOfficialRefreshed(context)
    }

    /** Payload aus einem DataItem — null, wenn Pflichtfelder fehlen.
     *  [WearSyncContract.KEY_UPDATED] fehlt bei einem aelteren Telefon;
     *  `containsKey` statt `getLong` mit Default, weil `getLong` sonst nicht
     *  von einem echten `0L`-Zeitstempel zu unterscheiden waere (siehe
     *  [SyncDecision.shouldApply]). */
    fun payloadOf(item: DataItem): SyncDecision.Payload? {
        val map = DataMapItem.fromDataItem(item).dataMap
        return SyncDecision.Payload(
            scheduleText = map.getString(WearSyncContract.KEY_SCHEDULE) ?: return null,
            lat = map.getDouble(WearSyncContract.KEY_LAT),
            lng = map.getDouble(WearSyncContract.KEY_LNG),
            city = map.getString(WearSyncContract.KEY_CITY) ?: return null,
            updatedEpochMs = if (map.containsKey(WearSyncContract.KEY_UPDATED)) {
                map.getLong(WearSyncContract.KEY_UPDATED)
            } else {
                null
            },
        )
    }

    /**
     * Holt ein bereits vorhandenes DataItem einmalig nach (nur wenn noch
     * nie etwas gesynct wurde, siehe [SyncDecision.shouldReplay] — danach
     * kostet der App-Start keinen gms-Roundtrip mehr). Wirft nie ausser
     * CancellationException; true, wenn etwas angewendet wurde.
     *
     * Bis Fix-Runde 5 galt diese Zusage NICHT: die Vorpruefung
     * ([WearOfficialCache.syncedLocation], ein nackter `store.data.first()`,
     * der nichts faengt, plus [SyncDecision.shouldReplay]) stand VOR dem
     * `try`. Eine DataStore-`IOException` verliess die Funktion damit
     * ungehindert — und der einzige Aufrufer ist `MainActivity.onStart` im
     * blanken `MainScope` (kein `SupervisorJob`, kein
     * `CoroutineExceptionHandler`): derselbe Absturzpfad wie in [apply] vor
     * Fix-Runde 4, nur zwei Funktionen weiter unten. Die Vorpruefung steht
     * jetzt IM `try`; ihr `false` ("nichts nachzuholen") und das `false` des
     * `catch`-Zweigs ("Nachholen gescheitert") haben fuer den Aufrufer
     * ohnehin dieselbe Bedeutung: nicht neu zeichnen.
     */
    suspend fun replayExisting(context: Context): Boolean {
        return try {
            val synced = WearOfficialCache.syncedLocation(context)
            if (!SyncDecision.shouldReplay(synced?.first, synced?.second)) return false
            val payloads = withContext(Dispatchers.IO) {
                val buffer = Tasks.await(
                    Wearable.getDataClient(context).getDataItems(),
                    10,
                    TimeUnit.SECONDS,
                )
                try {
                    buffer.filter { it.uri.path == WearSyncContract.PATH }.mapNotNull(::payloadOf)
                } finally {
                    buffer.release()
                }
            }
            payloads.forEach { apply(context, it) }
            payloads.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("WearSyncApplier", "Nachholen des bestehenden DataItems fehlgeschlagen", e)
            false
        }
    }
}
