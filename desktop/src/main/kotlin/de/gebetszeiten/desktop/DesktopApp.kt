package de.gebetszeiten.desktop

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.desktop.data.BundledTables
import de.gebetszeiten.desktop.data.DesktopCache
import de.gebetszeiten.desktop.data.DesktopSettings
import de.gebetszeiten.desktop.data.Refresher
import de.gebetszeiten.desktop.data.SettingsStore
import de.gebetszeiten.desktop.data.TimesSource
import de.gebetszeiten.desktop.data.loadPlaces
import de.gebetszeiten.desktop.display.DisplayState
import de.gebetszeiten.desktop.display.displayState
import de.gebetszeiten.desktop.notify.EzanPlayer
import de.gebetszeiten.desktop.notify.FocusState
import de.gebetszeiten.desktop.notify.NotifyEvent
import de.gebetszeiten.desktop.notify.dueEvents
import de.gebetszeiten.desktop.notify.notificationEvents
import de.gebetszeiten.desktop.taskbar.TaskbarField
import de.gebetszeiten.desktop.taskbar.Win32Taskbar
import de.gebetszeiten.desktop.ui.SettingsWindow
import de.gebetszeiten.desktop.ui.TodayPopup
import de.gebetszeiten.desktop.ui.Tray
import de.gebetszeiten.desktop.ui.installScaledDefaults
import de.gebetszeiten.net.CompositeDiyanetFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.EventQueue
import java.awt.Rectangle
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.system.exitProcess

private val DATE_DE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Fusszeile des Tages-Popups: woher die Zeiten stammen und von wann. */
fun sourceLine(hasTimes: Boolean, fromCache: Boolean, updatedEpochMs: Long?, zone: ZoneId): String = when {
    !hasTimes -> "Keine amtlichen Zeiten"
    fromCache && updatedEpochMs != null ->
        "Diyanet · Stand ${Instant.ofEpochMilli(updatedEpochMs).atZone(zone).toLocalDate().format(DATE_DE)}"
    else -> "Diyanet · mitgelieferte Tabelle"
}

/**
 * Das Programm: Feld, Tray, Takt, Abruf, Benachrichtigungen.
 *
 * Fadenmodell:
 * - Takt-Faden ([executor], genau einer): [tick] und [watch], und damit aller
 *   veraenderliche Zustand (letzte Anzeige, letzter Lauf, ausgeloeste Ereignisse).
 * - EDT: alles Swing/AWT und der Ezan ([ezan] play/stop nur hier).
 * - [io] (Dispatchers.IO): nur der Abruf; danach rechnet wieder der Takt.
 */
