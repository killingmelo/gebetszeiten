package de.gebetszeiten.core.prayertimes.display

import de.gebetszeiten.core.prayertimes.Prayer

/** Deutscher Anzeigename — identisch mit `prayer_*` in den App-Strings. */
fun Prayer.germanName(): String = when (this) {
    Prayer.FAJR -> "Fajr"
    Prayer.SUNRISE -> "Sonnenaufgang"
    Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"
    Prayer.MAGHRIB -> "Maghrib"
    Prayer.ISHA -> "Isha"
}
