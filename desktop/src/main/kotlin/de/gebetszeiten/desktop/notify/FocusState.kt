package de.gebetszeiten.desktop.notify

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import de.gebetszeiten.desktop.log

/**
 * `SHQueryUserNotificationState`: BUSY (2), Vollbild-Direct3D (3),
 * Praesentation (4) und QUIET_TIME (6) -> still. Alles andere darf klingen.
 * QUIET_TIME ist NICHT „Nicht stoeren", sondern die erste Stunde nach
 * Anmeldung/Update; „Nicht stoeren" meldet diese Schnittstelle unter
 * Windows 11 nicht verlaesslich, dafuer kommt WNF dazu ([quietDecision]).
 */
fun quietFromState(quns: Int): Boolean = quns == 2 || quns == 3 || quns == 4 || quns == 6

/**
 * WNF-Zustaende der Shell (undokumentiert, je ein DWORD, 0 = aus): das aktive
 * „Nicht stoeren"/Fokus-Profil und der Ruhemoment-Modus der Shell.
 * Auf dem Entwicklungs-PC (Windows 11 Build 26300) liefert nur das Profil
 * einen Wert; den Ruhemoment-Namen kennt das System dort nicht
 * (STATUS_OBJECT_NAME_NOT_FOUND) — er bleibt als Best Effort drin.
 */
const val WNF_SHEL_QUIETHOURS_ACTIVE_PROFILE_CHANGED = 0x0D83063EA3BF1C75L
const val WNF_SHEL_QUIET_MOMENT_SHELL_MODE_CHANGED = 0x0D83063EA3BF5075L

/**
 * Still, wenn SHQuery still meldet ODER ein WNF-Wert ungleich 0 ist.
 * `null` = nicht lesbar (Aufruf gescheitert, Zustand nie gesetzt) und zaehlt
 * nicht; ist gar nichts lesbar, klingt der Ezan.
 */
fun quietDecision(quns: Int?, wnfValues: List<Int?>): Boolean =
    (quns != null && quietFromState(quns)) || wnfValues.any { it != null && it != 0 }

private interface Shell32Quns : StdCallLibrary {
    fun SHQueryUserNotificationState(pquns: IntByReference): Int

    companion object {
        val INSTANCE: Shell32Quns = Native.load("shell32", Shell32Quns::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

private interface NtdllWnf : StdCallLibrary {
    fun NtQueryWnfStateData(
        stateName: LongByReference,
        typeId: Pointer?,
        explicitScope: Pointer?,
        changeStamp: IntByReference,
        buffer: Pointer,
        bufferSize: IntByReference,
    ): Int

    companion object {
        val INSTANCE: NtdllWnf = Native.load("ntdll", NtdllWnf::class.java)
    }
}

/**
 * Ob der Nutzer gerade nicht gestoert werden will: Spiel/Vollbild/
 * Praesentation ueber `SHQueryUserNotificationState`, „Nicht stoeren"/Fokus
 * per Best Effort ueber WNF (`ntdll!NtQueryWnfStateData`, undokumentiert —
 * kann sich mit einem Windows-Update aendern). Scheitert WNF, gilt nur SHQuery.
 */
object FocusState {
    /** Je Zustandsname hoechstens eine Protokollzeile. */
    private val wnfWarned = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /** Im Zweifel (alles scheitert): nicht still — der Ezan soll nicht verstummen. */
    fun quiet(): Boolean = quietDecision(queryQuns(), wnfValues())

    /** Rohwerte fuer die Handprobe `--demo-tray`. */
    fun describe(): String {
        val wnf = wnfValues()
        return "quns=${queryQuns()} wnfProfile=${wnf[0]} wnfMoment=${wnf[1]}"
    }

    private fun queryQuns(): Int? = runCatching {
        val state = IntByReference()
        if (Shell32Quns.INSTANCE.SHQueryUserNotificationState(state) == 0) state.value else null
    }.getOrNull()

    private fun wnfValues(): List<Int?> =
        listOf(WNF_SHEL_QUIETHOURS_ACTIVE_PROFILE_CHANGED, WNF_SHEL_QUIET_MOMENT_SHELL_MODE_CHANGED).map(::readWnfDword)

    /** Ein DWORD aus dem WNF-Zustand; `null` bei Fehler oder leerem Zustand. */
    private fun readWnfDword(stateName: Long): Int? = try {
        val buffer = Memory(4).apply { clear() }
        val size = IntByReference(4)
        val status = NtdllWnf.INSTANCE.NtQueryWnfStateData(
            LongByReference(stateName), null, null, IntByReference(), buffer, size,
        )
        when {
            status != 0 -> { warnOnce(stateName, "NTSTATUS 0x%08X".format(status)); null }
            size.value < 4 -> null // nie veroeffentlicht
            else -> buffer.getInt(0)
        }
    } catch (e: Throwable) {
        warnOnce(stateName, "${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private fun warnOnce(stateName: Long, what: String) {
        if (!wnfWarned.add(stateName)) return
        log("WNF-Zustand 0x%016X nicht lesbar (%s), zaehlt nicht fuer Nicht stoeren".format(stateName, what))
    }
}
