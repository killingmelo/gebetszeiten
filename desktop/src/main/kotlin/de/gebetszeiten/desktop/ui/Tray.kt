package de.gebetszeiten.desktop.ui

import de.gebetszeiten.desktop.display.DisplayState
import de.gebetszeiten.desktop.notify.NotifyEvent
import java.awt.CheckboxMenuItem
import java.awt.EventQueue
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Symbol im Infobereich: Tooltip mit dem Stand, Menue, Toast. Linksklick
 * oeffnet die heutigen Zeiten. Ohne System-Tray (Explorer neu gestartet,
 * Gruppenrichtlinie) bleibt alles still; [available] sagt es.
 */
class Tray(
    onToday: () -> Unit,
    onSettings: () -> Unit,
    onToggleField: () -> Unit,
    onStopEzan: () -> Unit,
    onQuit: () -> Unit,
) {
    private val fieldItem = CheckboxMenuItem("Feld in der Taskleiste", true)
    private val stopItem = MenuItem("Ezan stoppen").apply { isEnabled = false }
    private var icon: TrayIcon? = null

    val available: Boolean get() = icon != null

    init {
        if (SystemTray.isSupported()) {
            val menu = PopupMenu()
            menu.add(MenuItem("Heutige Zeiten").apply { addActionListener { onToday() } })
            menu.add(MenuItem("Einstellungen").apply { addActionListener { onSettings() } })
            menu.addSeparator()
            fieldItem.addItemListener { onToggleField() }
            menu.add(fieldItem)
            stopItem.addActionListener { onStopEzan() }
            menu.add(stopItem)
            menu.addSeparator()
            menu.add(MenuItem("Beenden").apply { addActionListener { onQuit() } })

            val t = TrayIcon(loadImage(), "Gebetszeiten", menu).apply {
                isImageAutoSize = true
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) onToday()
                    }
                })
            }
            runCatching {
                SystemTray.getSystemTray().add(t)
                icon = t
            }
        }
    }

    fun update(state: DisplayState) = onEdt {
        // Windows kuerzt Tooltips bei 127 Zeichen.
        icon?.toolTip = state.tooltip.take(127)
    }

    /** Toast ueber Windows; den Ton macht Windows selbst, kein Extra-Piepen. */
    fun toast(e: NotifyEvent) = onEdt {
        icon?.displayMessage(e.title, e.text, TrayIcon.MessageType.NONE)
    }

    fun setFieldChecked(on: Boolean) = onEdt { fieldItem.state = on }

    /** Ezan stoppen ist nur waehrend der Wiedergabe bedienbar. */
    fun setEzanPlaying(playing: Boolean) = onEdt { stopItem.isEnabled = playing }

    fun remove() = onEdt {
        icon?.let { SystemTray.getSystemTray().remove(it) }
        icon = null
    }

    private fun loadImage(): Image {
        val name = if (trayIconSize(systemScale()) >= 32) "/icon.png" else "/icon16.png"
        val url = Tray::class.java.getResource(name) ?: Tray::class.java.getResource("/icon.png")
        return Toolkit.getDefaultToolkit().getImage(url)
    }

    private fun onEdt(block: () -> Unit) {
        if (EventQueue.isDispatchThread()) block() else EventQueue.invokeLater(block)
    }
}
