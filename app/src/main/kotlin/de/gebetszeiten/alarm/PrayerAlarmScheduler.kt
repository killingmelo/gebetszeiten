package de.gebetszeiten.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.NextPrayer
import de.gebetszeiten.prayer.PrayerProvider
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules exactly one alarm — the next prayer transition. When it fires, the
 * receiver schedules the following one. This event-driven chain means ~5–6
 * wake-ups per day with no polling and no background service.
 */
object PrayerAlarmScheduler {

    private const val REQUEST_CODE = 100
    private const val PRE_REQUEST_CODE = 101
    private const val DISPLAY_REQUEST_CODE = 102
    const val ACTION_PRAYER = "de.gebetszeiten.action.PRAYER_ALARM"
    const val ACTION_PRE_REMINDER = "de.gebetszeiten.action.PRE_REMINDER"
    const val ACTION_DISPLAY_STEP = "de.gebetszeiten.action.DISPLAY_STEP"

    suspend fun scheduleNext(context: Context, settings: AppSettings, zone: ZoneId = ZoneId.systemDefault()) {
        val now = ZonedDateTime.now(zone)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // Keine Zeiten unter den aktuellen Einstellungen: der Gebets-Wecker
        // wird ABBESTELLT, nicht unangetastet gelassen — sonst bliebe ein
        // frueher gesetzter Wecker stehen und feuerte spaeter auf eine
        // veraltete Zeit. Bis Aufgabe 13 kehrte diese Funktion hier per
        // fruehem `return` zurueck, BEVOR ueberhaupt etwas abbestellt wurde
        // — auch die beiden Aufrufe unten liefen dann gar nicht erst, und
        // Vorlauf- wie Stufen-Wecker blieben ebenfalls stehen, obwohl ihre
        // eigene Null-Behandlung (unten) laengst richtig war.
        val next = PrayerProvider.next(context, settings, zone, now)
        val prayerAlarm = pendingIntent(context, REQUEST_CODE, ACTION_PRAYER)
        // Die eigentliche Entscheidung — Zeit oder Abbestellen — steht als
        // reine Funktion in `mainAlarmTriggerAtMillis`, ohne Context und
        // damit im Test pruefbar; hier wird nur noch verdrahtet.
        val trigger = mainAlarmTriggerAtMillis(next)
        if (trigger != null) {
            setAlarm(alarmManager, trigger, prayerAlarm)
        } else {
            alarmManager.cancel(prayerAlarm)
        }
        schedulePreReminder(context, alarmManager, settings, zone, now)
        scheduleDisplayStep(context, alarmManager, settings, zone, now)
    }

