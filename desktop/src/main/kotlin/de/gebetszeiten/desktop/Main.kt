package de.gebetszeiten.desktop

import de.gebetszeiten.desktop.taskbar.Win32Taskbar
import de.gebetszeiten.desktop.taskbar.runFieldDemo
import de.gebetszeiten.desktop.ui.runSettingsDemo
import de.gebetszeiten.desktop.ui.runTrayDemo
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** Haelt die Sperre bis Prozessende (sonst koennte der GC den Kanal schliessen). */
private var instance: SingleInstance? = null

/**
 * Einstieg. Ohne Schalter das Programm (Feld, Tray, Takt); Handproben hinter
 * `--demo-field`, `--demo-tray`, `--demo-settings`.
 */
fun main(args: Array<String>) {
    // Vor jedem AWT-Fenster: echte Pixel, Per-Monitor-DPI.
    Win32Taskbar.prepareProcess()
    if (args.contains("--demo-field")) {
        runFieldDemo(args)
        return
    }
    if (args.contains("--demo-tray")) {
        runTrayDemo(args)
        return
    }
    if (args.contains("--demo-settings")) {
        runSettingsDemo(args)
        return
    }

    val dir = resolveDataDir(
        property = System.getProperty("gebetszeiten.dataDir"),
        environment = System.getenv("GEBETSZEITEN_DATA_DIR"),
        appData = System.getenv("APPDATA"),
    )
    try {
        Files.createDirectories(dir)
        // Zweiter Start: still beenden, die erste Instanz laeuft weiter.
        instance = SingleInstance.tryAcquire(dir) ?: return
        logToFile(dir.resolve("gebetszeiten.log"))
        DesktopApp(dir).start()
    } catch (t: Throwable) {
        log("Start fehlgeschlagen: ${t.javaClass.simpleName}: ${t.message}")
        t.printStackTrace()
        exitProcess(1)
    }
}

/**
 * Die gepackte .exe hat keine Konsole: das Protokoll (System.err) geht
 * zusaetzlich in [file], bei jedem Start neu.
 */
private fun logToFile(file: Path) {
    val out = runCatching { FileOutputStream(file.toFile(), false) }.getOrNull() ?: return
    val console = System.err
    val tee = object : OutputStream() {
        override fun write(b: Int) {
            console.write(b)
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            console.write(b, off, len)
            out.write(b, off, len)
        }

        override fun flush() {
            console.flush()
            out.flush()
        }
    }
    System.setErr(PrintStream(tee, true, Charsets.UTF_8))
}
