package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.Prayer

/** Wie Erinnerungen erscheinen: eigenes Banner oder Windows-Benachrichtigung. */
enum class NotifyStyle { BANNER, WINDOWS }

/**
 * Ton je Ereignisart: [OFF], ein eingebauter Klang oder [FILE] (dann
 * steht der Pfad im zugehoerigen `*File`-Feld).
 */
enum class ToneChoice(val label: String) {
    OFF("Aus"),
    SOFT("Sanft"),
    BELL("Glocke"),
    DOUBLE("Doppelton"),
    FILE("Eigene Datei"),
}

/**
 * Einstellungen des Desktop-Programms. Ohne [latitude]/[longitude] ist kein
 * Ort gewaehlt: dann gibt es keine Zeiten, nie eine erfundene Zahl.
 */
data class DesktopSettings(
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val diyanetId: Int? = null,
    val reminders: Set<Prayer> = setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA),
    val leadMinutes: Int = 0,
    val karahaWarning: Boolean = false,
    val showKaraha: Boolean = true,
    /** Restzeit minutengenau („noch 27 Min") statt in Stufen („noch 20+ Min"). */
    val exactRemaining: Boolean = false,
    /** Warnung so viele Minuten vor Ende jeder Gebetszeit; 0 = aus. */
    val endWarningMinutes: Int = 20,
    val cumaHints: Boolean = true,
    /** Sunnet-Fasten: Mo/Do und Eyyam-ı Bîz, jeweils am Vorabend. */
    val fastingHints: Boolean = true,
    /** Beginn von Kuşluk (Duha) und Teheccüd. */
    val nafileHints: Boolean = false,
    /** Kandil-Naechte und besondere Tage (braucht das Hicri-Datum von Diyanet). */
    val hijriHints: Boolean = true,
    val ramazanHints: Boolean = true,
    val sahurMinutes: Int = 45,
    val iftarLeadMinutes: Int = 10,
    /** Lesetext (Ayet, Hadis, Gelehrtenwort) zu Hinweisen anzeigen. */
    val showTexts: Boolean = true,
    val notifyStyle: NotifyStyle = NotifyStyle.BANNER,
    /** Banner schliesst nach so vielen Sekunden; 0 = bleibt bis Klick. */
    val bannerSeconds: Int = 30,
    val tonePrayer: ToneChoice = ToneChoice.BELL,
    val toneLead: ToneChoice = ToneChoice.SOFT,
    val toneWarning: ToneChoice = ToneChoice.DOUBLE,
    val toneHint: ToneChoice = ToneChoice.SOFT,
    val tonePrayerFile: String? = null,
    val toneLeadFile: String? = null,
    val toneWarningFile: String? = null,
    val toneHintFile: String? = null,
    val toneVolume: Int = 60,
    val ezanFile: String? = null,
    val fajrEzanFile: String? = null,
    val ezanVolume: Int = 70,
    val taskbarField: Boolean = true,
    val autostart: Boolean = true,
) {
    /** Der gewaehlte Ort, oder null. */
    val coords: Pair<Double, Double>?
        get() = if (latitude != null && longitude != null) latitude to longitude else null
}