    /**
     * One alarm at the next display boundary — the moment the widget's or the
     * persistent notification's static text or icon would change:
     *  - STEPS countdown: floor-step boundaries (full hours, 10-minute marks,
     *    minute marks in the final 10) of either surface's target.
     *  - Karaha indicator: warning start, window start and window end of each
     *    makruh window (+6 ms-scale wake-ups/day while enabled).
     *
     * Die Dauerbenachrichtigung ist seit dem Statusleisten-Symbol AUCH im
     * EXACT-Modus dabei, und das kostet: EXACT verliert seine Eigenschaft,
     * ganz ohne Weckvorgaenge auszukommen (der Systemzaehler zeichnete sich
     * selbst). Es kommen die Stufengrenzen dazu — volle Stunden,
     * Zehnminuten-Marken, die letzten zehn Minuten einzeln, also rund 19 bis
     * 24 Weckvorgaenge je Gebetsintervall. Der Preis dafuer, dass das Symbol
     * auch dort lebt statt einzufrieren; der Plan hat ihn ausdruecklich
     * akzeptiert. Das Widget bleibt bei STEPS — es hat kein Symbol.
     */
    private suspend fun scheduleDisplayStep(
        context: Context,
        alarmManager: AlarmManager,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
    ) {
        val pending = pendingIntent(context, DISPLAY_REQUEST_CODE, ACTION_DISPLAY_STEP)
        val nowMs = System.currentTimeMillis()
        val boundaries = mutableListOf<Long>()

        if (settings.needsDisplayStepAlarms()) {
            // Surfaces' targets: widget = next transition, notification = next
            // actual prayer (sunrise skipped). Boundaries of either count.
            // DIESELBEN Praedikate wie in `needsDisplayStepAlarms()`, nicht
            // ihre Bedingungen noch einmal ausgeschrieben: sonst entschiede
            // die eine Stelle, OB die Kette laeuft, und die andere, WELCHE
            // Ziele Grenzen bekommen — laufen sie auseinander, bleibt
            // `boundaries` leer, der Alarm wird abbestellt und das Symbol
            // friert ein.
            // Keine Zeiten unter den aktuellen Einstellungen: dieses Ziel
            // liefert keine Grenze, statt eine zu erfinden.
            val targets = buildList {
                if (settings.widgetNeedsStepAlarms()) {
                    PrayerProvider.next(context, settings, zone, now)?.let { add(it.time) }
                }
                // Auch EXACT: das Symbol der Benachrichtigung zaehlt gegen
                // nextPrayer und wird nur von diesen Alarmen weitergestellt.
                if (settings.notificationNeedsStepAlarms()) {
                    PrayerProvider.nextPrayer(context, settings, zone, now)?.let { add(it.time) }
                }
            }
            targets.forEach { target ->
                // Die Rechnung selbst steht als reine Funktion in
                // DisplayStepBoundaries.kt — hier gaebe es keinen Test dafuer.
                boundaries += displayStepBoundaries(target.toInstant().toEpochMilli(), nowMs)
            }
        }

        if (settings.showKaraha) {
            // Karaha lines on widget + notification change at these moments.
            // Keine Zeiten unter den aktuellen Einstellungen: keine Fenster,
            // statt sie zu erfinden.
            val today = PrayerProvider.daily(context, settings, now.toLocalDate(), zone)
            val tomorrow = PrayerProvider.daily(context, settings, now.toLocalDate().plusDays(1), zone)
            val todayWindows = today?.let { Karaha.windows(it) } ?: emptyList()
            val tomorrowWindows = tomorrow?.let { Karaha.windows(it) } ?: emptyList()
            // Derselbe Zuschlag wie bei den Stufengrenzen. Karaha.status()
            // vergleicht zwar einschliessend (`!now.isBefore(start)`) und
            // waere auch exakt auf der Grenze richtig — aber eine Grenze ohne
            // Zuschlag waere hier die Ausnahme, und Ausnahmen kosten spaeter.
            (Karaha.boundaries(todayWindows) + Karaha.boundaries(tomorrowWindows))
                .forEach { boundaries += it.toInstant().toEpochMilli() + BOUNDARY_SETTLE_MS }
        }

        val nextBoundary = nextDisplayBoundary(boundaries, nowMs)
        if (nextBoundary != null) {
            setAlarm(alarmManager, nextBoundary, pending)
        } else {
            alarmManager.cancel(pending)
        }
    }

    /**
     * Optional heads-up N minutes before the next reminder-enabled prayer.
     * Rescheduled by the same transition chain — no extra recurring wake-ups
     * beyond the single pre-alarm itself.
     */
    private suspend fun schedulePreReminder(
        context: Context,
        alarmManager: AlarmManager,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
    ) {
        val pre = pendingIntent(context, PRE_REQUEST_CODE, ACTION_PRE_REMINDER)
        val lead = settings.reminderLeadMinutes
        // Keine Zeiten unter den aktuellen Einstellungen: wie "nicht gewollt"
        // behandelt, der Pre-Reminder-Alarm wird abbestellt.
        val nextPrayer = PrayerProvider.nextPrayer(context, settings, zone, now)
        if (nextPrayer == null) {
            alarmManager.cancel(pre)
            return
        }
        val triggerAt = nextPrayer.time.minusMinutes(lead.toLong()).toInstant().toEpochMilli()
        val wanted = lead > 0 &&
            nextPrayer.prayer.name in settings.reminders &&
            triggerAt > System.currentTimeMillis()
        if (wanted) {
            setAlarm(alarmManager, triggerAt, pre)
        } else {
            alarmManager.cancel(pre)
        }
    }

    /** Wohin der naechste Gebets-Wecker gestellt wird — oder `null`, wenn er
     *  abbestellt werden muss, weil unter den aktuellen Einstellungen keine
     *  Zeiten vorliegen. Herausgezogen nach demselben Muster wie
     *  `displayStepBoundaries`/`nextDisplayBoundary`: ohne `Context` und
     *  `AlarmManager`, also im Test pruefbar, auch wenn `scheduleNext`
     *  selbst es wegen der Android-Naht nicht ist. */
    internal fun mainAlarmTriggerAtMillis(next: NextPrayer?): Long? =
        next?.time?.toInstant()?.toEpochMilli()

    private fun pendingIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PrayerAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAlarm(alarmManager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        // Don't pre-check canScheduleExactAlarms(): it only reflects the
        // SCHEDULE_EXACT_ALARM appop and reports false even when USE_EXACT_ALARM
        // is granted, which silently degraded every alarm to a 1-hour window.
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            // Exact-alarm permission really revoked: degrade gracefully to an
            // inexact (still doze-friendly) alarm rather than crashing.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}
