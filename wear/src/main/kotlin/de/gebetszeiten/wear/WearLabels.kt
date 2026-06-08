package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.Prayer

/** Short German labels suited to the small watch screen. */
fun Prayer.label(): String = when (this) {
    Prayer.FAJR -> "Fajr"
    Prayer.SUNRISE -> "Sonne"
    Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"
    Prayer.MAGHRIB -> "Maghrib"
    Prayer.ISHA -> "Isha"
}
