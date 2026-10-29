package de.gebetszeiten.desktop.ui

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.HijriDay
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.Nafile
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.display.germanName
import de.gebetszeiten.core.prayertimes.display.nextPrayer
import de.gebetszeiten.desktop.notify.Reading
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private const val WIDTH_PX = 300

/** Was das Tages-Popup zeigt; alles ausser [place]/[source] darf fehlen. */
data class TodayInfo(
    val times: DailyPrayerTimes?,
    val next: DailyPrayerTimes? = null,
    val place: String?,
    val showKaraha: Boolean = true,
    val source: String,
    val hijri: HijriDay? = null,
    val todayLine: String? = null,
    val reading: Reading? = null,
)

/**
 * Kleines randloses Fenster mit den Zeiten des Tages. Schliesst bei
 * Fokusverlust. Alle Masse laufen ueber [scaled] (der Prozess hat Swing-uiScale 1).
 */
class TodayPopup {
    private var dialog: JDialog? = null

    /**
     * [anchor] ist das Rechteck, ueber (oder unter) dem das Fenster sitzt, in
     * Bildschirmpixeln — meist das Tray-Symbol. [now] fuer die Hervorhebung
     * des naechsten Gebets und die Restzeit.
     */
    fun showAbove(anchor: Rectangle, info: TodayInfo, now: ZonedDateTime = ZonedDateTime.now()) {
        SwingUtilities.invokeLater { showNow(anchor, info, now) }
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

    private fun showNow(anchor: Rectangle, info: TodayInfo, now: ZonedDateTime) {
        dispose()
        val p = Theme.current()
        val d = JDialog().apply {
            isUndecorated = true
            isAlwaysOnTop = true
            focusableWindowState = true
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            contentPane = Content(p).build(info, now)
            pack()
            // Fokusverlust schliesst; zuerst muss das Fenster ihn bekommen haben.
            addWindowFocusListener(object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) = dispose()
            })
        }
        val r = scaled(14).toDouble()
        d.shape = java.awt.geom.RoundRectangle2D.Double(0.0, 0.0, d.width.toDouble(), d.height.toDouble(), r, r)
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

    /** Aufbau mit einer Palette; nur auf dem EDT. */
    private class Content(private val p: Palette) {

        fun build(info: TodayInfo, now: ZonedDateTime): JPanel {
            val panel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    g.color = p.bg
                    g.fillRect(0, 0, width, height)
                    g.color = p.edge
                    g.drawRect(0, 0, width - 1, height - 1)
                }
            }
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createEmptyBorder(scaled(14), scaled(16), scaled(12), scaled(16))

            panel.add(label(info.place ?: "Kein Ort gewählt", 15, Font.BOLD, p.text))
            val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
            panel.add(label("$day, ${now.format(DATE)}", 12, Font.PLAIN, p.muted))
            info.hijri?.let { panel.add(label(it.text(), 12, Font.PLAIN, p.muted)) }
            info.todayLine?.let {
                panel.add(Box.createVerticalStrut(scaled(8)))
                panel.add(chip(it))
            }
            panel.add(Box.createVerticalStrut(scaled(12)))

            val times = info.times
            if (times == null) {
                panel.add(label("Keine Zeiten", 14, Font.PLAIN, p.muted))
            } else {
                val next = nextPrayer(times, info.next, now)
                if (next != null) {
                    panel.add(nextCard(next.prayer.germanName(), next.time, now))
                    panel.add(Box.createVerticalStrut(scaled(10)))
                }
                val highlight = next?.prayer?.takeIf { next.time.toLocalDate() == times.date }
                for ((prayer, time) in times.ordered()) {
                    panel.add(row(prayer.germanName(), time.format(HHMM), highlight = prayer == highlight, color = if (prayer == Prayer.SUNRISE) p.muted else p.text))
                }
                if (info.showKaraha) {
                    panel.add(section("Karāha"))
                    for (w in Karaha.windows(times)) {
                        panel.add(row(karahaName(w.kind), span(w.start, w.end), highlight = false, color = p.warn, active = isIn(now, w.start, w.end)))
                    }
                }
                panel.add(section("Freiwillige Gebete"))
                val duha = Nafile.duha(times)
                panel.add(row("Ḍuḥā", span(duha.start, duha.end), highlight = false, color = p.muted, active = isIn(now, duha.start, duha.end)))
                val evvabin = Nafile.awwabin(times)
                panel.add(row("Auwābīn", span(evvabin.start, evvabin.end), highlight = false, color = p.muted, active = isIn(now, evvabin.start, evvabin.end)))
                Nafile.tahajjud(times, info.next?.fajr)?.let { w ->
                    panel.add(row("Tahaǧǧud", span(w.start, w.end), highlight = false, color = p.muted, active = isIn(now, w.start, w.end)))
                }
            }

