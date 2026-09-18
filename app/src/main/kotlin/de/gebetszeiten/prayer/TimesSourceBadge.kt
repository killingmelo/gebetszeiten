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
    /** Keine amtliche Quelle — der Notausgang (eigene Berechnung) springt ein. */
    data object Calculated : TimesSourceBadge
    /** Keine amtliche Quelle UND der Notausgang ist aus — dieser Ort liefert
     *  gar keine Zeiten (Aufgabe 15). */
    data object None : TimesSourceBadge
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
 *
 * Bis Aufgabe 15 stand `useCalculated` (damals "immer rechnen") als ERSTE
 * Bedingung hier — ein eingeschalteter Notausgang hat jede amtliche Quelle
 * verdraengt, auch eine vorhandene. Das war unter der alten Bedeutung
 * richtig, unter der neuen ([calculationFillsGaps] = "Luecken fuellen") ist
 * es das Gegenteil: amtliche Quellen gewinnen IMMER, wenn es sie gibt, der
 * Notausgang steht deshalb jetzt als LETZTE Bedingung, nicht mehr als erste.
 * Bleibt auch er aus und es gibt keine amtliche Quelle, liefert dieser Ort
 * gar keine Zeiten — [TimesSourceBadge.None], nicht mehr [TimesSourceBadge.Calculated].
 */
fun timesSourceBadge(
    bundledName: String?,
    officialPlace: DiyanetPlace?,
    distanceKm: Double?,
    calculationFillsGaps: Boolean,
): TimesSourceBadge = when {
    bundledName != null -> TimesSourceBadge.Bundled(bundledName)
    officialPlace != null && distanceKm != null ->
        TimesSourceBadge.Official(officialPlace.displayName(), distanceKm.roundToInt())
    calculationFillsGaps -> TimesSourceBadge.Calculated
    else -> TimesSourceBadge.None
}
