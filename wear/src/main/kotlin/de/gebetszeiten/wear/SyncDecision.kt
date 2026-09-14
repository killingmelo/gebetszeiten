package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.RawEntry
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import java.time.LocalDate

/**
 * Reine Entscheidungslogik des Phone→Wear-Syncs — JVM-testbar; der
 * ListenerService bleibt ein dünner IO-Wrapper ohne eigene Logik.
 */
object SyncDecision {

    data class Payload(
        val scheduleText: String,
        val lat: Double,
        val lng: Double,
        val city: String,
    )

    /** Unlesbarer/leerer Payload → null: verwerfen, alter Cache bleibt. */
    fun parse(payload: Payload): Map<LocalDate, SixTimes>? =
        ScheduleText.parse(payload.scheduleText).ifEmpty { null }

    /** Übernehmen bei Erst-Sync oder ANDEREM Handy-Ort als zuletzt
     *  übernommen (gleiche 1-km-Toleranz wie der Cache-Stempel); sonst
     *  bleibt ein Uhr-Picker-Override bestehen. */
    fun shouldAdoptLocation(payload: Payload, syncedLat: Double?, syncedLng: Double?): Boolean =
        !stampMatches(syncedLat, syncedLng, payload.lat, payload.lng)

    /** Bestehendes DataItem beim App-Start nachholen? Nur wenn noch nie
     *  etwas gesynct wurde (Neuinstallation/Daten geloescht: das DataItem
     *  ist unveraendert, `onDataChanged` feuert nie). Sobald ein Sync
     *  angekommen ist, bleibt der App-Start frei von gms-Roundtrips. */
    fun shouldReplay(syncedLat: Double?, syncedLng: Double?): Boolean =
        syncedLat == null || syncedLng == null

    /**
     * Gewinnt der Sync gegen den eigenen, an dieser Stelle bereits
     * abgelegten Stand? Seit Aufgabe 6 ruft die Uhr ihre amtlichen Zeiten
     * auch selbst ab ([refreshWearOfficial]); der (billigere) Sync vom
     * Handy bleibt daneben bestehen, darf aber einen frischeren eigenen
     * Fund nicht mehr blind verdraengen.
     *
     * Ohne eigenen Stand ([ownUpdatedEpochMs] `null`, siehe [ownUpdatedEpochMs])
     * gewinnt der Sync immer — an diesem Ort gibt es nichts zu schuetzen.
     * Bei GLEICHEM Stand gewinnt ebenfalls der Sync: er ist billiger als ein
     * eigener Abruf, ein Gleichstand ist kein Grund, ihn zu verwerfen. Nur
     * ein ECHT frischerer eigener Stand schlaegt ihn — das ist der Fall, in
     * dem die Uhr GERADE (im selben App-Start, siehe `MainActivity.onStart`,
     * das `refreshWearOfficial` und den Sync-Nachholpfad unabhaengig
     * voneinander anstoesst) selbst schon amtliche Zeiten geholt hat, bevor
     * der Sync angewendet wird.
     *
     * [ownUpdatedEpochMs] MUSS vom Aufrufer am SELBEN Ort ermittelt worden
     * sein wie der Sync (siehe [ownUpdatedEpochMs]) — sonst waere dieser
     * Vergleich eine Aussage ueber zwei verschiedene Orte, Aepfel gegen
     * Birnen.
     */
    fun syncWins(syncUpdatedEpochMs: Long, ownUpdatedEpochMs: Long?): Boolean =
        ownUpdatedEpochMs == null || syncUpdatedEpochMs >= ownUpdatedEpochMs

    /**
     * Der fuer [syncWins] massgebliche eigene Zeitstempel: der Cache-Kopf AN
     * DEN SYNC-KOORDINATEN [lat]/[lng] (~1 km Toleranz, siehe [stampMatches]
     * ueber [CacheStore.select]) — AUSDRUECKLICH NICHT am aktuell gewaehlten
     * Uhr-Ort.
     *
     * Grund: die Uhr fuehrt nur EINEN eigenen Abruf, den des aktiven Orts
     * ([refreshWearOfficial], `WearSettings.location`). Ein Handy-Sync kann
     * aber fuer einen ANDEREN Ort ankommen, wenn die Uhr inzwischen per
     * Picker einen anderen Ort gewaehlt hat als das Handy zuletzt gesehen
     * hat (siehe [shouldAdoptLocation] — der Picker-Override bleibt dann
     * bestehen, [WearOfficialCache.store] legt den Sync trotzdem unter
     * SEINEN eigenen Koordinaten ab). Ein Vergleich mit dem Zeitstempel des
     * aktiven Orts waere dann ein Vergleich ueber zwei verschiedene Orte —
     * sinnlos, denn ein frischer eigener Fund am aktiven Ort sagt nichts
     * darueber aus, ob am SYNC-Ort schon einmal (eigenstaendig oder per
     * frueherem Sync) etwas abgelegt wurde. Deshalb wird hier immer an DEN
     * SYNC-KOORDINATEN nachgeschlagen: unabhaengig davon, was der aktive
     * Ort gerade ist, bleibt der Vergleich in [syncWins] ueber denselben
     * Ort.
     *
     * `null`, wenn dort noch kein Eintrag liegt — dann gewinnt der Sync
     * ohnehin (siehe [syncWins]).
     */
    fun ownUpdatedEpochMs(entries: List<RawEntry>, lat: Double, lng: Double): Long? =
        CacheStore.select(entries, lat, lng)?.header?.updatedEpochMs
}
