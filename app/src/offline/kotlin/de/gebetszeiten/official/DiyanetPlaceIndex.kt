package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace

/**
 * Offline-Flavor: kein Diyanet-ID-Index. Der Flavor ruft nie ab, also gibt es
 * hier nichts aufzulösen — und kein 400-KB-Asset, das nach Netzfähigkeit
 * aussieht. Badges entstehen offline ausschließlich aus dem DE-Bundle.
 */
object DiyanetPlaceIndex {
    suspend fun preload(context: Context) = Unit
    suspend fun nearest(context: Context, lat: Double, lng: Double): DiyanetPlace? = null
    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double = Double.MAX_VALUE
}
