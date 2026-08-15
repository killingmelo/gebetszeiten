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
 * Klassifiziert einen Suchtreffer. Reihenfolge spiegelt
 * `resolveLocationIdChain` (`CompositeDiyanetFetcher.kt`) — die Funktion, die
 * entscheidet, WELCHER Diyanet-Standort abgerufen wird: Nutzerwunsch ->
 * gebuendelte Tabelle -> Index -> Berechnung.
 *
 * ACHTUNG, hier wurde schon zweimal falsch abgebogen: `PrayerProvider.daily`
 * fragt den Online-Cache vor der gebuendelten Tabelle, aber das ist eine
 * andere Frage — dort geht es um die ZEITEN, und der Cache enthaelt genau die
 * Zeiten der ID, die zuvor aus dem Bundle kam. `daily()` kennt den Index
 * ueberhaupt nicht. Fuer die Frage, welcher STANDORT benannt wird, gilt
 * Bundle vor Index: Nuernberg ist in beiden Quellen ID 11024, das Bundle
 * schreibt ihn nur besser ("Nürnberg" statt Diyanets "NURNBERG").
 */
fun timesSourceBadge(
    bundledName: String?,
    officialPlace: DiyanetPlace?,
    distanceKm: Double?,
    useCalculated: Boolean,
): TimesSourceBadge = when {
    useCalculated -> TimesSourceBadge.Calculated
    bundledName != null -> TimesSourceBadge.Bundled(bundledName)
    officialPlace != null && distanceKm != null ->
        TimesSourceBadge.Official(officialPlace.displayName(), distanceKm.roundToInt())
    else -> TimesSourceBadge.Calculated
}
