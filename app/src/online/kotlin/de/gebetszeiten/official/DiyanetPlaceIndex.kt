package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace

/** Online-Flavor: der echte Index liegt in :net-diyanet, zusammen mit dem
 *  Asset, das nur zum Abrufen gebraucht wird. Dieses Objekt haelt bloss den
 *  gewohnten Namen fuer die Oberflaeche. */
object DiyanetPlaceIndex {
    suspend fun preload(context: Context) = de.gebetszeiten.net.DiyanetPlaceIndex.preload(context)
    suspend fun nearest(context: Context, lat: Double, lng: Double): DiyanetPlace? =
        de.gebetszeiten.net.DiyanetPlaceIndex.nearest(context, lat, lng)
    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double =
        de.gebetszeiten.net.DiyanetPlaceIndex.distanceKm(place, lat, lng)
}
