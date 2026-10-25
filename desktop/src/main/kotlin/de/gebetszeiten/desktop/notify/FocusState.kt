package de.gebetszeiten.desktop.notify

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * `SHQueryUserNotificationState`: BUSY (2), Vollbild-Direct3D (3),
 * Praesentation (4) und Ruhezeit (6) -> still. Alles andere darf klingen.
 */
fun quietFromState(quns: Int): Boolean = quns == 2 || quns == 3 || quns == 4 || quns == 6

private interface Shell32Quns : StdCallLibrary {
    fun SHQueryUserNotificationState(pquns: IntByReference): Int

    companion object {
        val INSTANCE: Shell32Quns = Native.load("shell32", Shell32Quns::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** Ob der Nutzer gerade nicht gestoert werden will (Spiel, Praesentation, Fokus-Assistent). */
object FocusState {
    /** Im Zweifel (Aufruf scheitert): nicht still — der Ezan soll nicht verstummen. */
    fun quiet(): Boolean = runCatching {
        val state = IntByReference()
        val hr = Shell32Quns.INSTANCE.SHQueryUserNotificationState(state)
        hr == 0 && quietFromState(state.value)
    }.getOrDefault(false)
}
