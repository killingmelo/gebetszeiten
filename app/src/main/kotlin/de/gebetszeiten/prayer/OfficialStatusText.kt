package de.gebetszeiten.prayer

import de.gebetszeiten.official.OfficialStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

/**
 * Mehrzeiliger Klartext für die Statuszeile im Einstellungs-Sheet.
 * Reine Funktion (keine versteckte Systemuhr-/Zeitzonen-Abhaengigkeit) —
 * deshalb ohne Android testbar.
 *
 * [source] ist die vom Aufrufer (dem Einstellungs-Sheet) bereits
 * klassifizierte, tatsaechlich aktive Quelle (spiegelt `PrayerProvider.daily`)
 * — NICHT der Diyanet-Standortname allein. Nur so kann die Zeile die
 * Faelle sauber unterscheiden, in denen `status` (der Cache-Stempel) etwas
 * anderes zeigt als das, was gerade wirklich angezeigt wird: eigene
 * Berechnung gewaehlt, Online-Abruf abgeschaltet, oder eine gebuendelte
 * DE-Tabelle, fuer die nie ein Netzabruf stattfand.
 */
fun officialStatusText(
    status: OfficialStatus,
    source: TimesSourceBadge,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val lines = mutableListOf<String>()
    when (source) {
        is TimesSourceBadge.Official -> {
            val idSuffix = status.locationId?.let { " (ID $it)" } ?: ""
            lines += "Quelle: amtliche Diyanet-Zeiten · ${source.locationName}$idSuffix"
        }
        is TimesSourceBadge.Bundled ->
            lines += "Quelle: amtliche Diyanet-Zeiten · ${source.locationName} (gebündelt)"
        TimesSourceBadge.Calculated ->
            lines += "Quelle: eigene Berechnung (Diyanet-Methode)"
    }
    status.coveredUntil?.let { lines += "Abgedeckt bis: ${DATE.format(it)}" }
    // Gebuendelte Tabellen wurden nie abgerufen (im offline-Flavor existiert
    // dafuer nicht einmal ein Fetcher) — "Letzter Abruf"/"Fehler" waeren hier
    // Aussagen ueber ein Ereignis, das nie stattfinden konnte.
    if (source !is TimesSourceBadge.Bundled) {
        if (status.lastAttemptEpochMs == null) {
            // "noch kein Versuch" nur, wenn es auch keinerlei Beleg fuer einen
            // frueheren Abruf gibt. Liegen Standort oder Abdeckung vor, hat es
            // sehr wohl einen gegeben — seine Aufzeichnung wurde nur vom Versuch
            // fuer einen anderen Ort verdraengt (ein Datensatz fuer alle Orte).
            lines += if (status.locationId != null || status.coveredUntil != null) {
                "Letzter Abruf: unbekannt"
            } else {
                "Letzter Abruf: noch kein Versuch"
            }
        } else {
            val stamp = STAMP.format(Instant.ofEpochMilli(status.lastAttemptEpochMs).atZone(zone))
            lines += "Letzter Abruf: $stamp"
        }
        status.lastError?.let { lines += "Fehler: $it" }
    }
    return lines.joinToString("\n")
}
