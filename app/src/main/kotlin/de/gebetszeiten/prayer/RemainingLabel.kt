package de.gebetszeiten.prayer

import java.time.Duration

/**
 * Floor-rounded remaining-time step for widget and persistent notification.
 *
 * A live Chronometer redraws EVERY SECOND while the home/lock screen is
 * visible — thousands of renders per day. These coarse, honest steps
 * ("noch 2+ Std" = more than 2h left) change only at a handful of moments,
 * each driven by one exact alarm, so the surfaces stay completely static
 * in between.
 */
fun remainingStepLabel(remaining: Duration): String {
    val safe = if (remaining.isNegative) Duration.ZERO else remaining
    return when {
        safe.toHours() >= 1 -> "noch ${safe.toHours()}+ Std"
        safe.toMinutes() >= 30 -> "noch 30+ Min"
        safe.toMinutes() >= 10 -> "noch 10+ Min"
        else -> "gleich"
    }
}
