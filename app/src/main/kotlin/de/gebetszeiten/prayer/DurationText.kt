package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.display.durationLabel as coreDurationLabel

/** "3 Std 26 Min" / "55 Min" — siehe [coreDurationLabel] in core. */
fun durationLabel(min: Long): String = coreDurationLabel(min)
