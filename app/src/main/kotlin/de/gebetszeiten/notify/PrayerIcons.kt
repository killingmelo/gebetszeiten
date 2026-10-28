package de.gebetszeiten.notify

import androidx.annotation.DrawableRes
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.prayer.PhotoKey

/**
 * Das Statusleisten-Symbol einer Gebets-Meldung (Eintritt, Vorlauf). Jedes
 * Motiv passt zu seinem Foto: Horizont mit Lichtbogen, Sonne hoch, Stab mit
 * langem Schatten, halbe Sonne am Horizont, Sichel mit Stern.
 *
 * Šurūq ist kein Gebet und bekommt keine Meldung; falls doch, bleibt der Mond.
 */
@DrawableRes
fun prayerIconRes(prayer: Prayer): Int = when (prayer) {
    Prayer.FAJR -> R.drawable.ic_prayer_fajr
    Prayer.SUNRISE -> R.drawable.ic_notification
    Prayer.DHUHR -> R.drawable.ic_prayer_dhuhr
    Prayer.ASR -> R.drawable.ic_prayer_asr
    Prayer.MAGHRIB -> R.drawable.ic_prayer_maghrib
    Prayer.ISHA -> R.drawable.ic_prayer_isha
}

/** Das Titelbild, das zu einem Gebet gehört (dieselben Fotos wie auf „Heute“). */
fun photoKeyFor(prayer: Prayer): PhotoKey = when (prayer) {
    Prayer.FAJR -> PhotoKey.FAJR
    Prayer.SUNRISE -> PhotoKey.SHURUQ
    Prayer.DHUHR -> PhotoKey.ZUHR
    Prayer.ASR -> PhotoKey.ASR
    Prayer.MAGHRIB -> PhotoKey.MAGHRIB
    Prayer.ISHA -> PhotoKey.ISHA
}

/**
 * Das Foto für die Daueranzeige: der Abschnitt, der GERADE läuft.
 *
 * [active] ist bei allen Aufrufern ohne Sonnenaufgang gefiltert und darum an
 * zwei Stellen leer: zwischen Šurūq und Ẓuhr (dann ist Šurūq der Abschnitt)
 * und vor Faǧr, wo der Tag noch kein Gebet kennt (dann ist es Nacht). Beides
 * ergibt sich aus [next].
 */
fun ongoingPhotoKey(active: Prayer?, next: Prayer): PhotoKey = when {
    active != null -> photoKeyFor(active)
    next == Prayer.DHUHR -> PhotoKey.SHURUQ
    else -> PhotoKey.ISHA
}
