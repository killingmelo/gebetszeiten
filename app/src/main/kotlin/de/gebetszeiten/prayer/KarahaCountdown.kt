package de.gebetszeiten.prayer

import java.time.Duration
import java.time.ZonedDateTime

/** Countdown-Text für eine Makruh-(Karaha-)Zeit relativ zu [now]. */
object KarahaCountdown {

    private const val LEAD_MINUTES = 60L

    data class State(val active: Boolean, val text: String)

    /** null außerhalb des relevanten Fensters; sonst aktiv ("noch …") oder
     *  bevorstehend innerhalb [LEAD_MINUTES] ("in …"). */
    fun state(now: ZonedDateTime, start: ZonedDateTime, end: ZonedDateTime): State? = when {
        !now.isBefore(start) && now.isBefore(end) ->
            State(active = true, text = "noch " + durationLabel(Duration.between(now, end).toMinutes().coerceAtLeast(0)))
        now.isBefore(start) -> {
            val toStart = Duration.between(now, start).toMinutes()
            if (toStart in 0 until LEAD_MINUTES) State(active = false, text = "in " + durationLabel(toStart)) else null
        }
        else -> null
    }
}
