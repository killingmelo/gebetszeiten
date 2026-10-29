package de.gebetszeiten.core.prayertimes.display

import de.gebetszeiten.core.prayertimes.Karaha
import java.time.format.DateTimeFormatter

/**
 * Compact karaha indicator shared by widget and lock-screen notification:
 * "⚠️ Karāha bis 13:21" while inside a window, "⚠️ Karāha ab 13:01" in the
 * 15 minutes before one. Static text — redrawn only at the window boundaries
 * via the display-alarm chain, never in between.
 */
data class KarahaLine(val text: String, val active: Boolean)

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Status → Text; `null`, wenn gerade kein Karaha ansteht. */
fun karahaLine(status: Karaha.Status): KarahaLine? = when (status) {
    is Karaha.Status.Active ->
        KarahaLine("⚠️ Karāha bis ${status.window.end.format(timeFormat)}", active = true)
    is Karaha.Status.Soon ->
        KarahaLine("⚠️ Karāha ab ${status.window.start.format(timeFormat)}", active = false)
    Karaha.Status.None -> null
}
