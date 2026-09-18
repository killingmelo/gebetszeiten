package de.gebetszeiten.notify

/**
 * Was mit der Pause-Meldung ("Keine amtlichen Zeiten — Erinnerungen
 * pausiert") gerade zu tun ist:
 *  - [SHOW]: einmal posten — es gibt keine Zeiten, und das wurde noch nicht
 *    gemeldet.
 *  - [CLEAR]: die Meldung zurueckziehen — es gibt wieder Zeiten, und sie war
 *    gemeldet.
 *  - [NOTHING]: nichts tun — entweder gibt es Zeiten und war nie gemeldet
 *    (Normalfall), oder es gibt weiterhin keine und wurde schon gemeldet
 *    (genau der Fall, den Aufgabe 14 stumm halten soll: EINMAL, nicht
 *    taeglich).
 */
enum class PauseNotice { SHOW, CLEAR, NOTHING }

/**
 * Seit Aufgabe 13 bestellen `PrayerAlarmScheduler` und `PrayerNotifier.
 * updateOngoing` Wecker und Dauerbenachrichtigung lautlos ab, wenn
 * [hasTimes] falsch ist (kein Notausgang, keine amtlichen Zeiten). Richtig,
 * aber stumm — wer sich auf Erinnerungen verlaesst, merkt den Ausfall sonst
 * erst Tage spaeter. Diese Funktion entscheidet NUR, was zu tun ist; sie
 * schreibt selbst nichts und kennt weder Context noch DataStore — die
 * Persistierung von [alreadyShown] (Merker `pause_notice_shown`) und das
 * tatsaechliche Posten/Zuruecknehmen liegen bei den Aufrufern
 * ([de.gebetszeiten.data.SettingsRepository.resolvePauseNotice],
 * [de.gebetszeiten.notify.PrayerNotifier.updatePauseNotice]).
 *
 * Vier Faelle, keiner davon ein Default-Zweig: [hasTimes] × [alreadyShown]
 * sind zusammen genau vier Kombinationen, und jede hat eine eigene, im Test
 * benannte Bedeutung.
 */
fun pauseNotice(hasTimes: Boolean, alreadyShown: Boolean): PauseNotice = when {
    !hasTimes && !alreadyShown -> PauseNotice.SHOW
    !hasTimes && alreadyShown -> PauseNotice.NOTHING
    hasTimes && alreadyShown -> PauseNotice.CLEAR
    else -> PauseNotice.NOTHING
}
