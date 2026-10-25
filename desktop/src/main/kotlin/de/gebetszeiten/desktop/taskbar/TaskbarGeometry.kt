package de.gebetszeiten.desktop.taskbar

/** Rechteck in Bildschirm- oder Fensterpixeln, rechts/unten exklusiv wie Win32-RECT. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Wo das Feld in der Leiste sitzt: rechtsbuendig [gap] Pixel links vom
 * Infobereich ([trayNotify]), volle Hoehe der Leiste. Koordinaten **relativ zur
 * Taskleiste**, denn als Kind (`SetParent`) zaehlen sie ab deren Client-Ecke.
 *
 * `null`, wenn die Leiste senkrecht steht (Hoehe > Breite) oder links vom
 * Infobereich kein Platz fuer [width] bleibt — dann uebernimmt das Tray-Symbol.
 */
fun fieldBox(taskbar: Box, trayNotify: Box, width: Int, gap: Int = 8): Box? {
    if (taskbar.height > taskbar.width) return null
    val right = trayNotify.left - taskbar.left - gap
    val left = right - width
    if (left < 0) return null
    return Box(left, 0, right, taskbar.height)
}

/** Was der 60-s-Waechter mit dem Feld tun soll. */
enum class Reattach { KEEP, ATTACH, RECREATE, HIDE }

/**
 * Entscheidung des Waechters, rein aus Fenster-Handles (als Zahl):
 * [windowAlive] ist `IsWindow(feld)` — stirbt Explorer, zerstoert Windows die
 * Kindfenster der Leiste mit, auch fremde; [currentParent] ist
 * `GetParent(feld)`, [taskbar] die jetzt gefundene `Shell_TrayWnd`.
 * Keine Leiste -> verstecken; Feld weg -> neu bauen; andere Mutter -> neu einhaengen.
 */
fun reattachDecision(windowAlive: Boolean, currentParent: Long, taskbar: Long?): Reattach = when {
    taskbar == null || taskbar == 0L -> Reattach.HIDE
    !windowAlive -> Reattach.RECREATE
    currentParent == taskbar -> Reattach.KEEP
    else -> Reattach.ATTACH
}
