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

    /**
     * Zeitpunkt, zu dem das TELEFON diese Zeiten geholt hat (der Kopf
     * seines eigenen Caches, `updatedEpochMs` — siehe `WearCacheSync.push`
     * am Telefon) — NICHT der Sende-Zeitpunkt. Die Uhr braucht diesen Wert,
     * um einen Sync gegen einen frischeren eigenen Fund abzuwaegen (siehe
     * `SyncDecision.syncWins` im wear-Modul): "jetzt beim Anwenden" waere
     * dafuer untauglich, weil es IMMER nach jedem vergangenen eigenen
     * Zeitstempel liegt und der Vergleich dann nie zugunsten des eigenen
     * Stands ausfiele.
     *
     * FEHLT der Schluessel im DataItem (aeltere Telefon-App-Version, die
     * dieses Feld noch nicht sendet), gewinnt der Sync wie vor dieser
     * Zusicherung immer — das ist der Zustand VOR ihrer Einfuehrung, also
     * kein Rueckschritt, nur kein zusaetzlicher Schutz fuer diesen einen
     * Fall.
     */
    const val KEY_UPDATED = "updatedEpochMs"
}
