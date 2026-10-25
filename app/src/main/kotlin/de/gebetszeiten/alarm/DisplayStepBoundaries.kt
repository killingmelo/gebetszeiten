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
