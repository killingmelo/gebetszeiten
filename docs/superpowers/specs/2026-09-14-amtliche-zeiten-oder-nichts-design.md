# Amtliche Zeiten oder nichts

**Stand 14.09.2026.** Entwurf, noch nicht umgesetzt.

## Warum

`PrayerProvider.daily()` faellt heute lautlos auf die eigene Berechnung zurueck,
wenn keine amtliche Quelle liefert — ohne den vorhandenen Schalter zu fragen und
ohne es zu sagen. Am Emulator sichtbar geworden: die App zeigte „Berechnet ·
Diyanet-Methode", obwohl niemand das eingeschaltet hatte.

Nutzerwunsch: **alles muss amtlich sein.** Die Berechnung bleibt, aber nur als
bewusst eingeschalteter Notausgang.

## Entscheidungen des Nutzers

| Frage | Entscheidung |
|---|---|
| Was zeigt die App ohne amtliche Zeiten? | Klaren Hinweis statt Zeiten, mit „Jetzt abrufen" und „Berechnung einschalten". Wecker und Dauerbenachrichtigung schweigen. |
| Wie erfaehrt der Nutzer vom Ausfall? | **Einmalige** Meldung „Keine amtlichen Zeiten — Erinnerungen pausiert", dazu derselbe Hinweis im Widget. Nicht taeglich. |
| Was bedeutet der Schalter? | **Nur Luecken fuellen.** Amtliche Zeiten gewinnen immer, wenn es sie gibt. |

## Die Naht

Heute nimmt `daily()` einen `Context` und ist deshalb **von keinem Test
gedeckt** — die Quellenreihenfolge, das Herzstueck der App, ist unbelegt. Die
Regel wandert darum in eine reine Funktion, im Muster von `chooseTarget`,
`headersFor`, `ongoingTexts`, `statusOf`:

```kotlin
// core-prayertimes, damit Phone UND Wear dieselbe Regel benutzen
enum class DaySource { ONLINE_CACHE, BUNDLED_TABLE, CALCULATION }

/** Welche Quellen fuer diesen Tag befragt werden duerfen, in welcher
 *  Reihenfolge. Leer = es gibt nichts zu fragen. */
fun daySourceOrder(useOnline: Boolean, calculationFillsGaps: Boolean): List<DaySource>
```

`daily()` laeuft die Liste ab und gibt `null` zurueck, wenn keine Quelle liefert:

```kotlin
suspend fun daily(...): DailyPrayerTimes? {
    for (source in daySourceOrder(settings.useOnline, settings.calculationFillsGaps)) {
        when (source) {                                  // erschoepfend, kein stiller Pfad
            ONLINE_CACHE   -> cache.get(...)?.let { return it.toDaily(date, zone) }
            BUNDLED_TABLE  -> BundledOfficialSource.get(...)?.let { return it.toDaily(date, zone) }
            CALCULATION    -> return PrayerSchedule.forDate(settings, date, zone)
        }
    }
    return null
}
```

Die Reihenfolge bleibt, was sie war; neu ist allein, dass CALCULATION nur in der
Liste steht, wenn der Nutzer es will — und dass „nichts" ein moegliches Ergebnis
ist. Die Kurzschluss-Auswertung bleibt erhalten: die gebuendelte Tabelle wird
nicht geladen, wenn der Cache trifft.

## Der Vertrag aendert sich

`daily()`, `next()`, `nextPrayer()`, `currentlyActive()`, `activeUntil()` werden
nullable. Rund 30 Aufrufstellen muessen eine Antwort haben; der Compiler erzwingt
das an jeder einzelnen. `null` heisst genau eine Sache: **keine Zeiten unter den
aktuellen Einstellungen.** Es gibt nur diesen einen Grund, deshalb genuegt `null`
und braucht es keinen eigenen Ergebnistyp.

Betroffen:

| Bereich | Verhalten bei `null` |
|---|---|
| Heute-Ansicht | Hinweis statt Zeitachse, zwei Knoepfe |
| Monatsansicht | Leere Zeilen fuer Tage ohne amtliche Zeiten, Hinweis ueber der Tabelle |
| Widget | Derselbe Hinweis statt „naechstes Gebet" |
| Dauerbenachrichtigung | Wird entfernt, nicht mit leeren Werten gezeigt |
| Wecker (`PrayerAlarmScheduler`) | Alle Gebets- und Stufen-Wecker abbestellen |
| Karaha-Anzeige | Keine Karaha-Zeile |
| Wear | Hinweis auf der Uhr, keine Zeiten |

