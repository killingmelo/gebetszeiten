package de.gebetszeiten.desktop.ui

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.core.prayertimes.Prayer
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
    val tray = Tray(
        onToday = { popup.showAbove(anchor(), sampleTimes(), "Nürnberg", Karaha.windows(sampleTimes()), "Diyanet · Stand ${LocalDate.now().format(DE)}") },
        onSettings = { println("Einstellungen") },
        onToggleField = { println("Feld umgeschaltet") },
        onStopEzan = { println("Ezan stoppen") },
        onQuit = { System.exit(0) },
    )
    println("tray.available=${tray.available} quiet=${FocusState.quiet()} scale=${systemScale()}")
    tray.update(DisplayState("Asr", "noch 20+ Min", Tone.NORMAL, "Noch 20+ Min bis Asr", null))
    val now = ZonedDateTime.now()
    tray.toast(NotifyEvent(now, EventKind.PRAYER, Prayer.ASR, "Asr", "Es ist Zeit für Asr", false))
    popup.showAbove(anchor(), sampleTimes(), "Nürnberg", Karaha.windows(sampleTimes()), "Diyanet · Stand ${LocalDate.now().format(DE)}")

    args.firstOrNull { it.startsWith("--shot=") }?.removePrefix("--shot=")?.let { path ->
        Thread.sleep(1500)
        println("popup.isShowing=${popup.isShowing}")
        popup.snapshot()?.let { javax.imageio.ImageIO.write(it, "png", java.io.File(path)) }
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

/** Oberhalb des Infobereichs der Taskleiste, sonst unten rechts im Bild. */
private fun anchor(): Rectangle {
    val notify = Win32Taskbar.taskbar()?.let { Win32Taskbar.trayNotify(it) }
    if (notify != null) {
        val b = Win32Taskbar.rect(notify)
        return Rectangle(b.left, b.top, b.width, b.height)
    }
    return Rectangle(1800, 1040, 100, 40)
}

private fun sampleTimes(): DailyPrayerTimes {
    val zone = ZoneId.of("Europe/Berlin")
    val day = LocalDate.now(zone)
    fun at(h: Int, m: Int) = ZonedDateTime.of(day, LocalTime.of(h, m), zone)
    return DailyPrayerTimes(day, zone, at(5, 40), at(7, 43), at(12, 55), at(15, 10), at(17, 45), at(19, 20))
}
