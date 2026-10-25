package de.gebetszeiten.desktop.taskbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskbarGeometryTest {

    @Test
    fun fieldSitsLeftOfTray() {
        val taskbar = Box(0, 1392, 2560, 1440)
        val tray = Box(2300, 1392, 2560, 1440)
        assertEquals(Box(2172, 0, 2292, 48), fieldBox(taskbar, tray, width = 120))
    }

    @Test
    fun verticalTaskbarGivesNull() {
        val taskbar = Box(0, 0, 48, 1440)
        val tray = Box(0, 1200, 48, 1440)
        assertNull(fieldBox(taskbar, tray, width = 120))
    }

    @Test
    fun notEnoughSpaceGivesNull() {
        val taskbar = Box(0, 1392, 2560, 1440)
        val tray = Box(100, 1392, 2560, 1440)
        assertNull(fieldBox(taskbar, tray, width = 120))
    }

    @Test
    fun taskbarOnSecondMonitorStaysRelative() {
        // Leiste beginnt nicht bei x = 0: Ergebnis bleibt relativ zur Leiste.
        val taskbar = Box(-1920, 1032, 0, 1080)
        val tray = Box(-300, 1032, 0, 1080)
        assertEquals(Box(1492, 0, 1612, 48), fieldBox(taskbar, tray, width = 120))
    }

    @Test
    fun reattachKeepsWhenParentIsCurrentTaskbar() {
        assertEquals(Reattach.KEEP, reattachDecision(windowAlive = true, currentParent = 42L, taskbar = 42L))
    }

    @Test
    fun reattachAfterExplorerRestart() {
        // Alte Leiste weg (Elternfenster 0 oder fremd), neue Leiste da.
        assertEquals(Reattach.ATTACH, reattachDecision(windowAlive = true, currentParent = 0L, taskbar = 77L))
        assertEquals(Reattach.ATTACH, reattachDecision(windowAlive = true, currentParent = 42L, taskbar = 77L))
    }

    @Test
    fun reattachRecreatesWhenExplorerTookTheWindowAlong() {
        // Stirbt die Leiste, zerstoert Windows ihre Kindfenster mit.
        assertEquals(Reattach.RECREATE, reattachDecision(windowAlive = false, currentParent = 0L, taskbar = 77L))
    }

    @Test
    fun reattachHidesWithoutTaskbar() {
        assertEquals(Reattach.HIDE, reattachDecision(windowAlive = true, currentParent = 42L, taskbar = null))
        assertEquals(Reattach.HIDE, reattachDecision(windowAlive = false, currentParent = 0L, taskbar = null))
    }
}
