package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.display.durationLabel as coreDurationLabel

/** "3 Std 26 Min" / "55 Min" — siehe [coreDurationLabel] in core.
 *  Unter einer Minute heisst es „weniger als 1 Min“ statt „0 Min“: die
 *  Minuten werden abgerundet, und vor einer Makrūh-Zeit stand sonst
 *  „in 0 Min“. */
fun durationLabel(min: Long): String =
    if (min < 1) "weniger als 1 Min" else coreDurationLabel(min)
