package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher
import de.gebetszeiten.net.CompositeDiyanetFetcher

/** Online-Flavor der Uhr: sie ruft selbst ab, ueber dieselben drei Quellen
 *  wie das Telefon. Auf Wear OS leitet das System die Anfrage ueber Bluetooth
 *  durchs gekoppelte Telefon, wenn die Uhr kein eigenes Netz hat.
 *
 *  `bundledLocationId` sucht den naechstgelegenen Standort in
 *  `locations-de.tsv` ueber [WearOfficialSource.nearestLocation] — die Uhr
 *  hat dieses Bundle laengst (`wear/build.gradle.kts` bindet `shared-assets`
 *  als Asset-Verzeichnis ein, und [WearOfficialSource] liest daraus schon
 *  seit dessen Einfuehrung), es wurde hier nur nicht benutzt. Genau das
 *  Muster wie `OfficialTimesProvider` im app-Modul, das dafuer
 *  `BundledOfficialSource.nearestLocation` heranzieht.
 *
 *  Damit loesen Uhr und Telefon fuer denselben deutschen Ort KONSTRUKTIV auf
 *  dieselbe Diyanet-ID auf (nicht nur zufaellig, weil zwei getrennt gebaute
 *  Indizes zufaellig uebereinstimmen): beide fragen zuerst dieselbe Tabelle.
 *  Bleibt der Standort dort unbekannt (Ausland, oder ein Ort ausserhalb der
 *  25-km-Schwelle), liefert `nearestLocation` `null`, und die Kette faellt
 *  auf den weltweiten Index/Cache/Proxy-Namenssuche zurueck — dort bleiben
 *  drei bekannte Abweichungen (siehe `PhoneWatchLocationIdConsistencyTest`
 *  in `:net-diyanet`), die fuer deutsche Orte durch diese Zeile nicht mehr
 *  erreicht werden. */
object WearFetchProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher? = CompositeDiyanetFetcher.create(context) { lat, lng ->
        WearOfficialSource.nearestLocation(context, lat, lng)?.diyanetId
    }
}
