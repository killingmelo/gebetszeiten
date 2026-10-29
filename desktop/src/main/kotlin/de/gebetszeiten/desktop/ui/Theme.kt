package de.gebetszeiten.desktop.ui

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.metal.DefaultMetalTheme
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * Gemeinsame Farben fuer Popup, Banner und Einstellungen. Dunkel ist das
 * bisherige Popup-Schema (Schwarz, Sand, Gold); hell dieselben Toene auf
 * warmem Papier. Welches gilt, entscheidet Windows (`AppsUseLightTheme`),
 * gelesen bei jedem Oeffnen eines Fensters.
 */
data class Palette(
    val bg: Color,
    val surface: Color,
    val edge: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentBg: Color,
    val warn: Color,
    val onAccent: Color,
)

object Theme {
    val DARK = Palette(
        bg = Color(0x0B, 0x0B, 0x0C),
        surface = Color(0x17, 0x17, 0x19),
        edge = Color(0x2A, 0x2A, 0x2E),
        text = Color(0xE8, 0xE2, 0xD0),
        muted = Color(0x9A, 0x96, 0x8A),
        accent = Color(0xF2, 0xC9, 0x4C),
        accentBg = Color(0x24, 0x20, 0x12),
        warn = Color(0xD9, 0x8E, 0x4A),
        onAccent = Color(0x14, 0x12, 0x0A),
    )

    val LIGHT = Palette(
        bg = Color(0xFB, 0xF8, 0xF1),
        surface = Color(0xF2, 0xEC, 0xDE),
        edge = Color(0xDD, 0xD3, 0xBE),
        text = Color(0x24, 0x20, 0x16),
        muted = Color(0x6E, 0x67, 0x58),
        accent = Color(0x9A, 0x6B, 0x00),
        accentBg = Color(0xF6, 0xE7, 0xB8),
        warn = Color(0xB0, 0x56, 0x12),
        onAccent = Color(0xFF, 0xFF, 0xFF),
    )

    /** Windows-Einstellung „App-Modus“; ohne lesbaren Wert dunkel. */
    fun current(): Palette = if (windowsLight()) LIGHT else DARK

    private fun windowsLight(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "AppsUseLightTheme",
        ) == 1
    }.getOrDefault(false)

    fun font(px: Int, style: Int = Font.PLAIN) = Font("Segoe UI", style, scaled(px))

    /** Fuer arabischen Lesetext: Segoe UI kann Arabisch, Traditional Arabic liest sich ruhiger. */
    fun arabicFont(px: Int): Font {
        val f = Font("Traditional Arabic", Font.PLAIN, scaled(px))
        return if (f.family == "Traditional Arabic") f else Font("Segoe UI", Font.PLAIN, scaled(px))
    }

    /**
     * Swing-Standardaussehen (Metal) in den Farben von [p]. Vor dem ersten
     * Fenster setzen; Schriften ungeskaliert (13 px), [installScaledDefaults]
     * skaliert sie danach mit.
     */
    fun installMetal(p: Palette) {
        MetalLookAndFeel.setCurrentTheme(PaletteMetalTheme(p))
        UIManager.setLookAndFeel(MetalLookAndFeel())
        val d = UIManager.getLookAndFeelDefaults()
        d["TabbedPane.selected"] = ColorUIResource(p.bg)
        d["TabbedPane.contentAreaColor"] = ColorUIResource(p.bg)
        d["TabbedPane.tabAreaBackground"] = ColorUIResource(p.surface)
        d["TabbedPane.unselectedBackground"] = ColorUIResource(p.surface)
        d["TabbedPane.borderHightlightColor"] = ColorUIResource(p.edge)
        d["TabbedPane.light"] = ColorUIResource(p.edge)
        d["TabbedPane.focus"] = ColorUIResource(p.accent)
        d["Button.background"] = ColorUIResource(p.surface)
        d["Button.select"] = ColorUIResource(p.accentBg)
        d["Button.gradient"] = null
        d["ToggleButton.gradient"] = null
        d["CheckBox.gradient"] = null
        d["Slider.gradient"] = null
        d["ScrollBar.gradient"] = null
        d["ComboBox.selectionBackground"] = ColorUIResource(p.accentBg)
        d["ComboBox.selectionForeground"] = ColorUIResource(p.text)
        d["List.selectionBackground"] = ColorUIResource(p.accentBg)
        d["List.selectionForeground"] = ColorUIResource(p.text)
        d["TextField.inactiveBackground"] = ColorUIResource(p.surface)
        d["TextField.inactiveForeground"] = ColorUIResource(p.muted)
        d["ToolTip.background"] = ColorUIResource(p.surface)
        d["ToolTip.foreground"] = ColorUIResource(p.text)
    }
}

/** Metal-Farbschema aus einer [Palette]. */
private class PaletteMetalTheme(private val p: Palette) : DefaultMetalTheme() {
    private fun c(color: Color) = ColorUIResource(color)
    private val font = FontUIResource("Segoe UI", Font.PLAIN, 13)

    override fun getName() = "Gebetszeiten"
    override fun getPrimary1() = c(p.accent)
    override fun getPrimary2() = c(p.accent)
    override fun getPrimary3() = c(p.accentBg)
    override fun getSecondary1() = c(p.muted)
    override fun getSecondary2() = c(p.edge)
    override fun getSecondary3() = c(p.bg)
    override fun getWhite() = c(p.surface)
    override fun getBlack() = c(p.text)
    override fun getControlTextColor() = c(p.text)
    override fun getSystemTextColor() = c(p.text)
    override fun getUserTextColor() = c(p.text)
    override fun getInactiveControlTextColor() = c(p.muted)
    override fun getInactiveSystemTextColor() = c(p.muted)
    override fun getWindowBackground() = c(p.surface)
    override fun getTextHighlightColor() = c(p.accentBg)
    override fun getHighlightedTextColor() = c(p.text)
    override fun getMenuBackground() = c(p.surface)
    override fun getMenuForeground() = c(p.text)
    override fun getMenuSelectedBackground() = c(p.accentBg)
    override fun getMenuSelectedForeground() = c(p.text)
    override fun getControlTextFont() = font
    override fun getSystemTextFont() = font
    override fun getUserTextFont() = font
    override fun getMenuTextFont() = font
    override fun getWindowTitleFont() = FontUIResource("Segoe UI", Font.BOLD, 13)
    override fun getSubTextFont() = FontUIResource("Segoe UI", Font.PLAIN, 11)
}
