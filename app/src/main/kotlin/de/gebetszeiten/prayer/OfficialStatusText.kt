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
 *
 * [canFetch] entscheidet ueber "Letzter Abruf"/"Fehler" — bewusst NICHT
 * `source is Bundled` (Fix-Runde 3): ob ein Abrufmechanismus ueberhaupt
 * existiert, haengt am Flavor/Schalter, nicht an der Quelle, die gerade
 * traegt. Online-Flavor + `useOnline` + keine eigene Berechnung kann einen
 * fehlgeschlagenen Abruf haben, WAEHREND die gebuendelte DE-Tabelle traegt —
 * der Fehlergrund darf dann nicht verschwinden, sonst bleibt "Jetzt
 * aktualisieren" (das an derselben Bedingung haengt) ein Knopf ohne
 * sichtbare Wirkung. Am Aufrufort identisch mit der Knopf-Sichtbarkeit:
 * `settings.useOnline && !settings.useCalculated`.
 */
fun officialStatusText(
    status: OfficialStatus,
    source: TimesSourceBadge,
    canFetch: Boolean,
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
    // "Abgedeckt bis" beschreibt die Abdeckung des ONLINE-CACHES — nur
    // zeigen, wenn der Cache auch die aktive Quelle ist. Sonst (Bundled,
    // Calculated) koennte ein veraltetes `coveredUntil` faelschlich als
    // Abdeckung der gebuendelten Tabelle bzw. der Berechnung gelesen werden.
    if (source is TimesSourceBadge.Official) {
        status.coveredUntil?.let { lines += "Abgedeckt bis: ${DATE.format(it)}" }
    }
    // Ohne Abrufmechanismus (offline-Flavor, oder online mit ausgeschaltetem
    // Abruf) waeren "Letzter Abruf"/"Fehler" Aussagen ueber ein Ereignis, das
    // gar nicht stattfinden kann.
    if (canFetch) {
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
