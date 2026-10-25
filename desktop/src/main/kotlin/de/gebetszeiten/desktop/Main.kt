package de.gebetszeiten.desktop

import de.gebetszeiten.desktop.taskbar.Win32Taskbar
import de.gebetszeiten.desktop.taskbar.runFieldDemo
import de.gebetszeiten.desktop.ui.runTrayDemo

/**
 * Einstieg. Bis Task 10 (Tray, Ticker, Waechter) nur die Handprobe des
 * Taskleisten-Felds hinter `--demo-field`.
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
    System.err.println("Gebetszeiten: Hauptprogramm folgt (Task 10). Proben: --demo-field, --demo-tray")
}
