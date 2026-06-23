package de.gebetszeiten.prayer

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

// International (deutsch gebräuchliche) Transliteration der Hijri-Monatsnamen.
private val HIJRI_MONTHS = arrayOf(
    "Muharram", "Safar", "Rabi al-awwal", "Rabi al-thani", "Jumada al-awwal", "Jumada al-thani",
    "Rajab", "Schaban", "Ramadan", "Schawwal", "Dhul-Qada", "Dhul-Hijja",
)

/** "24. Dhul-Hijja 1447" — with the moon-sighting day offset applied. */
fun hijriText(date: LocalDate, offsetDays: Int = 0): String {
    val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
    val d = h.get(ChronoField.DAY_OF_MONTH)
    val m = h.get(ChronoField.MONTH_OF_YEAR)
    val y = h.get(ChronoField.YEAR)
    return "$d. ${HIJRI_MONTHS[m - 1]} $y"
}

/** "24. Dhul-Hijja" — short form for compact surfaces (widget header). */
fun hijriTextShort(date: LocalDate, offsetDays: Int = 0): String {
    val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
    val d = h.get(ChronoField.DAY_OF_MONTH)
    val m = h.get(ChronoField.MONTH_OF_YEAR)
    return "$d. ${HIJRI_MONTHS[m - 1]}"
}
