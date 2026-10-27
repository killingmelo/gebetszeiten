package de.gebetszeiten.core.prayertimes.display

import de.gebetszeiten.core.prayertimes.Prayer

/** Anzeigename in DMG-Umschrift — identisch mit `prayer_*` in den App-Strings. */
fun Prayer.germanName(): String = when (this) {
    Prayer.FAJR -> "Faǧr"
    Prayer.SUNRISE -> "Sonnenaufgang"
    Prayer.DHUHR -> "Ẓuhr"
    Prayer.ASR -> "ʿAṣr"
    Prayer.MAGHRIB -> "Maġrib"
    Prayer.ISHA -> "ʿIšāʾ"
}