            info.reading?.let { r ->
                panel.add(Box.createVerticalStrut(scaled(12)))
                panel.add(divider())
                panel.add(Box.createVerticalStrut(scaled(10)))
                panel.add(label("Text des Tages", 11, Font.BOLD, p.accent))
                panel.add(Box.createVerticalStrut(scaled(4)))
                // Gerade, nicht kursiv: Segoe UI Italic hat kein ﷺ.
                panel.add(wrapped(excerpt(r.german), Theme.font(13), p.text))
                panel.add(Box.createVerticalStrut(scaled(4)))
                panel.add(wrapped(r.source, Theme.font(11), p.muted))
            }

            panel.add(Box.createVerticalStrut(scaled(12)))
            panel.add(label(info.source, 11, Font.PLAIN, p.muted))
            panel.preferredSize = Dimension(scaled(WIDTH_PX), panel.preferredSize.height)
            return panel
        }

        private fun nextCard(name: String, at: ZonedDateTime, now: ZonedDateTime): JComponent {
            val card = object : JPanel(BorderLayout()) {
                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = p.accentBg
                    g2.fillRoundRect(0, 0, width, height, scaled(10), scaled(10))
                    g2.dispose()
                }
            }
            card.isOpaque = false
            card.alignmentX = 0f
            card.border = BorderFactory.createEmptyBorder(scaled(8), scaled(10), scaled(8), scaled(10))
            val left = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(label("Nächstes Gebet", 11, Font.PLAIN, p.muted))
                add(label(name, 18, Font.BOLD, p.accent))
            }
            val right = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(label(at.format(HHMM), 22, Font.BOLD, p.text).apply { alignmentX = 1f })
                add(label(remaining(now, at), 11, Font.PLAIN, p.muted).apply { alignmentX = 1f })
            }
            card.add(left, BorderLayout.WEST)
            card.add(right, BorderLayout.EAST)
            card.maximumSize = Dimension(Int.MAX_VALUE, card.preferredSize.height)
            return card
        }

        private fun chip(text: String): JComponent = wrapped("• $text",Theme.font(12, Font.BOLD), p.accent)

        private fun section(title: String): JComponent = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = 0f
            border = BorderFactory.createEmptyBorder(scaled(10), scaled(6), scaled(2), 0)
            add(label(title.uppercase(), 10, Font.BOLD, p.muted), BorderLayout.WEST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        private fun divider(): JComponent = JPanel().apply {
            background = p.edge
            alignmentX = 0f
            preferredSize = Dimension(scaled(WIDTH_PX), 1)
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        }

        private fun row(name: String, value: String, highlight: Boolean, color: Color, active: Boolean = false): JPanel {
            val c = if (highlight) p.accent else color
            val style = if (highlight || active) Font.BOLD else Font.PLAIN
            return JPanel(BorderLayout()).apply {
                isOpaque = highlight || active
                background = p.accentBg
                border = BorderFactory.createEmptyBorder(scaled(3), scaled(6), scaled(3), scaled(6))
                alignmentX = 0f
                add(label(name, 14, style, c), BorderLayout.WEST)
                add(label(value, 14, style, c).apply { horizontalAlignment = SwingConstants.RIGHT }, BorderLayout.EAST)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            }
        }

        private fun label(text: String, px: Int, style: Int, color: Color) = JLabel(text).apply {
            font = Theme.font(px, style)
            foreground = color
            alignmentX = 0f
        }

        private fun wrapped(text: String, f: Font, color: Color): JTextArea {
            val inner = scaled(WIDTH_PX) - scaled(32)
            return JTextArea(text).apply {
                font = f
                foreground = color
                isOpaque = false
                isEditable = false
                isFocusable = false
                lineWrap = true
                wrapStyleWord = true
                border = null
                alignmentX = 0f
                setSize(inner, Short.MAX_VALUE.toInt())
                preferredSize = Dimension(inner, preferredSize.height)
                maximumSize = preferredSize
            }
        }

        private fun excerpt(s: String) = if (s.length <= 220) s else s.take(220).substringBeforeLast(' ') + " …"

        private fun span(a: ZonedDateTime, b: ZonedDateTime) = "${a.format(HHMM)}–${b.format(HHMM)}"

        private fun isIn(now: ZonedDateTime, a: ZonedDateTime, b: ZonedDateTime) = !now.isBefore(a) && now.isBefore(b)
    }

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

        fun karahaName(kind: Karaha.Kind) = when (kind) {
            Karaha.Kind.ISRAK -> "Išrāq"
            Karaha.Kind.ZEVAL -> "Zawāl"
            Karaha.Kind.ISFIRAR -> "Iṣfirār"
        }

        /** „in 1 Std 12 Min“ / „in 8 Min“ / „jetzt“. */
        fun remaining(now: ZonedDateTime, at: ZonedDateTime): String {
            val min = Duration.between(now, at).toMinutes().coerceAtLeast(0)
            return when {
                min == 0L -> "jetzt"
                min < 60 -> "in $min Min"
                else -> "in ${min / 60} Std ${min % 60} Min"
            }
        }
    }
}
