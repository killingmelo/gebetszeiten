package de.gebetszeiten.prayer

import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer

/** Maps the engine's [Prayer] enum to user-facing string resources. */
fun Prayer.labelRes(): Int = when (this) {
    Prayer.FAJR -> R.string.prayer_fajr
    Prayer.SUNRISE -> R.string.prayer_sunrise
    Prayer.DHUHR -> R.string.prayer_dhuhr
    Prayer.ASR -> R.string.prayer_asr
    Prayer.MAGHRIB -> R.string.prayer_maghrib
    Prayer.ISHA -> R.string.prayer_isha
}
