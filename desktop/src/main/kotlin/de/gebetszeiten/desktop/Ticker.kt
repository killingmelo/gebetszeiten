package de.gebetszeiten.desktop

import de.gebetszeiten.core.prayertimes.display.BOUNDARY_SETTLE_MS
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Laenger schlaeft der Takt nie: `dueEvents` laesst nur 2 Minuten Spiel. */
private val MAX_SLEEP: Duration = Duration.ofSeconds(60)

/** Ab dieser Abweichung zwischen Wand- und Monotonuhr gilt die Uhr als gesprungen. */
private const val JUMP_LIMIT_MS = 90_000L

/**
 * Wann der Takt das naechste Mal laeuft: am frueheren von [nextChange]
 * (`DisplayState.nextChangeAt`) und [nextEvent] (naechstes NotifyEvent),
 * spaetestens aber nach einer Minute. Liegt das Ziel schon zurueck (Uhr
 * vorgestellt), sofort.
 */
fun nextRunAt(now: ZonedDateTime, nextChange: ZonedDateTime?, nextEvent: ZonedDateTime?): ZonedDateTime {
    val latest = now.plus(MAX_SLEEP)
    val target = listOfNotNull(nextChange, nextEvent, latest).minOrNull()!!
    return if (target.isBefore(now)) now else target
}

/**
 * True, wenn die Wanduhr seit dem letzten Blick um mehr als 90 s anders
 * gelaufen ist als die Monotonuhr: Ruhezustand, Uhr gestellt, Zeitzone.
 */
fun wallClockJumped(lastWallMs: Long, lastMonoNs: Long, wallMs: Long, monoNs: Long): Boolean {
    val expected = lastWallMs + (monoNs - lastMonoNs) / 1_000_000L
    return abs(wallMs - expected) > JUMP_LIMIT_MS
}

/**
 * Taktgeber. [scheduler] muss EIN Thread sein: [onTick] und alles, was er
 * anfasst, laeuft nur dort. Nach jedem Lauf plant der Takt sich selbst neu
 * auf [nextRunAt] aus dem, was [onTick] per [plan] gemeldet hat (plus
 * [BOUNDARY_SETTLE_MS], damit die Grenze sicher ueberschritten ist).
 *
 * Dazu ein Waechter alle 60 s: Springt die Wanduhr (siehe [wallClockJumped]),
 * laeuft [onTick] sofort; danach immer [onWatch] (Feld wieder einhaengen,
 * Tray nach Explorer-Neustart).
 */
class Ticker(
    private val clock: () -> ZonedDateTime,
    private val scheduler: ScheduledExecutorService,
    private val onTick: () -> Unit,
    private val onWatch: () -> Unit = {},
) {
    @Volatile private var nextChange: ZonedDateTime? = null
    @Volatile private var nextEvent: ZonedDateTime? = null
    private var pending: ScheduledFuture<*>? = null
    private var lastWallMs = 0L
    private var lastMonoNs = 0L

    fun start() {
        scheduler.execute {
            lastWallMs = clock().toInstant().toEpochMilli()
            lastMonoNs = System.nanoTime()
        }
        tickNow()
        scheduler.scheduleWithFixedDelay(::watch, 60, 60, TimeUnit.SECONDS)
    }

    /** Aus [onTick] heraus: die naechsten bekannten Zeitpunkte. */
    fun plan(nextChange: ZonedDateTime?, nextEvent: ZonedDateTime?) {
        this.nextChange = nextChange
        this.nextEvent = nextEvent
    }

    /** Sofort neu rechnen (Einstellungen gespeichert, Abruf fertig). Von jedem Thread. */
    fun tickNow() {
        scheduler.execute(::run)
    }

    private fun run() {
        pending?.cancel(false)
        runCatching(onTick).onFailure { log("Takt", it) }
        val now = clock()
        val at = nextRunAt(now, nextChange, nextEvent)
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(0) + BOUNDARY_SETTLE_MS
        pending = scheduler.schedule(::run, delay, TimeUnit.MILLISECONDS)
    }

    /** Unter scheduleWithFixedDelay: eine durchgereichte Ausnahme wuerde den Waechter fuer immer beenden. */
    private fun watch() {
        runCatching(::watchOnce).onFailure { log("Waechter", it) }
    }

    private fun watchOnce() {
        val wall = clock().toInstant().toEpochMilli()
        val mono = System.nanoTime()
        val jumped = wallClockJumped(lastWallMs, lastMonoNs, wall, mono)
        lastWallMs = wall
        lastMonoNs = mono
        if (jumped) {
            log("Uhr gesprungen, neu rechnen", null)
            run()
        }
        runCatching(onWatch).onFailure { log("Waechter", it) }
    }

    private fun log(what: String, e: Throwable?) {
        System.err.println("Ticker: $what" + (e?.let { ": ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }
}
