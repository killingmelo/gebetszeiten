package de.gebetszeiten.desktop

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.HijriDay
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.desktop.data.BundledTables
import de.gebetszeiten.desktop.data.DesktopCache
import de.gebetszeiten.desktop.data.DesktopSettings
import de.gebetszeiten.desktop.data.HijriStore
import de.gebetszeiten.desktop.data.NotifyStyle
import de.gebetszeiten.desktop.data.Refresher
import de.gebetszeiten.desktop.data.ToneChoice
import de.gebetszeiten.desktop.data.SettingsStore
import de.gebetszeiten.desktop.data.TimesSource
import de.gebetszeiten.desktop.data.loadPlaces
import de.gebetszeiten.desktop.display.DisplayState
import de.gebetszeiten.desktop.display.displayState
import de.gebetszeiten.desktop.notify.DayContext
import de.gebetszeiten.desktop.notify.EventKind
import de.gebetszeiten.desktop.notify.EzanPlayer
import de.gebetszeiten.desktop.notify.FocusState
import de.gebetszeiten.desktop.notify.NotifyEvent
import de.gebetszeiten.desktop.notify.Occasion
import de.gebetszeiten.desktop.notify.Readings
import de.gebetszeiten.desktop.notify.ToneCategory
import de.gebetszeiten.desktop.notify.TonePlayer
import de.gebetszeiten.desktop.notify.dueEvents
import de.gebetszeiten.desktop.notify.kandilTonight
import de.gebetszeiten.desktop.notify.notificationEvents
import de.gebetszeiten.desktop.notify.specialDay
import de.gebetszeiten.desktop.notify.todayLine
import de.gebetszeiten.desktop.taskbar.TaskbarField
import de.gebetszeiten.desktop.taskbar.Win32Taskbar
import de.gebetszeiten.desktop.ui.BannerStack
import de.gebetszeiten.desktop.ui.SettingsWindow
import de.gebetszeiten.desktop.ui.TodayInfo
import de.gebetszeiten.desktop.ui.TodayPopup
import de.gebetszeiten.desktop.ui.Tray
import de.gebetszeiten.desktop.ui.installScaledDefaults
import de.gebetszeiten.net.CompositeDiyanetFetcher
import de.gebetszeiten.net.DiyanetDirectFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.awt.EventQueue
import java.awt.Rectangle
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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

