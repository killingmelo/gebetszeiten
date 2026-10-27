package de.gebetszeiten.desktop.ui

import de.gebetszeiten.desktop.data.loadPlaces
import de.gebetszeiten.desktop.data.SettingsStore
import java.io.File
import java.nio.file.Files

/**
 * Handprobe `--demo-settings`: Einstellungsfenster mit einem SettingsStore in
 * einem Temp-Ordner (nie das echte %APPDATA%) und ohne Autostart. Tippt
 * "nurnberg" ins Suchfeld, waehlt den ersten Treffer, macht mit `--shot=<png>`
 * ein Bild, speichert und gibt die geschriebene settings.properties aus.
 */
fun runSettingsDemo(args: Array<String>) {
    val shot = args.firstOrNull { it.startsWith("--shot=") }?.removePrefix("--shot=")
    val dir = Files.createTempDirectory("gebetszeiten-demo-settings")
    val places = SettingsDemoResources.places()
    println("places=${places.size} dir=$dir")
    val window = SettingsWindow(SettingsStore(dir), places, onSaved = { println("onSaved: $it") }, applyAutostart = {})
    window.show()
    Thread.sleep(800)
    val hit = window.searchAndSelectFirst("nurnberg")
    println("erster Treffer: $hit")
    Thread.sleep(500)
    // --shot=x.png: ein Bild je Reiter (x-0.png … x-4.png).
    if (shot != null) {
        for (tab in 0..4) {
            window.selectTab(tab)
            Thread.sleep(300)
            window.snapshot()?.let { javax.imageio.ImageIO.write(it, "png", File(shot.removeSuffix(".png") + "-$tab.png")) }
        }
        window.selectTab(0)
    }
    window.saveOnEdt()
    println("--- settings.properties ---")
    println(Files.readString(dir.resolve("settings.properties")))
    System.exit(0)
}

private object SettingsDemoResources {
    fun places() = loadPlaces { SettingsDemoResources::class.java.classLoader.getResourceAsStream(it) }
}
