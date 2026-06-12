package de.gebetszeiten.core.prayertimes

import java.time.ZonedDateTime

/**
 * The three Hanafi makruh (karaha) windows, shared by phone and watch so every
 * surface (app, widget, notification, Wear) agrees on the same boundaries.
 * Turkish/Diyanet conventions:
 *  - İşrak: sunrise until the sun is a spear's length high (≈45 min)
 *  - Zevâl: shortly before the zenith until Dhuhr (≈20 min)
 *  - İsfirar: the sun yellowing until sunset (≈40 min before Maghrib)
 */
object Karaha {

    const val ISRAK_AFTER_SUNRISE_MIN = 45L
    const val ZEVAL_BEFORE_DHUHR_MIN = 20L
    const val ISFIRAR_BEFORE_MAGHRIB_MIN = 40L

    /** Minutes ahead of a window at which surfaces show the early warning. */
    const val WARN_LEAD_MIN = 15L

    enum class Kind { ISRAK, ZEVAL, ISFIRAR }

    data class Window(val kind: Kind, val start: ZonedDateTime, val end: ZonedDateTime)

    /** What the compact surfaces (widget, notification, watch) should show. */
    sealed class Status {
        data object None : Status()

        /** A window starts within [WARN_LEAD_MIN] minutes — pre-warning. */
        data class Soon(val window: Window) : Status()

        /** Now inside a window — indicator until [Window.end]. */
        data class Active(val window: Window) : Status()
    }

    fun windows(times: DailyPrayerTimes): List<Window> = listOf(
        Window(Kind.ISRAK, times.sunrise, times.sunrise.plusMinutes(ISRAK_AFTER_SUNRISE_MIN)),
        Window(Kind.ZEVAL, times.dhuhr.minusMinutes(ZEVAL_BEFORE_DHUHR_MIN), times.dhuhr),
        Window(Kind.ISFIRAR, times.maghrib.minusMinutes(ISFIRAR_BEFORE_MAGHRIB_MIN), times.maghrib),
    )

    fun status(windows: List<Window>, now: ZonedDateTime): Status {
        windows.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
            ?.let { return Status.Active(it) }
        windows.firstOrNull {
            now.isBefore(it.start) && !now.isBefore(it.start.minusMinutes(WARN_LEAD_MIN))
        }?.let { return Status.Soon(it) }
        return Status.None
    }

    /**
     * Every moment at which a compact surface's karaha line changes — warning
     * start, window start and window end of each window. Used by the phone's
     * display-alarm chain to redraw exactly then (and never in between).
     */
    fun boundaries(windows: List<Window>): List<ZonedDateTime> = windows.flatMap {
        listOf(it.start.minusMinutes(WARN_LEAD_MIN), it.start, it.end)
    }
}
