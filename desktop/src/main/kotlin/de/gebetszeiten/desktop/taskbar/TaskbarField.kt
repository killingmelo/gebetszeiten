package de.gebetszeiten.desktop.taskbar

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import de.gebetszeiten.desktop.display.DisplayState
import de.gebetszeiten.desktop.display.Tone
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.text.AttributedString
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlin.math.ceil

/**
 * Das zweizeilige Feld links vom Infobereich, im Stil der Uhr. Ein rahmenloses
 * [JWindow], per `SetParent` Kind der Taskleiste — so verschwindet es mit ihr
 * (Vollbild, automatisch ausblenden) und schwebt nie allein ueber dem Bild.
 *
 * Hintergrund: das Feld ist deckend (Alpha-Schicht 255, siehe
 * [Win32Taskbar.attach]) und malt in der Farbe der Leiste. Die wird beim
 * Einhaengen direkt links neben dem Feld vom Bildschirm abgelesen (Mica faerbt
 * sie je nach Hintergrundbild ein); klappt das nicht, gelten die
 * Windows-11-Toene. Echte Durchsicht pro Pixel braeuchte UpdateLayeredWindow
 * statt Swing-Malen — fuer ein ruhiges Feld nicht noetig. Wechselt das
 * Hintergrundbild, gilt die neue Farbe erst nach dem naechsten Einhaengen.
 *
 * Alle Methoden duerfen von jedem Thread kommen; gearbeitet wird auf dem EDT.
 */
class TaskbarField(private val onClick: () -> Unit) {
    private var window: JWindow? = null
    private var hwnd: HWND? = null
    private var parent: HWND? = null
    private var state: DisplayState? = null
    private var background: Color? = null
    private var wanted = false
    private val view = FieldView().apply {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) onClick()
            }
        })
    }

    fun show(state: DisplayState) = onEdt {
        this.state = state
        wanted = true
        view.toolTipText = state.tooltip
        layout()
    }

    /**
     * Vom 60-s-Waechter: haengt das Feld nach einem Explorer-Neustart wieder
     * ein; findet sich keine Taskleiste, wird es versteckt (das Tray-Symbol
     * uebernimmt). Die `TaskbarCreated`-Nachricht wird nicht abgehoert — der
     * Waechter reicht und braucht kein eigenes Nachrichtenfenster.
     * Nach [hide] (Feld abgeschaltet) bleibt es aus, bis wieder [show] kommt.
     */
    fun reattachIfNeeded() = onEdt {
        if (!wanted) return@onEdt
        val h = hwnd ?: return@onEdt layout()
        val taskbar = Win32Taskbar.taskbar()?.takeIf { Win32Taskbar.isWindow(it) }
        val alive = Win32Taskbar.isWindow(h)
        val currentParent = if (alive) Win32Taskbar.parentOf(h) else 0L
        when (reattachDecision(alive, currentParent, taskbar?.let(Win32Taskbar::handleValue))) {
            Reattach.KEEP -> layout() // Leiste kann sich veraendert haben (DPI, Infobereich breiter)
            Reattach.ATTACH -> {
                parent = null
                background = null
                layout()
            }
            Reattach.RECREATE -> {
                dropWindow()
                layout()
            }
            Reattach.HIDE -> hideNow()
        }
    }

    fun hide() = onEdt {
        wanted = false
        hideNow()
    }

    private fun hideNow() {
        hwnd?.takeIf(Win32Taskbar::isWindow)?.let(Win32Taskbar::hide)
    }

    /** Das Fenster ist mit der alten Leiste untergegangen: Swing-Reste wegwerfen. */
    private fun dropWindow() {
        window?.let { runCatching { it.dispose() } }
        window = null
        hwnd = null
        parent = null
        background = null
    }

    /** Groesse, Lage und Einhaengen aus dem aktuellen Zustand. */
    private fun layout() {
        val current = state ?: return
        val taskbar = Win32Taskbar.taskbar() ?: return hideNow()
        val tray = Win32Taskbar.trayNotify(taskbar) ?: return hideNow()
        val taskbarBox = Win32Taskbar.rect(taskbar)
        val trayBox = Win32Taskbar.rect(tray)

        val scale = Win32Taskbar.dpiScale(taskbar)
        val light = Win32Taskbar.isLightTheme()
        val w = ensureWindow()
        view.font = Font("Segoe UI", Font.PLAIN, 1).deriveFont((9.0 * scale * 96.0 / 72.0).toFloat())
        view.light = light
        view.state = current

        val textWidth = maxOf(lineWidth(current.line1, view.font), lineWidth(current.line2, view.font))
        val box = fieldBox(taskbarBox, trayBox, width = textWidth + 8)
            ?: return hideNow()

        val h = hwnd!!
        if (parent == null || Win32Taskbar.handleValue(parent) != Win32Taskbar.parentOf(h)) {
            background = sampleBackground(taskbarBox, box) ?: if (light) LIGHT_BAR else DARK_BAR
            // Erst an die richtige Bildschirmstelle, damit nichts woanders aufblitzt.
            w.setBounds(taskbarBox.left + box.left, taskbarBox.top + box.top, box.width, box.height)
            if (!w.isVisible) w.isVisible = true
            Win32Taskbar.attach(h, taskbar)
            parent = taskbar
        }
        view.barColor = background ?: if (light) LIGHT_BAR else DARK_BAR
        // Als Kind zaehlen die Koordinaten ab der Taskleiste. Nur Win32 setzt die
        // Lage: Swing rechnet in Bildschirmkoordinaten und schoebe das Feld
        // sonst um die Lage der Leiste nach unten (gemessen: y doppelt). Die
        // neue Groesse erfaehrt Swing aus WM_SIZE.
        Win32Taskbar.place(h, box)
        w.validate()
        view.repaint()
    }

    private fun ensureWindow(): JWindow {
        window?.let { return it }
        val w = JWindow()
        w.focusableWindowState = false
        w.contentPane = view
        w.addNotify() // Fenster-Handle entsteht erst, wenn das Fenster darstellbar ist
        hwnd = HWND(Native.getWindowPointer(w))
        window = w
        return w
    }

    /** Farbe der Leiste knapp links vom Feld, solange das Feld dort noch nicht sitzt. */
    private fun sampleBackground(taskbar: Box, box: Box): Color? = runCatching {
        val x = taskbar.left + box.left - 4
        val y = taskbar.top + taskbar.height / 2
        Robot().getPixelColor(x, y)
    }.getOrNull()

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    private class FieldView : JComponent() {
        var state: DisplayState? = null
        var light = false
        var barColor: Color = DARK_BAR

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.color = barColor
            g2.fillRect(0, 0, width, height)
            val s = state ?: return
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            val fm = g2.getFontMetrics(font)
            val lines = listOf(s.line1, s.line2).filter { it.isNotEmpty() }
            val lineHeight = fm.ascent + fm.descent
            var y = (height - lineHeight * lines.size) / 2 + fm.ascent
            lines.forEachIndexed { i, text ->
                // Erste Zeile (Gebet) in Textfarbe, zweite (Rest/Karaha) im Farbton.
                g2.color = if (i == 0 && s.tone != Tone.NO_TIMES) textColor(light) else toneColor(s.tone, light)
                val layout = lineLayout(text, font) ?: return@forEachIndexed
                layout.draw(g2, (width - layout.advance), y.toFloat())
                y += lineHeight
            }
        }
    }

    companion object {
        private val FRC = FontRenderContext(null, true, true)

        /**
         * Segoe UI kennt „⚠" nicht, und Java faellt bei einer echten Schrift
         * nicht selbst auf eine andere zurueck (sonst erscheint ein Kaestchen).
         * Darum Zeichen, die Segoe UI fehlen, aus Segoe UI Symbol/Emoji; der
         * Variantenwaehler U+FE0F bleibt weg (unsichtbar, waere sonst Kaestchen).
         */
        private fun lineLayout(text: String, font: Font): TextLayout? {
            val clean = text.replace("\uFE0F", "")
            if (clean.isEmpty()) return null
            val fallbacks = listOf("Segoe UI Symbol", "Segoe UI Emoji").map { Font(it, font.style, 1).deriveFont(font.size2D) }
            val styled = AttributedString(clean)
            styled.addAttribute(TextAttribute.FONT, font)
            var i = 0
            while (i < clean.length) {
                val cp = clean.codePointAt(i)
                val n = Character.charCount(cp)
                if (!font.canDisplay(cp)) {
                    fallbacks.firstOrNull { it.canDisplay(cp) }?.let { styled.addAttribute(TextAttribute.FONT, it, i, i + n) }
                }
                i += n
            }
            return TextLayout(styled.iterator, FRC)
        }

        private fun lineWidth(text: String, font: Font): Int =
            lineLayout(text, font)?.advance?.let { ceil(it).toInt() } ?: 0

        private val DARK_BAR = Color(0x1C, 0x1C, 0x1C)
        private val LIGHT_BAR = Color(0xEE, 0xEE, 0xEE)

        private fun textColor(light: Boolean) = if (light) Color(0x1B, 0x1B, 0x1B) else Color.WHITE

        fun toneColor(tone: Tone, light: Boolean): Color = when (tone) {
            Tone.NORMAL -> textColor(light)
            Tone.URGENT -> Color(0xFF, 0x6B, 0x5A)
            Tone.KARAHA_SOON -> Color(0xF2, 0xB2, 0x33)
            Tone.KARAHA_ACTIVE -> Color(0xFF, 0x8A, 0x3D)
            Tone.NO_TIMES -> Color(0x9A, 0x9A, 0x9A)
        }
    }
}

