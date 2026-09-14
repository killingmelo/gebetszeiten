package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher
import de.gebetszeiten.net.CompositeDiyanetFetcher

/** Online-Flavor der Uhr: sie ruft selbst ab, ueber dieselben drei Quellen
 *  wie das Telefon. Auf Wear OS leitet das System die Anfrage ueber Bluetooth
 *  durchs gekoppelte Telefon, wenn die Uhr kein eigenes Netz hat.
 *
 *  `bundledLocationId` liefert hier immer `null`: das DE-Bundle
 *  (`BundledOfficialSource`), das dem Telefon einen Vorschlag gibt, ist
 *  App-seitig und der Uhr (noch) nicht zugaenglich. `null` heisst fuer
 *  `CompositeDiyanetFetcher.create` ausdruecklich „kein Vorschlag" — die
 *  Kette faellt dann auf Index/Cache/Proxy-Namenssuche zurueck, genau wie
 *  beim Telefon ausserhalb des DE-Bundles. Aufgabe 6 kann das verfeinern,
 *  sobald die Uhr selbst abruft. */
object WearFetchProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher? = CompositeDiyanetFetcher.create(context) { _, _ -> null }
}
