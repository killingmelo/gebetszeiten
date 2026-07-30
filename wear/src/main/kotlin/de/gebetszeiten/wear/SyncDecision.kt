package de.gebetszeiten.wear

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
}
