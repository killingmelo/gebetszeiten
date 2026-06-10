package de.gebetszeiten.prayer

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

// Diyanet / Turkish transliteration of the Hijri month names.
private val HIJRI_MONTHS = arrayOf(
    "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
    "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce",
)

/** "24. Zilhicce 1447" — with the moon-sighting day offset applied. */
fun hijriText(date: LocalDate, offsetDays: Int = 0): String {
    val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
    val d = h.get(ChronoField.DAY_OF_MONTH)
    val m = h.get(ChronoField.MONTH_OF_YEAR)
    val y = h.get(ChronoField.YEAR)
    return "$d. ${HIJRI_MONTHS[m - 1]} $y"
}

/** "24. Zilhicce" — short form for compact surfaces (widget header). */
fun hijriTextShort(date: LocalDate, offsetDays: Int = 0): String {
    val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
    val d = h.get(ChronoField.DAY_OF_MONTH)
    val m = h.get(ChronoField.MONTH_OF_YEAR)
    return "$d. ${HIJRI_MONTHS[m - 1]}"
}
