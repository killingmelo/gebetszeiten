package de.gebetszeiten.alarm

import de.gebetszeiten.core.prayertimes.display.BOUNDARY_MIN_LEAD_MS as CORE_BOUNDARY_MIN_LEAD_MS
import de.gebetszeiten.core.prayertimes.display.BOUNDARY_SETTLE_MS as CORE_BOUNDARY_SETTLE_MS
import de.gebetszeiten.core.prayertimes.display.displayStepBoundaries as coreDisplayStepBoundaries
import de.gebetszeiten.core.prayertimes.display.nextDisplayBoundary as coreNextDisplayBoundary

// Die Grenzberechnung samt Begruendungen lebt in core-prayertimes
// (`...core.prayertimes.display.StepBoundaries`); hier nur die bisherigen Namen.

/** Siehe `core.prayertimes.display.BOUNDARY_SETTLE_MS`. */
internal const val BOUNDARY_SETTLE_MS = CORE_BOUNDARY_SETTLE_MS

/** Siehe `core.prayertimes.display.BOUNDARY_MIN_LEAD_MS`. */
internal const val BOUNDARY_MIN_LEAD_MS = CORE_BOUNDARY_MIN_LEAD_MS

/** Siehe `core.prayertimes.display.displayStepBoundaries`. */
internal fun displayStepBoundaries(targetMs: Long, nowMs: Long): List<Long> =
    coreDisplayStepBoundaries(targetMs, nowMs)

/** Siehe `core.prayertimes.display.nextDisplayBoundary`. */
internal fun nextDisplayBoundary(boundaries: List<Long>, nowMs: Long): Long? =
    coreNextDisplayBoundary(boundaries, nowMs)

/**
 * Wie viele Weckvorgaenge dieses Ziel noch kostet.
 *
 * Es gibt sie, damit die Kostenangabe im Einstellungsblatt keine erfundene
 * Zahl ist: `settings_remaining_cost` nennt eine Spanne, und ein Test rechnet
 * sie mit GENAU dieser Funktion nach. Eine Zahl in der Oberflaeche, die kein
 * Test haelt, driftet beim ersten Umbau von der Wahrheit weg — der frueher
 * dort stehende Wert „19 bis 24" tat das bereits: er unterschlug die kurzen
 * Intervalle (Maghrib->Isha liegt im Sommer unter anderthalb Stunden und
 * kostet 15, nicht 19).
 *
 * Gezaehlt wird nach derselben Regel, nach der [nextDisplayBoundary] auswaehlt
 * — sonst zaehlte sie Grenzen mit, die nie feuern.
 */
internal fun displayStepCount(targetMs: Long, nowMs: Long): Int =
    displayStepBoundaries(targetMs, nowMs).count { it > nowMs + BOUNDARY_MIN_LEAD_MS }

/**
 * Wann das Widget seinen Titel wechselt, ohne dass ein Gebet beginnt: Beginn
 * von Ḍuḥā und Beginn der Zawāl-Zeit (je mit [BOUNDARY_SETTLE_MS]).
 *
 * Nur bei ausgeschalteter Karāha-Anzeige noetig — eingeschaltet liefern die
 * Karāha-Grenzen genau diese Zeitpunkte schon (Ende Israk = Beginn Ḍuḥā,
 * Beginn Zeval) — und nur, wenn ueberhaupt ein Widget liegt; sonst kostet
 * die Grenze zwei Weckvorgaenge am Tag fuer nichts. [widgetPlaced] wird von
 * aussen hereingereicht (Glance), damit diese Funktion ohne `Context` testbar
 * bleibt.
 */
internal fun widgetTitleBoundaries(
    karaha: de.gebetszeiten.prayer.KarahaTimes,
    nafl: de.gebetszeiten.prayer.NaflTimes,
    showKaraha: Boolean,
    widgetPlaced: Boolean,
): List<Long> =
    if (showKaraha || !widgetPlaced) {
        emptyList()
    } else {
        listOf(nafl.duhaStart, karaha.zevalStart).map { it.toInstant().toEpochMilli() + BOUNDARY_SETTLE_MS }
    }
