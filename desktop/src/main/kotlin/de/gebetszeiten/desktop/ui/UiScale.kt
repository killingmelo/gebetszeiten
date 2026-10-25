package de.gebetszeiten.desktop.ui

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import kotlin.math.roundToInt

private interface User32System : StdCallLibrary {
    fun GetDpiForSystem(): Int

    companion object {
        val INSTANCE: User32System = Native.load("user32", User32System::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** Reine Rechnung: Schriftgroesse bei Skalierung [scale], nie unter 1. */
fun scaledFontSize(base: Int, scale: Double): Int = (base * scale).roundToInt().coerceAtLeast(1)

/** Reine Rechnung: Pixelmass bei Skalierung [scale] (0 bleibt 0). */
fun scaledPx(px: Int, scale: Double): Int = (px * scale).roundToInt()

/** 16 px unter 150 %, ab 150 % die 32-px-Variante des Tray-Symbols. */
fun trayIconSize(scale: Double): Int = if (scale >= 1.5) 32 else 16

/**
 * Windows-Skalierung des Systems (1.0 bei 96 dpi). Der Prozess setzt
 * `sun.java2d.uiScale=1` (die Taskleisten-Flaeche braucht echte Pixel), also
 * skaliert Swing nicht von selbst: Fenster messen alles ueber [scaled].
 */
fun systemScale(): Double {
    val dpi = runCatching { User32System.INSTANCE.GetDpiForSystem() }.getOrDefault(0)
    return if (dpi > 0) dpi / 96.0 else 1.0
}

fun scaled(px: Int): Int = scaledPx(px, systemScale())

private var defaultsInstalled = false

/**
 * Skaliert alle Standard-Schriften von Swing mit [systemScale]. Einmal vor dem
 * ersten Fenster aufrufen; weitere Aufrufe tun nichts (sonst wuerde doppelt
 * skaliert).
 */
@Synchronized
fun installScaledDefaults() {
    if (defaultsInstalled) return
    defaultsInstalled = true
    val scale = systemScale()
    if (scale == 1.0) return
    val defaults = UIManager.getLookAndFeelDefaults()
    val fonts = defaults.keys().toList().filter { defaults.get(it) is FontUIResource }
    for (key in fonts) {
        val f = defaults.get(key) as FontUIResource
        defaults.put(key, FontUIResource(f.deriveFont(scaledFontSize(f.size, scale).toFloat())))
    }
}
