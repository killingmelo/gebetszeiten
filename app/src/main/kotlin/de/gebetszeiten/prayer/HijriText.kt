package de.gebetszeiten.prayer

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

// Hijri-Monatsnamen in DMG-Umschrift, wie alle anderen Begriffe der App.
private val HIJRI_MONTHS = arrayOf(
    "Muḥarram", "Ṣafar", "Rabīʿ al-awwal", "Rabīʿ aṯ-ṯānī", "Ǧumādā l-ūlā", "Ǧumādā l-āḫira",
    "Raǧab", "Šaʿbān", "Ramaḍān", "Šawwāl", "Ḏū l-Qaʿda", "Ḏū l-Ḥiǧǧa",
)

/** "24. Ḏū l-Ḥiǧǧa 1447" — with the moon-sighting day offset applied. */
fun hijriText(date: LocalDate, offsetDays: Int = 0): String {
    val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
    val d = h.get(ChronoField.DAY_OF_MONTH)
    val m = h.get(ChronoField.MONTH_OF_YEAR)
    val y = h.get(ChronoField.YEAR)
    return "$d. ${HIJRI_MONTHS[m - 1]} $y"
}

/** "24. Ḏū l-Ḥiǧǧa" — short form for compact surfaces (widget header). */
fun hijriTextShort(date: LocalDate, offsetDays: Int = 0): String {
    val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
    val d = h.get(ChronoField.DAY_OF_MONTH)
    val m = h.get(ChronoField.MONTH_OF_YEAR)
    return "$d. ${HIJRI_MONTHS[m - 1]}"
}
