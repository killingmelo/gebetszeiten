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
 */
fun officialStatusText(status: OfficialStatus, sourceName: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    val lines = mutableListOf<String>()
    if (status.locationId != null && sourceName != null) {
        lines += "Quelle: amtliche Diyanet-Zeiten · $sourceName (ID ${status.locationId})"
    } else {
        lines += "Quelle: eigene Berechnung (Diyanet-Methode)"
    }
    status.coveredUntil?.let { lines += "Abgedeckt bis: ${DATE.format(it)}" }
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
    return lines.joinToString("\n")
}
