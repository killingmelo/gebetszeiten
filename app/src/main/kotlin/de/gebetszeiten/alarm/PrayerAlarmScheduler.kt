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

    /**
     * Fragt alles ab, was die Entscheidung braucht ([alarmPlan] — reine
     * Funktion, kein `Context`), und setzt danach nur noch um, was der Plan
     * fuer die drei Ketten-Wecker sagt: setzen oder abbestellen, Feld fuer
     * Feld, ohne eigene Verzweigung.
     *
     * Bis Aufgabe 13 kehrte diese Funktion bei fehlenden Zeiten per fruehem
     * `return` zurueck, BEVOR ueberhaupt etwas abbestellt wurde — alte Wecker
     * blieben stehen. Die naheliegende zweite Form desselben Fehlers waere
     * gewesen, die drei Aufrufe hier stattdessen HINTER eine eigene
     * Bedingung zu verschachteln (z. B. `if (next != null) { ... }` um die
     * drei `applyAlarm`-Aufrufe). Da diese Funktion nach dieser Fix-Runde gar
     * keine eigene Verzweigung mehr enthaelt — nur noch Abfragen, den
     * Planaufbau und drei gleichfoermige `applyAlarm`-Aufrufe —, gibt es hier
     * keine Stelle mehr, an der sich eine solche Verschachtelung unbemerkt
     * einschleichen koennte.
     */
    suspend fun scheduleNext(context: Context, settings: AppSettings, zone: ZoneId = ZoneId.systemDefault()) {
        val now = ZonedDateTime.now(zone)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val nowMillis = System.currentTimeMillis()

        val next = PrayerProvider.next(context, settings, zone, now)
        val upcoming = PrayerProvider.nextPrayer(context, settings, zone, now)
        val boundaries = collectDisplayStepBoundaries(context, settings, zone, now, nowMillis, next, upcoming)

        val plan = alarmPlan(
            nextPrayerMillis = next?.time?.toInstant()?.toEpochMilli(),
            upcomingPrayerName = upcoming?.prayer?.name,
            upcomingPrayerMillis = upcoming?.time?.toInstant()?.toEpochMilli(),
            reminderLeadMinutes = settings.reminderLeadMinutes,
            enabledReminders = settings.reminders,
            displayStepBoundaries = boundaries,
            nowMillis = nowMillis,
        )

        applyAlarm(alarmManager, plan.prayerAtMillis, pendingIntent(context, REQUEST_CODE, ACTION_PRAYER))
        applyAlarm(alarmManager, plan.preReminderAtMillis, pendingIntent(context, PRE_REQUEST_CODE, ACTION_PRE_REMINDER))
        applyAlarm(alarmManager, plan.displayStepAtMillis, pendingIntent(context, DISPLAY_REQUEST_CODE, ACTION_DISPLAY_STEP))
    }

    /**
     * Alle drei Ketten-Wecker als Daten — reine Funktion, kein `Context`,
     * damit im Test pruefbar (Muster: `displayStepBoundaries`,
     * `daySourceOrder`). `null` heisst bei JEDEM Feld dasselbe: abbestellen.
     *
     * [displayStepBoundaries] kommt bereits fertig berechnet herein (die
     * Grenzrechnung selbst steht laengst rein und getestet in
     * `DisplayStepBoundaries.kt`) — nur das Sammeln dieser Grenzen braucht
     * `Context`/`PrayerProvider` und bleibt in [collectDisplayStepBoundaries].
     */
    internal data class AlarmPlan(
        val prayerAtMillis: Long?,
        val preReminderAtMillis: Long?,
        val displayStepAtMillis: Long?,
    )

    internal fun alarmPlan(
        nextPrayerMillis: Long?,
        upcomingPrayerName: String?,
        upcomingPrayerMillis: Long?,
        reminderLeadMinutes: Int,
        enabledReminders: Set<String>,
        displayStepBoundaries: List<Long>,
        nowMillis: Long,
    ): AlarmPlan {
        // Keine Zeiten unter den aktuellen Einstellungen: wie "nicht
        // gewollt" behandelt, der Pre-Reminder-Alarm wird abbestellt.
        val preReminderAtMillis = upcomingPrayerMillis?.let { millis ->
            val triggerAt = millis - reminderLeadMinutes * 60_000L
            val wanted = reminderLeadMinutes > 0 &&
                upcomingPrayerName in enabledReminders &&
                triggerAt > nowMillis
            if (wanted) triggerAt else null
        }
        return AlarmPlan(
            prayerAtMillis = nextPrayerMillis,
            preReminderAtMillis = preReminderAtMillis,
            displayStepAtMillis = nextDisplayBoundary(displayStepBoundaries, nowMillis),
        )
    }

    /**
     * Setzt [triggerAtMillis], oder bestellt ab, wenn es `null` ist. Dieselbe
     * Anwendung fuer alle drei Ketten-Wecker — keine feature-spezifische
     * Verzweigung hier, nur noch die eine, immer gleiche Umsetzung der
     * bereits getroffenen Entscheidung aus [alarmPlan].
     */
    private fun applyAlarm(alarmManager: AlarmManager, triggerAtMillis: Long?, pendingIntent: PendingIntent) {
        if (triggerAtMillis != null) {
            setAlarm(alarmManager, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    /**
     * Die Momente, an denen sich die Stufenanzeige aendert — Text („noch 20+
     * Min") wie Statusleisten-Symbol:
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
     *
     * [widgetTarget]/[notificationTarget] sind dieselben Zeitpunkte, die
     * `scheduleNext` fuer den Gebets- bzw. Vorlauf-Wecker ohnehin schon
     * abgefragt hat ([PrayerProvider.next]/[PrayerProvider.nextPrayer]) —
     * hier wiederverwendet statt ein zweites Mal abgefragt.
     */
    private suspend fun collectDisplayStepBoundaries(
        context: Context,
        settings: AppSettings,
        zone: ZoneId,
        now: ZonedDateTime,
        nowMillis: Long,
        widgetTarget: NextPrayer?,
        notificationTarget: NextPrayer?,
    ): List<Long> {
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
                    widgetTarget?.let { add(it.time) }
                }
                // Auch EXACT: das Symbol der Benachrichtigung zaehlt gegen
                // nextPrayer und wird nur von diesen Alarmen weitergestellt.
                if (settings.notificationNeedsStepAlarms()) {
                    notificationTarget?.let { add(it.time) }
                }
            }
            targets.forEach { target ->
                // Die Rechnung selbst steht als reine Funktion in
                // DisplayStepBoundaries.kt — hier gaebe es keinen Test dafuer.
                boundaries += displayStepBoundaries(target.toInstant().toEpochMilli(), nowMillis)
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

        return boundaries
    }

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
