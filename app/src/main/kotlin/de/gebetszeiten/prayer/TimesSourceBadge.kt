package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import kotlin.math.roundToInt

/** Welche Quelle ein Ort liefert — für das Badge in der Ortssuche. */
sealed interface TimesSourceBadge {
    /** Amtliche Zeiten aus einer gebündelten Jahrestabelle (Deutschland). */
    data class Bundled(val locationName: String) : TimesSourceBadge
    /** Amtliche Zeiten per Abruf über den nächstgelegenen Diyanet-Standort. */
    data class Official(val locationName: String, val distanceKm: Int) : TimesSourceBadge
    /** Keine amtliche Quelle — eigene Berechnung. */
    data object Calculated : TimesSourceBadge
}

/**
 * Klassifiziert einen Suchtreffer. Reihenfolge spiegelt `PrayerProvider.daily`:
 * Nutzerwunsch → Online-Cache/Index-Abruf → gebündelte Tabelle → Berechnung.
 */
fun timesSourceBadge(
    bundledName: String?,
    officialPlace: DiyanetPlace?,
    distanceKm: Double?,
    useCalculated: Boolean,
): TimesSourceBadge = when {
    useCalculated -> TimesSourceBadge.Calculated
    officialPlace != null && distanceKm != null ->
        TimesSourceBadge.Official(officialPlace.displayName(), distanceKm.roundToInt())
    bundledName != null -> TimesSourceBadge.Bundled(bundledName)
    else -> TimesSourceBadge.Calculated
}
