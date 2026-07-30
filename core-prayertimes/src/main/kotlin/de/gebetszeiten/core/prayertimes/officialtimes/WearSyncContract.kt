package de.gebetszeiten.core.prayertimes.officialtimes

/** Vertrag des Phone→Wear-Syncs: DataItem-Pfad + DataMap-Schlüssel.
 *  Liegt in core, damit Phone (Sender) und Uhr (Empfänger) exakt
 *  dieselben Konstanten nutzen. */
object WearSyncContract {
    const val PATH = "/official-times"
    const val KEY_SCHEDULE = "schedule"
    const val KEY_LAT = "lat"
    const val KEY_LNG = "lng"
    const val KEY_CITY = "city"
}
