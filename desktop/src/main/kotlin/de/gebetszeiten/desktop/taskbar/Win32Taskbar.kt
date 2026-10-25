package de.gebetszeiten.desktop.taskbar

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** Was jna-platform 5.14 nicht mitbringt (beides ab Windows 10 1607/1703). */
private interface User32Dpi : StdCallLibrary {
    fun GetDpiForWindow(hWnd: HWND): Int
    fun SetProcessDpiAwarenessContext(value: Pointer): Boolean

    companion object {
        val INSTANCE: User32Dpi = Native.load("user32", User32Dpi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/**
 * Duenne Win32-Schicht um die Windows-11-Taskleiste. Seit Windows 11 gibt es
 * keine DeskBands mehr; wie TrafficMonitor haengt sich das Feld als
 * Kindfenster in `Shell_TrayWnd` und sitzt links von `TrayNotifyWnd`.
 */
object Win32Taskbar {
    private val user32 = User32.INSTANCE

    /** `DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2` */
    private const val PER_MONITOR_AWARE_V2 = -4L

    /**
     * Vor dem ersten AWT-Fenster aufrufen. Der Prozess rechnet in echten
     * Bildschirmpixeln: Per-Monitor-V2 fuer Win32, und `sun.java2d.uiScale=1`,
     * damit Swing nicht zusaetzlich skaliert — sonst passen die Koordinaten
     * von `GetWindowRect` und Swing nicht zusammen. Schriftgroessen
     * skaliert das Feld selbst mit [dpiScale]. Gemessen bei 125 %: so stimmen
     * Swing-Groesse und Fenstergroesse ueberein; mit zusaetzlich
     * `sun.java2d.uiScale.enabled=false` teilte AWT die Fenstergroesse
     * trotzdem durch 1,25 und malte nur 4/5 des Felds.
     * GDI statt Direct3D: fuer ein geschichtetes Kindfenster der sichere Weg
     * (ohne den Schalter nicht ausprobiert).
     */
    fun prepareProcess() {
        System.setProperty("sun.java2d.uiScale", "1")
        System.setProperty("sun.java2d.d3d", "false")
        // Schlaegt fehl, wenn java.exe die DPI-Stufe schon per Manifest setzt
        // (ERROR_ACCESS_DENIED) — dann gilt die des Manifests; harmlos.
        runCatching { User32Dpi.INSTANCE.SetProcessDpiAwarenessContext(Pointer(PER_MONITOR_AWARE_V2)) }
    }

    fun taskbar(): HWND? = user32.FindWindow("Shell_TrayWnd", null)

    fun trayNotify(taskbar: HWND): HWND? = user32.FindWindowEx(taskbar, null, "TrayNotifyWnd", null)

    fun rect(h: HWND): Box {
        val r = RECT()
        user32.GetWindowRect(h, r)
        return Box(r.left, r.top, r.right, r.bottom)
    }

    fun isWindow(h: HWND?): Boolean = h != null && user32.IsWindow(h)

    /** `GetParent` als Zahl (0 = keines), fuer [reattachDecision]. */
    fun parentOf(child: HWND): Long = handleValue(user32.GetParent(child))

    fun handleValue(h: HWND?): Long = h?.pointer?.let { Pointer.nativeValue(it) } ?: 0L

    /**
     * Macht [child] zum Kind von [parent]: erst Stil `WS_CHILD|WS_VISIBLE`
     * statt `WS_POPUP` (so verlangt es die SetParent-Doku), dann einhaengen.
     *
     * Danach `WS_EX_LAYERED` (deckend, Alpha 255): Ohne bekommt das Kind keine
     * eigene Flaeche bei DWM, und die XAML-/Mica-Ebene der Windows-11-Leiste
     * liegt darueber — gemessen: das Feld schimmerte nur schwach durch.
     * Mit eigener Schicht liegt es sichtbar oben. (Geschichtete Kindfenster
     * gibt es ab Windows 8.)
     */
    fun attach(child: HWND, parent: HWND) {
        val style = user32.GetWindowLong(child, WinUser.GWL_STYLE)
        val childStyle = (style and WinUser.WS_POPUP.inv() and WinUser.WS_CAPTION.inv()) or
            WinUser.WS_CHILD or WinUser.WS_VISIBLE
        user32.SetWindowLong(child, WinUser.GWL_STYLE, childStyle)
        user32.SetParent(child, parent)
        val ex = user32.GetWindowLong(child, WinUser.GWL_EXSTYLE)
        user32.SetWindowLong(child, WinUser.GWL_EXSTYLE, ex or WinUser.WS_EX_LAYERED)
        user32.SetLayeredWindowAttributes(child, 0, 255.toByte(), WinUser.LWA_ALPHA)
    }

    /** Lage und Groesse in Koordinaten des Elternfensters, ganz oben unter dessen Kindern. */
    fun place(child: HWND, box: Box) {
        user32.SetWindowPos(
            child, HWND(Pointer.createConstant(0)), // HWND_TOP
            box.left, box.top, box.width, box.height,
            SWP_NOACTIVATE or SWP_SHOWWINDOW,
        )
    }

    fun hide(child: HWND) {
        user32.ShowWindow(child, WinUser.SW_HIDE)
    }

    /** Taskleiste hell? `SystemUsesLightTheme` = 1. Fehlt der Wert: dunkel (Windows-11-Standard). */
    fun isLightTheme(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "SystemUsesLightTheme",
        ) == 1
    }.getOrDefault(false)

    /** 1.0 bei 96 dpi, 1.5 bei 144 dpi … */
    fun dpiScale(h: HWND): Double {
        val dpi = runCatching { User32Dpi.INSTANCE.GetDpiForWindow(h) }.getOrDefault(0)
        return if (dpi > 0) dpi / 96.0 else 1.0
    }

    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_SHOWWINDOW = 0x0040
}
