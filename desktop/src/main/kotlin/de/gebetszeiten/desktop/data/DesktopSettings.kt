package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.Prayer

/**
 * Einstellungen des Desktop-Programms. Ohne [latitude]/[longitude] ist kein
 * Ort gewaehlt: dann gibt es keine Zeiten, nie eine erfundene Zahl.
 */
data class DesktopSettings(
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val diyanetId: Int? = null,
    val reminders: Set<Prayer> = setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA),
    val leadMinutes: Int = 0,
    val karahaWarning: Boolean = false,
    val showKaraha: Boolean = true,
    val ezanFile: String? = null,
    val fajrEzanFile: String? = null,
    val ezanVolume: Int = 70,
    val taskbarField: Boolean = true,
    val autostart: Boolean = true,
) {
    /** Der gewaehlte Ort, oder null. */
    val coords: Pair<Double, Double>?
        get() = if (latitude != null && longitude != null) latitude to longitude else null
}