class DesktopApp(private val dir: Path) {
    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "gebetszeiten-takt") }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val store = SettingsStore(dir)
    @Volatile private var settings: DesktopSettings = store.load()

    private val open: (String) -> InputStream? = { DesktopApp::class.java.classLoader.getResourceAsStream(it) }
    private val cache = DesktopCache(dir.resolve("cache.txt"))
    private val times = TimesSource(cache, BundledTables(open))
    private val places = loadPlaces(open)
    private val deLocations: List<OfficialLocation> = readDeLocations()
    private val refresher = Refresher(
        cache,
        CompositeDiyanetFetcher.create(
            nearestPlace = { lat, lng -> DiyanetPlaces.nearest(places, lat, lng) },
            bundledLocationId = { lat, lng -> OfficialLocations.nearest(deLocations, lat, lng)?.diyanetId },
            log = { msg, e -> log("Abruf: $msg (${e.javaClass.simpleName}: ${e.message})") },
        ),
        System::currentTimeMillis,
    )
    private val ticker = Ticker({ ZonedDateTime.now() }, executor, ::tick, ::watch)

    // Nur auf dem EDT angelegt und benutzt (tray wird nach Explorer-Neustart ersetzt).
    private val ezan = EzanPlayer()
    private lateinit var field: TaskbarField
    private lateinit var popup: TodayPopup
    private lateinit var settingsWindow: SettingsWindow
    @Volatile private var tray: Tray? = null
    private var ezanWatch: Timer? = null

    // Fuer das Popup, geschrieben vom Takt.
    @Volatile private var todayTimes: DailyPrayerTimes? = null
    @Volatile private var source: String = sourceLine(false, false, null, ZoneId.systemDefault())

    // Nur auf dem Takt-Faden.
    private var lastState: DisplayState? = null
    private var lastRun: ZonedDateTime? = null
    private val fired = HashSet<NotifyEvent>()
    private var lastTaskbar = 0L

    fun start() {
        SwingUtilities.invokeAndWait {
            installScaledDefaults()
            popup = TodayPopup()
            field = TaskbarField(onClick = ::showToday)
            tray = newTray()
            settingsWindow = SettingsWindow(store, places, onSaved = ::onSaved)
        }
        lastTaskbar = Win32Taskbar.handleValue(Win32Taskbar.taskbar())
        log("Start: Daten in $dir, Ort ${settings.placeName ?: "-"}, Feld ${settings.taskbarField}")
        // Erster Start ohne Ort: gleich die Einstellungen; das Feld zeigt "Keine Zeiten".
        if (settings.coords == null) settingsWindow.show()
        ticker.start()
        refresh(force = false)
    }

    // ---- Takt ------------------------------------------------------------

    private fun tick() {
        val now = ZonedDateTime.now()
        val s = settings
        val prev = lastRun ?: now
        val today = times.daily(s, now.toLocalDate(), now.zone)
        val tomorrow = times.daily(s, now.toLocalDate().plusDays(1), now.zone)
        todayTimes = today
        source = s.coords?.let { (lat, lng) ->
            sourceLine(today != null, cache.get(now.toLocalDate(), lat, lng) != null, cache.updatedEpochMs(lat, lng), now.zone)
        } ?: sourceLine(false, false, null, now.zone)

        val state = displayState(now, today, tomorrow, s.showKaraha, s.exactRemaining)
        if (state != lastState) {
            lastState = state
            if (s.taskbarField) field.show(state)
            tray?.update(state)
            log("Anzeige: ${state.line1} | ${state.line2} | naechste Aenderung ${state.nextChangeAt}")
        }

        val events = listOfNotNull(today, tomorrow).flatMap { notificationEvents(it, s) }
        for (e in dueEvents(events, prev, now)) {
            if (fired.add(e)) fire(e, s)
        }
        fired.removeIf { it.at.isBefore(now.minusDays(1)) }

        // Abruf taeglich nach Mitternacht und nach jedem Gebet.
        val dayChanged = lastRun != null && !now.toLocalDate().isEqual(prev.toLocalDate())
        val prayerPassed = lastRun != null && today != null && Prayer.entries.any { p ->
            p != Prayer.SUNRISE && today.time(p).let { it.isAfter(prev) && !it.isAfter(now) }
        }
        if (dayChanged || prayerPassed) refresh(force = false)
        lastRun = now

        ticker.plan(state.nextChangeAt, events.map { it.at }.filter { it.isAfter(now) }.minOrNull())
    }

    /** Waechter (alle 60 s, Takt-Faden): Feld wieder einhaengen, Tray nach Explorer-Neustart. */
    private fun watch() {
        if (settings.taskbarField) field.reattachIfNeeded()
        val current = Win32Taskbar.handleValue(Win32Taskbar.taskbar())
        // Neues Shell_TrayWnd = Explorer neu gestartet: das alte Tray-Symbol ist weg.
        // Ebenso, wenn das Symbol beim letzten Versuch nicht angelegt werden konnte
        // (Taskleiste noch nicht bereit): dann im naechsten Waechterlauf erneut.
        if (current != 0L && (current != lastTaskbar || tray?.available != true)) {
            lastTaskbar = current
            val state = lastState
            log("Taskleiste neu oder Tray-Symbol fehlt: Tray-Symbol neu anlegen")
            EventQueue.invokeLater {
                tray?.remove()
                tray = newTray().also { t -> state?.let(t::update); t.setEzanPlaying(ezan.isPlaying) }
            }
        }
    }

    private fun fire(e: NotifyEvent, s: DesktopSettings) {
        log("Ereignis: ${e.title} - ${e.text}")
        tray?.toast(e)
        if (!e.playEzan) return
        val file = (if (e.prayer == Prayer.FAJR) s.fajrEzanFile ?: s.ezanFile else s.ezanFile) ?: return
        EventQueue.invokeLater {
            // Toast immer, Ezan nur, wenn Windows nicht auf "nicht stoeren" steht.
            if (FocusState.quiet()) return@invokeLater
            ezan.play(Path.of(file), s.ezanVolume)
            watchEzan()
        }
    }

    // ---- Abruf -----------------------------------------------------------

    private fun refresh(force: Boolean) {
        val s = settings
        if (s.coords == null) return
        io.launch {
            refreshMutex.withLock {
                try {
                    refresher.refresh(s, force)
                    log("Abruf fertig (force=$force)")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Schreibfehler im Cache: protokollieren, weiterlaufen.
                    log("Abruf: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            ticker.tickNow()
        }
    }

    // ---- EDT -------------------------------------------------------------

    private fun newTray() = Tray(
        onToday = ::showToday,
        onSettings = { settingsWindow.show() },
        onToggleField = ::toggleField,
        onStopEzan = ::stopEzan,
        onQuit = ::quit,
    ).also { it.setFieldChecked(settings.taskbarField) }

    /** Vom Einstellungsfenster (EDT), nachdem es gespeichert hat. */
    private fun onSaved(s: DesktopSettings) {
        settings = s
        applyField(s.taskbarField)
        refresh(force = true)
    }

    /** Tray-Menue "Feld in der Taskleiste" (EDT). */
    private fun toggleField() {
        val s = settings.copy(taskbarField = !settings.taskbarField)
        settings = s
        executor.execute { runCatching { store.save(s) }.onFailure { log("Einstellungen: ${it.message}") } }
        applyField(s.taskbarField)
    }

    private fun applyField(on: Boolean) {
        tray?.setFieldChecked(on)
        if (!on) field.hide()
        // Sofort neu zeichnen, auch wenn sich die Anzeige nicht geaendert hat.
        executor.execute { lastState = null }
        ticker.tickNow()
    }

    private fun showToday() {
        val t = todayTimes
        val s = settings
        popup.showAbove(
            anchor = anchor(),
            times = t,
            place = s.placeName,
            karaha = if (t != null && s.showKaraha) Karaha.windows(t) else emptyList(),
            source = source,
        )
    }

    private fun stopEzan() {
        ezan.stop()
        tray?.setEzanPlaying(false)
    }

    /** Haelt "Ezan stoppen" im Menue aktuell, solange der Ezan laeuft. */
    private fun watchEzan() {
        tray?.setEzanPlaying(ezan.isPlaying)
        if (ezanWatch != null) return
        ezanWatch = Timer(2_000) {
            val playing = ezan.isPlaying
            tray?.setEzanPlaying(playing)
            if (!playing) {
                ezanWatch?.stop()
                ezanWatch = null
            }
        }.apply { start() }
    }

    private fun quit() {
        log("Beenden")
        ezan.stop()
        tray?.remove()
        field.hide()
        exitProcess(0)
    }

    private fun anchor(): Rectangle {
        val notify = Win32Taskbar.taskbar()?.let { Win32Taskbar.trayNotify(it) }
        if (notify != null) {
            val b = Win32Taskbar.rect(notify)
            return Rectangle(b.left, b.top, b.width, b.height)
        }
        val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        return Rectangle(screen.x + screen.width - 100, screen.y + screen.height, 100, 0)
    }

    private fun readDeLocations(): List<OfficialLocation> = try {
        open("official/locations-de.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { parseOfficialLocations(it) }.orEmpty()
    } catch (e: IOException) {
        emptyList()
    }
}

internal fun log(msg: String) {
    System.err.println("${java.time.LocalDateTime.now().withNano(0)} $msg")
}
