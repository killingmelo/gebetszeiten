package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.Prayer

/** Kurze Namen (DMG-Umschrift) fuer den kleinen Uhrbildschirm. */
fun Prayer.label(): String = when (this) {
    Prayer.FAJR -> "Faǧr"
    Prayer.SUNRISE -> "Sonne"
    Prayer.DHUHR -> "Ẓuhr"
    Prayer.ASR -> "ʿAṣr"
    Prayer.MAGHRIB -> "Maġrib"
    Prayer.ISHA -> "ʿIšāʾ"
}
