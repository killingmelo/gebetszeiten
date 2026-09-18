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
    return NoTimesNotice(
        headline = "Keine amtlichen Zeiten für $city",
        detail = "Die App zeigt nur amtliche Diyanet-Zeiten. " + notausgangHint(onlineEnabled),
        showFetch = onlineEnabled,
    )
}

/**
 * Der Satzteil, der sagt, was der Nutzer TUN kann — geteilt zwischen
 * [noTimesNotice] (Ein-Tag-Fall, Heute-Ansicht) und [monthNoTimesNotice]
 * (Monats-Fall), damit beide fuer dieselbe Handlung denselben Wortlaut
 * verwenden statt ihn zweimal einzeln zu formulieren.
 */
private fun notausgangHint(onlineEnabled: Boolean): String = if (onlineEnabled) {
    "Jetzt abrufen — oder die Berechnung als Notausgang einschalten."
} else {
    "Schalte den Online-Abruf ein oder erlaube die Berechnung als Notausgang."
}

/**
 * Die Hinweiszeile ueber der Monatstabelle (`MonatScreen`), wenn mindestens
 * ein Tag ohne amtliche Zeiten dabei ist ("—" in allen Spalten).
 *
 * [noTimesNotice] wurde in Aufgabe 10 fuer den EIN-TAG-Fall der Heute-Ansicht
 * formuliert: „Keine amtlichen Zeiten für <Ort>" ist dort immer wahr, weil es
 * an diesem einen Tag um genau diese Aussage geht. Fuer einen Monat stimmt
 * derselbe Satz nur, wenn ALLE Tage betroffen sind. Fehlt dagegen nur ein
 * einzelner Tag — typischerweise genau der Tag nach dem Ende der
 * gebuendelten Abdeckung —, gibt es fuer den Ort sehr wohl amtliche Zeiten,
 * nur nicht fuer diesen einen Tag; „keine amtlichen Zeiten für <Ort>" waere
 * dann schlicht falsch (Fix-Runde 1, Aufgabe 11).
 *
 * [emptyDays] zaehlt die Tage dieses Monats ohne Zeiten (1..[totalDays]),
 * [totalDays] die Tage des Monats insgesamt. Beide Faelle enden mit
 * [notausgangHint] — wie bei [noTimesNotice] sagt der Satz, was der Nutzer
 * TUN kann, nicht nur, was fehlt, und zwar in BEIDEN Faellen, nicht nur im
 * Teilmonat-Fall.
 */
fun monthNoTimesNotice(city: String, onlineEnabled: Boolean, emptyDays: Int, totalDays: Int): String {
    require(emptyDays in 1..totalDays) {
        "emptyDays ($emptyDays) muss zwischen 1 und totalDays ($totalDays) liegen"
    }
    val lead = if (emptyDays == totalDays) {
        // Ganzer Monat leer: dieselbe Aussage wie im Ein-Tag-Fall ist hier
        // tatsaechlich wahr, also derselbe Wortlaut (keine zweite Kopie).
        noTimesNotice(city, onlineEnabled).headline
    } else {
        val dayPhrase = if (emptyDays == 1) "einen Tag" else "$emptyDays Tage"
        "Für $dayPhrase in diesem Monat fehlen amtliche Zeiten"
    }
    return "$lead. " + notausgangHint(onlineEnabled)
}
