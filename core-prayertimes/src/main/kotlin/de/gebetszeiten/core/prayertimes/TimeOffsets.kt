package de.gebetszeiten.core.prayertimes

/**
 * Vom Nutzer eingestellte Verschiebung je Zeit in Minuten (−60 bis +60),
 * z. B. weil die eigene Moschee später betet. Ab Werk alles 0. Liegt in core,
 * damit Telefon und Uhr genau gleich rechnen (die Uhr bekommt die Werte über
 * den Sync, `WearSyncContract.KEY_OFFSETS`).
 *
 * Grundsatz (05.11.2026): Die Zeiten bleiben amtlich, die Verschiebung ist
 * die Wahl des Nutzers und wird überall gekennzeichnet; nie still.
 */
data class TimeOffsets(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    init {
        for (m in listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)) {
            require(m in RANGE) { "Verschiebung $m außerhalb von $RANGE" }
        }
    }

    fun of(prayer: Prayer): Int = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    fun with(prayer: Prayer, minutes: Int): TimeOffsets {
        val m = minutes.coerceIn(RANGE)
        return when (prayer) {
            Prayer.FAJR -> copy(fajr = m)
            Prayer.SUNRISE -> copy(sunrise = m)
            Prayer.DHUHR -> copy(dhuhr = m)
            Prayer.ASR -> copy(asr = m)
            Prayer.MAGHRIB -> copy(maghrib = m)
            Prayer.ISHA -> copy(isha = m)
        }
    }

    val isZero: Boolean get() = this == NONE

    /** Die verschobenen Zeiten; ohne Verschiebung dieselbe Instanz. */
    fun applyTo(times: DailyPrayerTimes): DailyPrayerTimes = if (isZero) {
        times
    } else {
        times.copy(
            fajr = times.fajr.plusMinutes(fajr.toLong()),
            sunrise = times.sunrise.plusMinutes(sunrise.toLong()),
            dhuhr = times.dhuhr.plusMinutes(dhuhr.toLong()),
            asr = times.asr.plusMinutes(asr.toLong()),
            maghrib = times.maghrib.plusMinutes(maghrib.toLong()),
            isha = times.isha.plusMinutes(isha.toLong()),
        )
    }

    /** Speicher- und Sync-Form: sechs Zahlen, kommagetrennt, in Prayer-Reihenfolge. */
    fun encode(): String = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha).joinToString(",")

    /** „+5“ / „−3“ für [prayer], oder null, wenn nicht verschoben. */
    fun markOf(prayer: Prayer): String? = mark(of(prayer))

    companion object {
        val RANGE = -60..60
        val NONE = TimeOffsets()

        /** Vorzeichen mit echtem Minuszeichen (U+2212), nie Gedanken- oder Halbgeviertstrich. */
        fun mark(minutes: Int): String? = when {
            minutes > 0 -> "+$minutes"
            minutes < 0 -> "−${-minutes}"
            else -> null
        }

        /** Unlesbares oder Fehlendes ergibt keine Verschiebung, Werte außerhalb werden begrenzt. */
        fun decode(text: String?): TimeOffsets {
            val parts = text?.split(",")?.map { it.trim().toIntOrNull() } ?: return NONE
            if (parts.size != 6 || parts.any { it == null }) return NONE
            val v = parts.map { it!!.coerceIn(RANGE) }
            return TimeOffsets(v[0], v[1], v[2], v[3], v[4], v[5])
        }
    }
}
