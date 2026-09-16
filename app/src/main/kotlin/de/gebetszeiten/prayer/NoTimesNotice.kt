package de.gebetszeiten.prayer

/**
 * Der Wortlaut fuer den Leerfall aus Aufgabe 9: `PrayerProvider.daily` liefert
 * `null`, wenn weder ein amtlicher Stand (Cache oder gebuendelte Tabelle) noch
 * die eigene Berechnung als Notausgang zur Verfuegung steht. Diese Datei
 * schreibt NUR die Worte dafuer — verdrahtet wird sie erst in einer spaeteren
 * Aufgabe. Reine Funktion, kein Android: `core-prayertimes` bleibt frei von
 * Oberflaechen-Sprache, also lebt der deutsche Text hier im `app`-Modul.
 */
data class NoTimesNotice(
    val headline: String,
    val detail: String,
    val showFetch: Boolean,
)

/**
 * [city] steht vollstaendig in der Ueberschrift — auch mehrteilige Namen wie
 * „Bad Mergentheim".
 *
 * [onlineEnabled] ist NICHT dasselbe wie `settings.useOnline` allein: es muss
 * bereits ausdruecken, ob ein Abrufversuch ueberhaupt etwas bewirken KANN.
 * Im offline-Flavor gibt es keinen Abrufmechanismus (`OfficialTimesProvider.
 * fetcher` liefert dort `null`) — dort waere „Jetzt abrufen" eine Luege, ganz
 * unabhaengig davon, ob ein Schalter zufaellig auf „an" steht. Der Aufrufer
 * traegt diese Vorpruefung (Flavor UND Schalter), genau wie `canFetch` in
 * [officialStatusText] bereits `settings.useOnline && !settings.useCalculated`
 * vorrechnet, statt die Flavor-Frage hier hineinzuziehen: diese Funktion
 * bekommt Android-Context nie zu sehen, und ein zweiter Ort, an dem die
 * Flavor-Entscheidung fallen kann, waere ein zweiter Ort, an dem sie
 * auseinanderlaufen kann.
 *
 * `detail` nennt in JEDEM Fall den Notausgang (die eigene Berechnung) — der
 * Satz sagt, was der Nutzer TUN kann, nicht nur, was fehlt.
 */
fun noTimesNotice(city: String, onlineEnabled: Boolean): NoTimesNotice {
    val detail = if (onlineEnabled) {
        "Die App zeigt nur amtliche Diyanet-Zeiten. Jetzt abrufen — oder die " +
            "Berechnung als Notausgang einschalten."
    } else {
        "Die App zeigt nur amtliche Diyanet-Zeiten. Schalte den Online-Abruf " +
            "ein oder erlaube die Berechnung als Notausgang."
    }
    return NoTimesNotice(
        headline = "Keine amtlichen Zeiten für $city",
        detail = detail,
        showFetch = onlineEnabled,
    )
}
