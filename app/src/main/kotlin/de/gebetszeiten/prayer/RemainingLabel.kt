package de.gebetszeiten.prayer

import java.time.Duration
import de.gebetszeiten.core.prayertimes.display.isUrgent as coreIsUrgent
import de.gebetszeiten.core.prayertimes.display.remainingStepLabel as coreRemainingStepLabel
import de.gebetszeiten.core.prayertimes.display.remainingStepShort as coreRemainingStepShort

// Die Stufenlogik samt Begruendungen lebt in core-prayertimes
// (`...core.prayertimes.display.RemainingSteps`), damit Telefon und Desktop
// dieselben Texte zeigen. Hier nur die bisherigen Namen.

/** Siehe `core.prayertimes.display.remainingStepLabel`. */
fun remainingStepLabel(remaining: Duration): String = coreRemainingStepLabel(remaining)

/** Siehe `core.prayertimes.display.remainingStepShort`. */
fun remainingStepShort(remaining: Duration): String = coreRemainingStepShort(remaining)

/** Siehe `core.prayertimes.display.isUrgent` (einziger App-Aufrufer: das Widget). */
fun isUrgent(remaining: Duration): Boolean = coreIsUrgent(remaining)
