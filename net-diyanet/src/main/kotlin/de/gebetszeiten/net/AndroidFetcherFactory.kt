package de.gebetszeiten.net

import android.content.Context
import android.util.Log

/**
 * Android-Fabrik fuer den plattformneutralen [CompositeDiyanetFetcher]: der
 * Ortsindex kommt aus den App-Assets ([DiyanetPlaceIndex]), geloggt wird ueber
 * `android.util.Log` — Verhalten wie vor dem Auslagern nach `:net-diyanet-core`.
 *
 * Als Erweiterung der Companion, damit die Aufrufer weiter
 * `CompositeDiyanetFetcher.create(context) { lat, lng -> ... }` schreiben und
 * nur `de.gebetszeiten.net.create` importieren muessen. Die Kernfabrik
 * unterscheidet sich im ersten Parameter ([Context] statt Ortsindex-Lambda).
 */
fun CompositeDiyanetFetcher.Companion.create(
    context: Context,
    bundledLocationId: suspend (lat: Double, lng: Double) -> Int?,
): CompositeDiyanetFetcher = create(
    nearestPlace = { lat, lng -> DiyanetPlaceIndex.nearest(context, lat, lng) },
    bundledLocationId = bundledLocationId,
    log = { msg, e -> Log.w("DiyanetFetch", msg, e) },
)