/** Lesetext fuer die Windows-Benachrichtigung: erster Absatz, hoechstens ~160 Zeichen. */
fun shortExcerpt(text: String): String {
    val first = text.lineSequence().first()
    return if (first.length <= 160) first else first.take(160).substringBeforeLast(' ') + " …"
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
    private val hijri = HijriStore(dir.resolve("hijri.txt"))
    private val hijriFetcher = DiyanetDirectFetcher()
    private val readings = Readings.load(open)

    // Nur auf dem EDT angelegt und benutzt (tray wird nach Explorer-Neustart ersetzt).
    private val ezan = EzanPlayer()
    private val tones = TonePlayer()
    private lateinit var banners: BannerStack
    private lateinit var field: TaskbarField
    private lateinit var popup: TodayPopup
    private lateinit var settingsWindow: SettingsWindow
    @Volatile private var tray: Tray? = null
    private var ezanWatch: Timer? = null

    // Fuer das Popup, geschrieben vom Takt.
    @Volatile private var todayTimes: DailyPrayerTimes? = null
    @Volatile private var tomorrowTimes: DailyPrayerTimes? = null
    @Volatile private var hijriToday: HijriDay? = null
    @Volatile private var hijriTomorrow: HijriDay? = null
    @Volatile private var source: String = sourceLine(false, false, null, ZoneId.systemDefault())

    /** Toene (und Ezan) still bis dahin (epoch ms); 0 = nicht stumm. */
    @Volatile private var mutedUntil = 0L

    // Nur auf dem Takt-Faden.
    private var lastState: DisplayState? = null
    private var lastRun: ZonedDateTime? = null
    private val fired = HashSet<NotifyEvent>()
    private var lastTaskbar = 0L

    // Nur im Abruf (unter refreshMutex): hoechstens ein Hicri-Versuch pro Tag.
    private var hijriAttempt: LocalDate? = null

    fun start() {
        SwingUtilities.invokeAndWait {
            installScaledDefaults()
            banners = BannerStack(onStopEzan = ::stopEzan)
            popup = TodayPopup()
            field = TaskbarField(onClick = ::showToday)
            tray = newTray()
            settingsWindow = SettingsWindow(store, places, onSaved = ::onSaved)
        }
        lastTaskbar = Win32Taskbar.handleValue(Win32Taskbar.taskbar())
        log("Start: Daten in $dir, Ort ${settings.placeName ?: "-"}, Feld ${settings.taskbarField}, Lesetexte ${readings.size}")
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
        val date = now.toLocalDate()
        val today = times.daily(s, date, now.zone)
        val tomorrow = times.daily(s, date.plusDays(1), now.zone)
        val dayAfter = times.daily(s, date.plusDays(2), now.zone)
        val h0 = hijri.get(date)
        val h1 = hijri.get(date.plusDays(1))
        val h2 = hijri.get(date.plusDays(2))
        todayTimes = today
        tomorrowTimes = tomorrow
        hijriToday = h0
        hijriTomorrow = h1
        if (mutedUntil != 0L && System.currentTimeMillis() >= mutedUntil) {
            mutedUntil = 0L
            tray?.setMuted(false)
        }
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

        val events = listOfNotNull(
            today?.let { DayContext(it, tomorrow, h0, h1) },
            tomorrow?.let { DayContext(it, dayAfter, h1, h2) },
        ).flatMap { notificationEvents(it, s) }
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
        val reading = if (s.showTexts) e.occasion?.let { readings.pick(it, e.at.toLocalDate()) } else null
        val ezanFile = if (e.playEzan) (if (e.prayer == Prayer.FAJR) s.fajrEzanFile ?: s.ezanFile else s.ezanFile) else null
        EventQueue.invokeLater {
            // Anzeige immer; Ton und Ezan nur, wenn Windows nicht auf "nicht stoeren"
            // steht und nicht per Tray stummgeschaltet ist.
            val quiet = FocusState.quiet() || System.currentTimeMillis() < mutedUntil
            when (s.notifyStyle) {
                NotifyStyle.BANNER -> banners.show(e, reading, s.bannerSeconds, ezanPlaying = false)
                NotifyStyle.WINDOWS -> tray?.toast(e, reading?.german?.let(::shortExcerpt))
            }
            if (quiet) return@invokeLater
            if (ezanFile != null) {
                ezan.play(Path.of(ezanFile), s.ezanVolume)
                watchEzan()
            } else {
                val (choice, file) = toneFor(e.tone, s)
                tones.play(choice, file, s.toneVolume)
            }
        }
    }

    private fun toneFor(c: ToneCategory, s: DesktopSettings): Pair<ToneChoice, String?> = when (c) {
        ToneCategory.PRAYER -> s.tonePrayer to s.tonePrayerFile
        ToneCategory.LEAD -> s.toneLead to s.toneLeadFile
        ToneCategory.WARNING -> s.toneWarning to s.toneWarningFile
        ToneCategory.HINT -> s.toneHint to s.toneHintFile
    }

    /** Tray-Menue „Benachrichtigung testen“: ein Hinweis mit dem Text des Tages, ueber den normalen Weg. */
    private fun testNotification() {
        val now = ZonedDateTime.now()
        val e = NotifyEvent(now, EventKind.HINT, null, "Probe", "So sehen Hinweise aus.", false, todayOccasion(now.toLocalDate()))
        executor.execute { fire(e, settings) }
    }

    /** Anlass fuer den Text des Tages: besonderer Tag, Kandil, Cuma, sonst allgemein. */
    private fun todayOccasion(date: LocalDate): Occasion =
        specialDay(hijriToday)
            ?: kandilTonight(date, hijriTomorrow)
            ?: Occasion.CUMA.takeIf { date.dayOfWeek == DayOfWeek.FRIDAY }
            ?: Occasion.GENEL

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
                refreshHijri(s)
            }
            ticker.tickNow()
        }
    }

    /**
     * Hicri-Daten von der Diyanet-Seite, wenn fuer heute + 30 Tage etwas fehlt;
     * hoechstens ein Versuch am Tag. Der Kalender ist ortsunabhaengig, jede
     * Diyanet-ID taugt. Fehler: protokollieren, ohne Hicri weiterlaufen.
     */
    private suspend fun refreshHijri(s: DesktopSettings) {
        val today = LocalDate.now()
        if (hijriAttempt == today || !hijri.needsRefresh(today)) return
        val (lat, lng) = s.coords ?: return
        val id = s.diyanetId ?: cache.cachedLocationId(lat, lng) ?: return
        hijriAttempt = today
        try {
            val days = withTimeout(25_000) { hijriFetcher.fetchHijri(id) }
            hijri.putAll(days, today)
            log("Hicri: ${days.size} Tage (ID $id)")
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) log("Hicri: Zeitueberschreitung") else throw e
        } catch (e: Exception) {
            log("Hicri: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ---- EDT -------------------------------------------------------------

    private fun newTray() = Tray(
        onToday = ::showToday,
        onSettings = { settingsWindow.show() },
        onToggleField = ::toggleField,
        onStopEzan = ::stopEzan,
        onQuit = ::quit,
        onMuteHour = ::muteHour,
        onTestBanner = ::testNotification,
    ).also {
        it.setFieldChecked(settings.taskbarField)
        it.setMuted(mutedUntil != 0L)
    }

    /** Tray „Töne 1 Stunde aus“ (EDT). Der Takt hebt es nach Ablauf wieder auf. */
    private fun muteHour(on: Boolean) {
        mutedUntil = if (on) System.currentTimeMillis() + 3_600_000L else 0L
        if (on) {
            tones.stop()
            stopEzan()
        }
        log(if (on) "Töne aus für 1 Stunde" else "Töne wieder an")
    }

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
        val s = settings
        val date = LocalDate.now()
        val h0 = hijriToday
        val h1 = hijriTomorrow
        popup.showAbove(
            anchor(),
            TodayInfo(
                times = todayTimes,
                next = tomorrowTimes,
                place = s.placeName,
                showKaraha = s.showKaraha,
                source = source,
                hijri = h0,
                todayLine = todayLine(date, h0, h1),
                reading = if (s.showTexts) readings.pick(todayOccasion(date), date) else null,
            ),
        )
    }

    private fun stopEzan() {
        ezan.stop()
        tray?.setEzanPlaying(false)
        banners.setEzanPlaying(false)
    }

    /** Haelt "Ezan stoppen" im Menue und in den Bannern aktuell, solange der Ezan laeuft. */
    private fun watchEzan() {
        tray?.setEzanPlaying(ezan.isPlaying)
        banners.setEzanPlaying(ezan.isPlaying)
        if (ezanWatch != null) return
        ezanWatch = Timer(2_000) {
            val playing = ezan.isPlaying
            tray?.setEzanPlaying(playing)
            banners.setEzanPlaying(playing)
            if (!playing) {
                ezanWatch?.stop()
                ezanWatch = null
            }
        }.apply { start() }
    }

    private fun quit() {
        log("Beenden")
        ezan.stop()
        tones.stop()
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
