package de.gebetszeiten.core.prayertimes

/**
 * Ein Hicri-Datum, wie Diyanet es fuer einen Tag angibt (Spalte „Hicri Tarih“).
 * Nie berechnet: fehlt es, gibt es fuer den Tag kein Hicri-Datum.
 * [month] 1 = Muharrem … 12 = Zilhicce.
 */
data class HijriDay(val day: Int, val month: Int, val year: Int) {
    init {
        require(day in 1..30) { "Hicri-Tag $day" }
        require(month in 1..12) { "Hicri-Monat $month" }
    }

    /** „5. Rabīʿ al-auwal 1448“ (DMG-Umschrift). */
    fun text(): String = "$day. ${MONTHS_DMG[month - 1]} $year"

    companion object {
        /** Monatsnamen in DMG-Umschrift, fuer die Anzeige. */
        val MONTHS_DMG = listOf(
            "Muḥarram", "Ṣafar", "Rabīʿ al-auwal", "Rabīʿ aṯ-ṯānī", "Ǧumādā l-ūlā", "Ǧumādā l-āḫira",
            "Raǧab", "Šaʿbān", "Ramaḍān", "Šauwāl", "Ḏū l-Qaʿda", "Ḏū l-Ḥiǧǧa",
        )

        /** Monatsnamen, wie die Diyanet-Seite sie schreibt (nur zum Einlesen). */
        val MONTHS_TR = listOf(
            "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
            "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce",
        )

        /** Monatsname von der Diyanet-Seite (mit oder ohne Umlaute) → 1..12, sonst null. */
        fun monthOf(name: String): Int? {
            val key = fold(name)
            return MONTHS_TR.indexOfFirst { fold(it) == key }.takeIf { it >= 0 }?.plus(1)
        }

        private fun fold(s: String): String = s.trim().lowercase()
            .replace('ü', 'u').replace('ş', 's').replace('ı', 'i').replace("̇", "")
            .replace('ç', 'c').replace('ğ', 'g').replace('ö', 'o')
    }
}
