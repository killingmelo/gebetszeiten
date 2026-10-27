package de.gebetszeiten.desktop.ui

import de.gebetszeiten.desktop.notify.EventKind
import de.gebetszeiten.desktop.notify.NotifyEvent
import de.gebetszeiten.desktop.notify.Reading
import java.awt.BorderLayout
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer

private const val WIDTH_PX = 380
private const val GAP_PX = 10
private const val MAX_BANNERS = 4

/**
 * Eigene Einblendungen unten rechts ueber der Taskleiste, gestapelt (neueste
 * unten). Nehmen keinen Fokus, schliessen nach [seconds] (0 = bleiben),
 * solange die Maus nicht darueber steht. Nur auf dem EDT benutzen.
 */
class BannerStack(private val onStopEzan: () -> Unit) {
    private val open = mutableListOf<BannerWindow>()

    fun show(e: NotifyEvent, reading: Reading?, seconds: Int, ezanPlaying: Boolean) {
        while (open.size >= MAX_BANNERS) open.first().close()
        val b = BannerWindow(e, reading, Theme.current(), seconds, ezanPlaying, onStopEzan, ::relayout) { closed ->
            open.remove(closed)
            relayout()
        }
        open += b
        relayout()
        b.window.isVisible = true
    }

    /** Ezan zu Ende: Knopf in allen Bannern weg. */
    fun setEzanPlaying(playing: Boolean) = open.forEach { it.setEzanPlaying(playing) }

    fun closeAll() = open.toList().forEach { it.close() }

    /** Handprobe: Lesetext des neuesten Banners aufklappen. */
    internal fun expandLast() = open.lastOrNull()?.toggleMore()

    /** Oberstes Banner als Bild (Handprobe), sonst null. */
    fun snapshot(): BufferedImage? = open.lastOrNull()?.snapshot()

    private fun relayout() {
        val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val gap = scaled(GAP_PX)
        var bottom = area.y + area.height - gap
        for (b in open.asReversed()) {
            val size = b.window.size
            b.window.setLocation(area.x + area.width - size.width - gap, bottom - size.height)
            bottom -= size.height + gap
        }
    }
}