## Die einmalige Meldung

Zweite reine Funktion, damit die Entscheidung nicht in einem `Context`-Rumpf
verschwindet:

```kotlin
enum class PauseNotice { SHOW, CLEAR, NOTHING }
fun pauseNotice(hasTimes: Boolean, alreadyShown: Boolean): PauseNotice
```

- keine Zeiten und noch nicht gemeldet -> `SHOW`
- wieder Zeiten und war gemeldet -> `CLEAR` (Meldung zuruecknehmen, Merker loeschen)
- sonst -> `NOTHING`

Merker `pauseNoticeShown: Boolean` im DataStore. Ohne ihn kaeme die Meldung bei
jedem Wecker wieder — also mehrmals taeglich, was der Nutzer ausdruecklich nicht
wollte.

## Die Einstellung

`useCalculated` wird zu `calculationFillsGaps`. **Neuer DataStore-Schluessel mit
Migration**, nicht derselbe Schluessel mit neuer Bedeutung: der alte Wert `true`
hiess „immer rechnen", der neue heisst „Luecken fuellen". Wer ihn an hatte,
bekommt kuenftig amtliche Zeiten, wo welche da sind — eine Verbesserung, aber
eine Verhaltensaenderung, und die gehoert in eine benannte Migration statt in
eine stille Umdeutung. Muster: die `countdownMode`-Migration aus Aufgabe 16.

Wortlaut im Blatt (der heutige stimmt nicht mehr, er verspricht „Amtliche Zeiten
sind Standard, wo verfuegbar" und beschreibt damit genau den Rueckfall, den wir
abschaffen):

> **Berechnung als Notausgang**
> Wenn fuer einen Tag keine amtlichen Diyanet-Zeiten vorliegen, die lokale
> astronomische Berechnung verwenden. Amtliche Zeiten haben immer Vorrang.
> Ist dies aus, zeigt die App fuer solche Tage gar keine Zeiten.

## Wear

`WearPrayer.daily` benutzt dieselbe `daySourceOrder` aus `core-prayertimes`;
`WearSettings.useCalculated` bekommt dieselbe neue Bedeutung. Die Uhr kann NICHT
selbst abrufen — sie lebt vom Sync des Telefons und der gebuendelten Tabelle.
Ohne beides zeigt sie den Hinweis.

## Tests

- `daySourceOrder`: Tabelle ueber alle vier Kombinationen von `useOnline` und
  `calculationFillsGaps`; Reihenfolge, nicht nur Mengengleichheit.
- `pauseNotice`: alle vier Kombinationen.
- Texte des Leerfalls: reine Funktionen, wie `officialStatusText`.
- **Quelltext-Test im Haus-Idiom:** `PrayerSchedule.forDate` darf ausserhalb von
  `PrayerProvider` nicht aufgerufen werden. Sonst schleicht sich der naechste
  stille Rueckfall an einer anderen Stelle wieder ein. Praezedenz:
  `NoNetworkInSharedCodeTest`, der Quelldateien liest.
- Mutationen, die sterben muessen: `calculationFillsGaps` in der Reihenfolge
  ignorieren; `null` durch berechnete Zeiten ersetzen; den Merker der Meldung
  nie setzen.

## Folgen, die benannt gehoeren

1. **Der offline-Flavor ist bis 31.12.2026 leer** (das Bundle beginnt 2027, und
   er hat keinen Online-Pfad). Er wird nicht ausgeliefert — er ist der Nachweis
   der Netzfreiheit —, aber sein Integritaetstest und jede Handpruefung sehen
   eine leere App. Das ist richtig so und kein Defekt.
2. **Die Wear-App ohne Sync ist bis 2027 ebenfalls leer.** Sie wird
   ausgeliefert. Wer die Uhr nutzt, ohne das Telefon einmal synchronisieren zu
   lassen, sieht bis Jahresende nur den Hinweis.
3. **Orte ohne amtliche Abdeckung zeigen nie Zeiten**, solange der Notausgang
   aus ist. Das ist die Absicht, aber es trifft jeden Ort, den Diyanet nicht
   fuehrt.

## Nicht in diesem Entwurf

- Einen zweiten Schalter „immer rechnen" (Nutzerentscheidung: eine Einstellung).
- Die Berechnung selbst aendern.
- Amtliche Zeiten fuer neue Regionen beschaffen.
