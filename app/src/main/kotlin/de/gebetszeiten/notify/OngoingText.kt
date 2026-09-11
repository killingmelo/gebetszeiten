package de.gebetszeiten.notify

/**
 * Untertitel der Dauerbenachrichtigung: der aktive Ort, gerendert als
 * `setSubText`. Der Untertitel steht in der Kopfzeile neben dem App-Namen und
 * ist auch auf dem Sperrbildschirm sichtbar, OHNE die Inhaltszeile zu
 * verbrauchen — dort draengen sich schon Uhrzeit, „aktuell", „bis" und die
 * Karaha-Zeile.
 *
 * Ein leerer oder nur aus Leerzeichen bestehender Ortsname ergibt `null`, also
 * gar keinen Untertitel: eine leere Kopfzeile sieht aus wie ein Fehler.
 */
internal fun ongoingSubText(city: String?): String? = city?.trim()?.ifEmpty { null }

/**
 * Der aufgeklappte Text der Dauerbenachrichtigung (`BigTextStyle`), damit
 * „runterziehen" tatsaechlich mehr zeigt als der eingeklappte Zustand.
 *
 * Reine Funktion: alle Bausteine kommen bereits aufgeloest herein — genau
 * dieselben, die `updateOngoing` heute in die eingeklappte Inhaltszeile
 * schreibt (`ongoing_at`, `ongoing_since`, `ongoing_until`,
 * `KarahaLine.text`). Neue Texte gibt es nicht, und die eingeklappte Zeile
 * bleibt unveraendert.
 *
 * [timeLine] („um 22:48") ist ein PFLICHTargument, kein Nullable: die genaue
 * Uhrzeit des naechsten Gebets muss in BEIDEN Countdown-Modi im Aufgeklappten
 * stehen. Im Stufen-Modus traegt der Titel („Noch 20+ Min bis Isha") sie
 * nicht, und genau die verlangt der Nutzer.
 *
 * Leere Bausteine werden uebersprungen, statt eine leere Zeile zu erzeugen.
 */
internal fun ongoingBigText(
    timeLine: String,
    activeLine: String? = null,
    untilLine: String? = null,
    karahaText: String? = null,
): String = listOfNotNull(timeLine, activeLine, untilLine, karahaText)
    .filter { it.isNotBlank() }
    .joinToString("\n")
