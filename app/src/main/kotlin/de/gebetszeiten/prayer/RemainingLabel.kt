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
        // 10-minute floor steps below an hour: 27 min → "noch 20+ Min".
        // Precision grows as the prayer approaches — coarse far out is fine,
        // misleading close up is not.
        safe.toMinutes() >= 10 -> "noch ${(safe.toMinutes() / 10) * 10}+ Min"
        // Final 10 minutes: the exact number IS the urgency signal.
        safe.toMinutes() >= 1 -> "noch ${safe.toMinutes()} Min"
        else -> "jetzt"
    }
}

/** True in the final minutes before the prayer — surfaces switch to their
 *  urgency colour. */
fun isUrgent(remaining: Duration): Boolean =
    !remaining.isNegative && remaining.toMinutes() < 10