/**
 * Handprobe (`:desktop:run --args="--demo-field"`): zeigt das Feld mit einem
 * festen Zustand und haengt es alle 60 s bei Bedarf neu ein.
 */
fun runFieldDemo(args: Array<String>) {
    val tone = args.firstNotNullOfOrNull { a -> Tone.entries.firstOrNull { "--tone=${it.name}" == a } } ?: Tone.NORMAL
    val state = when (tone) {
        Tone.KARAHA_SOON -> DisplayState("Dhuhr", "⚠️ Karaha ab 13:01", tone, "Noch 20+ Min bis Dhuhr · ⚠️ Karaha ab 13:01", null)
        Tone.KARAHA_ACTIVE -> DisplayState("Dhuhr", "⚠️ Karaha bis 13:21", tone, "Noch 10 Min bis Dhuhr · ⚠️ Karaha bis 13:21", null)
        Tone.URGENT -> DisplayState("Asr", "noch 7 Min", tone, "Noch 7 Min bis Asr", null)
        Tone.NO_TIMES -> DisplayState("Keine Zeiten", "", tone, "Keine amtlichen Zeiten für diesen Ort", null)
        Tone.NORMAL -> DisplayState("Asr", "noch 20+ Min", tone, "Noch 20+ Min bis Asr", null)
    }
    val field = TaskbarField(onClick = { println("Feld angeklickt") })
    field.show(state)
    val timer = javax.swing.Timer(60_000) { field.reattachIfNeeded() }
    timer.start()
}
