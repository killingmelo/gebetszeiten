package de.gebetszeiten.notify

import de.gebetszeiten.data.AppSettings

/**
 * Wie die Dauerbenachrichtigung aussehen soll, wie der Nutzer es gewählt hat
 * (Einstellungen, Abschnitt Restzeit). Ohne Android-Bezug, damit [pillOf] im
 * JVM-Test läuft.
 */
data class LiveLook(
    /** Restzeit als Live-Anzeige in der Statusleiste. */
    val statusBar: Boolean = true,
    /** Auf dem Sperrbildschirm sichtbar. */
    val lockScreen: Boolean = true,
    /** [AppSettings.PILL_TEXT_NUMBER] oder [AppSettings.PILL_TEXT_PRAYER]. */
    val text: String = AppSettings.PILL_TEXT_NUMBER,
    /** [AppSettings.PILL_ICON_MOON], [AppSettings.PILL_ICON_PRAYER] oder [AppSettings.PILL_ICON_NUMBER]. */
    val icon: String = AppSettings.PILL_ICON_MOON,
)

/** Welches kleine Symbol die Meldung trägt. */
enum class PillIcon { MOON, PRAYER, NUMBER }

/**
 * Was die Meldung für Statusleiste und Pille setzt.
 *
 * [promote]: Live-Update anfordern. [text]: Kurztext der Pille, `null` ohne.
 * [icon]: kleines Symbol, in der Pille links und sonst in der Statusleiste.
 */
data class Pill(val promote: Boolean, val text: String?, val icon: PillIcon)

/**
 * Die eine Entscheidung, aus Wahl, Restzeit und dem, was das Gerät erlaubt.
 *
 * - Statusleiste aus oder keine Restzeit: kein Live-Update, ruhiges Symbol.
 * - Statusleiste an, Live-Update erlaubt: Pille mit gewähltem Text und Symbol.
 * - Statusleiste an, Live-Update nicht erlaubt (OnePlus ab Werk, Android unter
 *   16): das Zahlen-Symbol, wie vor 0.5.1. Text und Symbolwahl wirken dann nicht.
 */
fun pillOf(look: LiveLook, chip: String?, prayerName: String, canPromote: Boolean): Pill {
    if (!look.statusBar || chip == null) return Pill(promote = false, text = null, icon = PillIcon.MOON)
    if (!canPromote) return Pill(promote = true, text = chip, icon = PillIcon.NUMBER)
    val text = if (look.text == AppSettings.PILL_TEXT_PRAYER) "$prayerName $chip" else chip
    val icon = when (look.icon) {
        AppSettings.PILL_ICON_PRAYER -> PillIcon.PRAYER
        AppSettings.PILL_ICON_NUMBER -> PillIcon.NUMBER
        else -> PillIcon.MOON
    }
    return Pill(promote = true, text = text, icon = icon)
}
