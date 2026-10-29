package de.gebetszeiten.desktop.ui

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.HijriDay
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.desktop.notify.Occasion
import de.gebetszeiten.desktop.notify.Reading
import de.gebetszeiten.desktop.notify.Readings
import de.gebetszeiten.desktop.display.DisplayState
import de.gebetszeiten.desktop.display.Tone
import de.gebetszeiten.desktop.notify.EventKind
import de.gebetszeiten.desktop.notify.EzanPlayer
import de.gebetszeiten.desktop.notify.FocusState
import de.gebetszeiten.desktop.notify.NotifyEvent
import de.gebetszeiten.desktop.taskbar.Win32Taskbar
import java.awt.Rectangle
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Handprobe `--demo-tray`: Symbol im Infobereich, ein Toast und das Popup mit
 * Beispielzeiten. Mit `--ezan=<Datei>` spielt es die Datei einmal (Lautstaerke
 * 20) und stoppt sie nach 1,5 s. Beendet sich selbst nach `--seconds=N` (20).
 */
fun runTrayDemo(args: Array<String>) {
    installScaledDefaults()
    val seconds = args.firstNotNullOfOrNull { it.removePrefix("--seconds=").takeIf { _ -> it.startsWith("--seconds=") } }
        ?.toIntOrNull() ?: 20
    val ezanFile = args.firstNotNullOfOrNull { it.removePrefix("--ezan=").takeIf { _ -> it.startsWith("--ezan=") } }

    val popup = TodayPopup()
    val info = TodayInfo(
        times = sampleTimes(),
        next = sampleTimes(1),
        place = "Nürnberg",
        source = "Diyanet · Stand ${LocalDate.now().format(DE)}",
        hijri = HijriDay(16, 5, 1448),
        todayLine = "Ǧumʿa",
        reading = Reading("genel", "", "Beispieltext des Tages für die Handprobe.", "Quelle, Werk, S. 1", ""),
    )
    val tray = Tray(
        onToday = { popup.showAbove(anchor(), info) },
        onSettings = { println("Einstellungen") },
        onToggleField = { println("Feld umgeschaltet") },
        onStopEzan = { println("Ezan stoppen") },
        onQuit = { System.exit(0) },
    )
    println("tray.available=${tray.available} quiet=${FocusState.quiet()} ${FocusState.describe()} scale=${systemScale()}")
    tray.update(DisplayState("ʿAṣr", "noch 20+ Min", Tone.NORMAL, "Noch 20+ Min bis ʿAṣr", null))
    val now = ZonedDateTime.now()
    tray.toast(NotifyEvent(now, EventKind.PRAYER, Prayer.ASR, "ʿAṣr", "Es ist Zeit für ʿAṣr", false))
    popup.showAbove(anchor(), info)

    args.firstOrNull { it.startsWith("--shot=") }?.removePrefix("--shot=")?.let { path ->
        Thread.sleep(1500)
        println("popup.isShowing=${popup.isShowing}")
        popup.snapshot()?.let { javax.imageio.ImageIO.write(it, "png", java.io.File(path)) }
    }

    // --banner-shot=x.png: Banner mit Beispiel-Hinweis und aufgeklapptem Lesetext.
    args.firstOrNull { it.startsWith("--banner-shot=") }?.removePrefix("--banner-shot=")?.let { path ->
        val readings = Readings.load { TrayDemoRes::class.java.classLoader.getResourceAsStream(it) }
        val reading = readings.pick(Occasion.CUMA, LocalDate.now()) ?: info.reading
        val stack = javax.swing.SwingUtilities.invokeAndWait {
            bannerDemo = BannerStack(onStopEzan = { println("Ezan stoppen (Banner)") })
            bannerDemo!!.show(
                NotifyEvent(ZonedDateTime.now(), EventKind.HINT, null, "Ǧumʿa", "Ǧumʿa mubāraka. Sūrat al-Kahf lesen, viel Ṣalawāt.", false, Occasion.CUMA),
                reading, 0, ezanPlaying = true,
            )
        }
        Thread.sleep(800)
        javax.swing.SwingUtilities.invokeAndWait {
            if (args.contains("--banner-open")) bannerDemo!!.expandLast()
        }
        Thread.sleep(300)
        javax.swing.SwingUtilities.invokeAndWait {
            bannerDemo!!.snapshot()?.let { javax.imageio.ImageIO.write(it, "png", java.io.File(path)) }
        }
        println("banner: lesetexte=${readings.size} stack=$stack")
    }

    if (ezanFile != null) {
        val player = EzanPlayer()
        player.play(Path.of(ezanFile), 20)
        Thread.sleep(300)
        println("ezan.isPlaying(nach 0,3 s)=${player.isPlaying}")
        Thread.sleep(1200)
        player.stop()
        println("ezan.isPlaying(nach stop)=${player.isPlaying}")
        player.play(Path.of("gibt-es-nicht.mp3"), 20)
        println("ezan.isPlaying(fehlende Datei)=${player.isPlaying}")
    }
    Thread.sleep(seconds * 1000L)
    tray.remove()
    System.exit(0)
}

private val DE = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

private var bannerDemo: BannerStack? = null

private object TrayDemoRes

/** Oberhalb des Infobereichs der Taskleiste, sonst unten rechts im Bild. */
private fun anchor(): Rectangle {
    val notify = Win32Taskbar.taskbar()?.let { Win32Taskbar.trayNotify(it) }
    if (notify != null) {
        val b = Win32Taskbar.rect(notify)
        return Rectangle(b.left, b.top, b.width, b.height)
    }
    return Rectangle(1800, 1040, 100, 40)
}

private fun sampleTimes(plusDays: Long = 0): DailyPrayerTimes {
    val zone = ZoneId.of("Europe/Berlin")
    val day = LocalDate.now(zone).plusDays(plusDays)
    fun at(h: Int, m: Int) = ZonedDateTime.of(day, LocalTime.of(h, m), zone)
    return DailyPrayerTimes(day, zone, at(5, 40), at(7, 43), at(12, 55), at(15, 10), at(17, 45), at(19, 20))
}
