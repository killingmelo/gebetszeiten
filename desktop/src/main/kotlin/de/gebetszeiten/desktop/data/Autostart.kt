package de.gebetszeiten.desktop.data

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Path

/** Wert, der unter `Run` steht: der Pfad in Anfuehrungszeichen. */
fun autostartRunValue(exe: Path): String = "\"$exe\""

/** True, wenn [command] die gepackte Gebetszeiten.exe ist (nicht java.exe aus dem Entwicklungslauf). */
fun isPackagedExe(command: String?): Boolean =
    command != null && command.lowercase().endsWith("gebetszeiten.exe")

/**
 * Autostart ueber `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, Wert
 * `Gebetszeiten`. Wirksam nur, wenn das Programm selbst als gepackte `.exe`
 * laeuft; aus Gradle/IDE (java.exe) tut [set] nichts, damit ein Entwicklungslauf
 * nie einen Autostart-Eintrag auf java.exe anlegt. [set] ist bewusst ungetestet
 * (schreibt in die echte Registry); getestet sind nur [autostartRunValue] und
 * [isPackagedExe].
 */
object Autostart {
    private const val KEY = """Software\Microsoft\Windows\CurrentVersion\Run"""
    private const val NAME = "Gebetszeiten"

    fun set(enabled: Boolean, exe: Path) {
        if (!isPackagedExe(ProcessHandle.current().info().command().orElse(null))) return
        runCatching {
            if (enabled) {
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, KEY, NAME, autostartRunValue(exe))
            } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, NAME)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, KEY, NAME)
            }
        }.onFailure { System.err.println("Autostart: ${it.javaClass.simpleName}: ${it.message}") }
    }

    fun isEnabled(): Boolean = runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, NAME)
    }.getOrDefault(false)

    /** Pfad der laufenden gepackten .exe, sonst null. */
    fun currentExe(): Path? =
        ProcessHandle.current().info().command().orElse(null)?.takeIf { isPackagedExe(it) }?.let { Path.of(it) }
}