internal class BannerWindow(
    private val event: NotifyEvent,
    private val reading: Reading?,
    private val p: Palette,
    seconds: Int,
    ezanPlaying: Boolean,
    private val onStopEzan: () -> Unit,
    private val onResized: () -> Unit,
    private val onClosed: (BannerWindow) -> Unit,
) {
    val window = JWindow()
    private val stopButton = button("Ezan stoppen", primary = true).apply {
        isVisible = ezanPlaying
        addActionListener { onStopEzan(); isVisible = false; refit(); onResized() }
    }
    private val more = JPanel()
    private val moreLink = JLabel()
    private var short: JComponent? = null
    private var closed = false

    // Steht die Maus darueber (auch ueber Knopf/Text), wartet es eine Runde.
    private val timer: Timer? = if (seconds > 0) {
        Timer(seconds * 1000) { if (window.contentPane.getMousePosition(true) == null) close() }.apply { isRepeats = true }
    } else {
        null
    }

    init {
        window.isAlwaysOnTop = true
        window.focusableWindowState = false
        window.type = java.awt.Window.Type.POPUP
        window.contentPane = build()
        refit()
        timer?.start()
    }

    fun setEzanPlaying(playing: Boolean) {
        if (stopButton.isVisible != playing) {
            stopButton.isVisible = playing
            refit()
            onResized()
        }
    }

    /** Lesetext auf- oder zuklappen. */
    fun toggleMore() {
        if (reading == null) return
        more.isVisible = !more.isVisible
        short?.isVisible = !more.isVisible
        moreLink.text = if (more.isVisible) "Weniger" else "Weiterlesen"
        // Wer liest, will nicht, dass es verschwindet.
        if (more.isVisible) timer?.stop()
        refit()
        onResized()
    }

    fun close() {
        if (closed) return
        closed = true
        timer?.stop()
        window.dispose()
        onClosed(this)
    }

    fun snapshot(): BufferedImage {
        val c = window.contentPane
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        c.paint(g)
        g.dispose()
        return img
    }

    private fun refit() {
        window.pack()
        val r = scaled(14).toDouble()
        window.shape = RoundRectangle2D.Double(0.0, 0.0, window.width.toDouble(), window.height.toDouble(), r, r)
    }

    private fun accentFor(kind: EventKind): Color = when (kind) {
        EventKind.KARAHA_WARNING, EventKind.END_WARNING -> p.warn
        else -> p.accent
    }

    private fun kindLabel(kind: EventKind): String = when (kind) {
        EventKind.PRAYER -> "Gebetszeit"
        EventKind.LEAD -> "Erinnerung"
        EventKind.KARAHA_WARNING, EventKind.END_WARNING -> "Warnung"
        EventKind.HINT -> "Hinweis"
    }

    private fun build(): JComponent {
        val accent = accentFor(event.kind)
        val root = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.color = p.bg
                g2.fillRect(0, 0, width, height)
                g2.color = accent
                g2.fillRect(0, 0, scaled(4), height)
                g2.color = p.edge
                g2.drawRect(0, 0, width - 1, height - 1)
            }

            // Feste Breite, Hoehe nach Inhalt (aendert sich beim Aufklappen).
            override fun getPreferredSize(): Dimension = super.getPreferredSize().also { it.width = scaled(WIDTH_PX) }
        }
        root.border = BorderFactory.createEmptyBorder(scaled(12), scaled(18), scaled(12), scaled(14))

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val head = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = 0f
            add(label("${kindLabel(event.kind)} · ${event.at.format(HHMM)}", 11, Font.PLAIN, p.muted), BorderLayout.WEST)
            add(label("×", 16, Font.PLAIN, p.muted).apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = "Schließen"
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = close()
                })
            }, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        body.add(head)
        body.add(Box.createVerticalStrut(scaled(4)))
        body.add(label(event.title, 16, Font.BOLD, accent))
        body.add(Box.createVerticalStrut(scaled(2)))
        body.add(wrapped(event.text, Theme.font(13), p.text))

        if (reading != null) {
            body.add(Box.createVerticalStrut(scaled(10)))
            // Gerade, nicht kursiv: Segoe UI Italic hat kein ﷺ.
            short = wrapped(excerpt(reading.german.lines().first()), Theme.font(13), p.text)
            body.add(short)
            more.apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentX = 0f
                isVisible = false
                add(wrapped(reading.german, Theme.font(13), p.text))
                if (reading.arabic.isNotBlank()) {
                    add(Box.createVerticalStrut(scaled(8)))
                    add(wrapped(reading.arabic, Theme.arabicFont(19), p.text, rtl = true))
                }
                add(Box.createVerticalStrut(scaled(6)))
                add(wrapped(reading.source + (reading.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), Theme.font(11), p.muted))
                add(wrapped("Deutsch: sinngemäße Übersetzung", Theme.font(10), p.muted))
            }
            body.add(more)
            moreLink.apply {
                text = "Weiterlesen"
                font = Theme.font(12, Font.BOLD)
                foreground = accent
                alignmentX = 0f
                border = BorderFactory.createEmptyBorder(scaled(4), 0, 0, 0)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = toggleMore()
                })
            }
            body.add(moreLink)
        }

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, scaled(6), 0)).apply {
            isOpaque = false
            add(stopButton)
            add(button("Schließen", primary = false).apply { addActionListener { close() } })
        }
        root.add(body, BorderLayout.CENTER)
        root.add(buttons, BorderLayout.SOUTH)
        buttons.border = BorderFactory.createEmptyBorder(scaled(10), 0, 0, 0)

        return root
    }

    private fun excerpt(s: String) = if (s.length <= EXCERPT) s else s.take(EXCERPT).substringBeforeLast(' ') + " …"

    private fun label(text: String, px: Int, style: Int, color: Color) = JLabel(text).apply {
        font = Theme.font(px, style)
        foreground = color
        alignmentX = 0f
    }

    /** Umbrechender Text in fester Breite; [rtl] fuer Arabisch. */
    private fun wrapped(text: String, f: Font, color: Color, rtl: Boolean = false): JTextArea {
        val inner = scaled(WIDTH_PX) - scaled(18) - scaled(14)
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
            if (rtl) componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
            setSize(inner, Short.MAX_VALUE.toInt())
            preferredSize = Dimension(inner, preferredSize.height)
            maximumSize = preferredSize
        }
    }

    private fun button(text: String, primary: Boolean) = object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = when {
                primary && model.isRollover -> p.accent.brighter()
                primary -> p.accent
                model.isRollover -> p.edge
                else -> p.surface
            }
            val arc = scaled(8)
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }.apply {
        font = Theme.font(12, Font.BOLD)
        foreground = if (primary) p.onAccent else p.text
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = BorderFactory.createEmptyBorder(scaled(5), scaled(12), scaled(5), scaled(12))
    }

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val EXCERPT = 180
    }
}
