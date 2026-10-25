package de.gebetszeiten.desktop.ui

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.display.germanName
import de.gebetszeiten.core.prayertimes.display.nextPrayer
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Kleines randloses Fenster mit den Zeiten des Tages. Schliesst bei
 * Fokusverlust. Alle Masse laufen ueber [scaled] (der Prozess hat Swing-uiScale 1).
 */
class TodayPopup {
    private var dialog: JDialog? = null

    /**
     * [anchor] ist das Rechteck, ueber (oder unter) dem das Fenster sitzt, in
     * Bildschirmpixeln — meist das Tray-Symbol. [now] nur fuer die Hervorhebung
     * des naechsten Gebets.
     */
    fun showAbove(
        anchor: Rectangle,
        times: DailyPrayerTimes?,
        place: String?,
        karaha: List<Karaha.Window>,
        source: String,
        now: ZonedDateTime = ZonedDateTime.now(),
    ) {
        SwingUtilities.invokeLater { showNow(anchor, times, place, karaha, source, now) }
    }

    /** Offenes Fenster als Bild (Handprobe/Test), sonst null. */
    fun snapshot(): java.awt.image.BufferedImage? {
        var img: java.awt.image.BufferedImage? = null
        runCatching {
            SwingUtilities.invokeAndWait {
                val c = dialog?.contentPane ?: return@invokeAndWait
                img = java.awt.image.BufferedImage(c.width, c.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = img!!.createGraphics()
                c.paint(g)
                g.dispose()
            }
        }
        return img
    }

    val isShowing: Boolean get() = dialog?.isShowing == true

    fun close() {
        SwingUtilities.invokeLater { dispose() }
    }

    private fun dispose() {
        dialog?.dispose()
        dialog = null
    }

    private fun showNow(
        anchor: Rectangle,
        times: DailyPrayerTimes?,
        place: String?,
        karaha: List<Karaha.Window>,
        source: String,
        now: ZonedDateTime,
    ) {
        dispose()
        val d = JDialog().apply {
            isUndecorated = true
            isAlwaysOnTop = true
            focusableWindowState = true
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            contentPane = buildContent(times, place, karaha, source, now)
            pack()
            // Fokusverlust schliesst; zuerst muss das Fenster ihn bekommen haben.
            addWindowFocusListener(object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) = dispose()
            })
        }
        dialog = d
        d.location = position(anchor, d.size)
        d.isVisible = true
        d.toFront()
        d.requestFocus()
    }

    private fun position(anchor: Rectangle, size: Dimension): Point {
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
        val screen = screens.firstOrNull { it.intersects(anchor) || it.contains(anchor.x, anchor.y) }
            ?: screens.firstOrNull() ?: Rectangle(0, 0, 1920, 1080)
        val gap = scaled(8)
        // Rechtsbuendig zum Anker, innerhalb des Bildschirms.
        var x = anchor.x + anchor.width - size.width
        x = x.coerceIn(screen.x + gap, (screen.x + screen.width - size.width - gap).coerceAtLeast(screen.x + gap))
        val above = anchor.y + anchor.height / 2 > screen.y + screen.height / 2
        val y = if (above) anchor.y - size.height - gap else anchor.y + anchor.height + gap
        return Point(x, y.coerceIn(screen.y, (screen.y + screen.height - size.height).coerceAtLeast(screen.y)))
    }

    private fun buildContent(
        times: DailyPrayerTimes?,
        place: String?,
        karaha: List<Karaha.Window>,
        source: String,
        now: ZonedDateTime,
    ): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = BG
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(EDGE, 1),
            BorderFactory.createEmptyBorder(scaled(14), scaled(16), scaled(12), scaled(16)),
        )

        panel.add(label(place ?: "Kein Ort gewählt", scaled(15), Font.BOLD, TEXT))
        panel.add(Box.createVerticalStrut(scaled(10)))

        if (times == null) {
            panel.add(label("Keine Zeiten", scaled(14), Font.PLAIN, MUTED))
        } else {
            val next = nextPrayer(times, null, now)?.prayer
            for ((prayer, time) in times.ordered()) {
                panel.add(row(prayer.germanName(), time.format(HHMM), highlight = prayer == next, dim = prayer == Prayer.SUNRISE))
            }
            if (karaha.isNotEmpty()) {
                panel.add(Box.createVerticalStrut(scaled(10)))
                for (w in karaha) {
                    panel.add(row(karahaName(w.kind), "${w.start.format(HHMM)}–${w.end.format(HHMM)}", highlight = false, dim = true, warn = true))
                }
            }
        }

        panel.add(Box.createVerticalStrut(scaled(10)))
        panel.add(label(source, scaled(11), Font.PLAIN, MUTED))
        panel.preferredSize = Dimension(scaled(250), panel.preferredSize.height)
        return panel
    }

    private fun row(name: String, value: String, highlight: Boolean, dim: Boolean, warn: Boolean = false): JPanel {
        val color = when {
            highlight -> ACCENT
            warn -> WARN
            dim -> MUTED
            else -> TEXT
        }
        val style = if (highlight) Font.BOLD else Font.PLAIN
        return JPanel(BorderLayout()).apply {
            isOpaque = highlight
            background = HIGHLIGHT_BG
            border = BorderFactory.createEmptyBorder(scaled(3), scaled(6), scaled(3), scaled(6))
            alignmentX = 0f
            add(label(name, scaled(14), style, color), BorderLayout.WEST)
            add(label(value, scaled(14), style, color).apply { horizontalAlignment = SwingConstants.RIGHT }, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun label(text: String, px: Int, style: Int, color: Color) = JLabel(text).apply {
        font = Font("Segoe UI", style, px)
        foreground = color
        alignmentX = 0f
    }

    private fun karahaName(kind: Karaha.Kind) = when (kind) {
        Karaha.Kind.ISRAK -> "İşrak"
        Karaha.Kind.ZEVAL -> "Zeval"
        Karaha.Kind.ISFIRAR -> "İsfirar"
    }

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val BG = Color(0x0B, 0x0B, 0x0C)
        val EDGE = Color(0x2A, 0x2A, 0x2E)
        val TEXT = Color(0xE8, 0xE2, 0xD0)
        val MUTED = Color(0x9A, 0x96, 0x8A)
        val ACCENT = Color(0xF2, 0xC9, 0x4C)
        val WARN = Color(0xD9, 0x8E, 0x4A)
        val HIGHLIGHT_BG = Color(0x24, 0x20, 0x12)
    }
}
