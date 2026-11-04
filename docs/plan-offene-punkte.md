# Plan: offene Punkte (Stand 04.11.2026)

Noch nichts umgesetzt. Weitere Punkte kommen dazu, danach wird in der hier festgelegten Reihenfolge gebaut.

## 1. Ortsfeld zeigt nach der Auswahl nichts an

**Befund.** In der Ersteinrichtung (Schritt 1) und im Einstellungsblatt ist das Feld leer, sobald eine Stadt gewählt wurde. Erst beim erneuten Antippen erscheint die Stadt in blasser Schrift. Die Auswahl wird trotzdem gespeichert.

**Ursache** (`app/src/main/kotlin/de/gebetszeiten/ui/PlacePicker.kt`):
- Der Suchtext hängt per `rememberSaveable(currentCity)` an der gewählten Stadt. Nach der Auswahl ändert sich `currentCity`, der Suchtext wird neu angelegt und ist damit leer.
- Die Stadt steht nur als Platzhalter (`placeholder`). Material3 zeigt den Platzhalter erst, wenn das Feld Fokus hat. Ohne Fokus sieht man nur die Beschriftung „Stadt“.

**Vorschlag.**
- Beschriftung „Stadt“ immer klein oben halten (Material3 `labelPosition` mit dauerhaft verkleinerter Beschriftung, Compose-BOM 2026.05.01 sollte das haben; vor dem Umbau prüfen).
- Ohne Fokus steht die gewählte Stadt in normaler Textfarbe (ink) im Feld, mit Fokus blass als Platzhalter („tippen, um zu ändern“).
- Das Feld bleibt ein reines Suchfeld mit leerem Entwurf. So bleibt das IME-Echo-Problem, das im Code beschrieben ist, ausgeschlossen.
- In der Ersteinrichtung entfällt damit die doppelte Zeile „Aktuell eingestellt: …“ über dem Feld. Zu entscheiden: streichen oder behalten.

**Prüfung.** Auf dem Emulator: Stadt wählen, Feld zeigt sie sofort an; erneut antippen, Suche beginnt leer; Tag und Nacht.

## 2. Kleines „?“ an Begriffen mit Erklärung

**Befund.** Auf „Heute“ lassen sich die Nebenzeilen (Ǧamāʿa, Makrūh-Zeiten, Ḍuḥā, Awwābīn, Taḥaǧǧud) antippen und öffnen den passenden Eintrag in „Zeiten verstehen“. Das ist gut, aber man sieht es nicht.

**Umfang.** Alle Nebenzeilen in `SideLine` (`HeuteScreen.kt`) führen zu einem Eintrag; `VerstehenContent.kt` hat für jeden Abschnitt einen. Die Hauptgebetszeilen sind heute nicht antippbar.

**Vorschlag.**
- Hinter dem Namen ein kleines „?“ im Kreis, Farbe Nebentext (stone), etwa 14 dp, mittig zur Textzeile. Eigenes Vektor-Icon `ic_help_small`, kein Material-Icon-Paket nötig.
- Das Icon ist Zierde: die ganze Zeile bleibt die Tippfläche (mindestens 48 dp), TalkBack liest es nicht vor (die Zeile sagt schon „Erklärung zeigen“).
- Bei vergangenen Zeilen (stone) und Makrūh (gold) das Icon in derselben Farbe wie der Name.
- Offene Frage: Sollen auch die fünf Hauptgebete antippbar werden (Einträge Faǧr, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ gibt es schon)? Dann bekämen sie ebenfalls das „?“.
- Gleiches Muster prüfen für Begriffe an anderen Stellen (Desktop-Popup `TodayPopup.kt`, Gebets-Meldung), nur wenn dort ein Eintrag erreichbar ist.

**Prüfung.** Screenshot 360x640 und große Schrift (200 %): Icon bricht nicht in eine eigene Zeile, Zeit bleibt rechtsbündig.

## 3. Aḏān und Duʿāʾ nach dem Aḏān (optional, einstellbar)

**Stand.** Läuft in einer eigenen Sitzung („Adhan und Adhan-Duʿāʾ“). Dort wurde bisher nur Bestand aufgenommen, am Code ist nichts geändert.

**Blocker.** In `ezan/` liegen drei MP3-Dateien (zusammen 7,8 MB) aus YouTube-Downloads ohne Lizenzangabe. Die dürfen so nicht ins APK. Zu klären:
- Aufnahmen mit klarer Lizenz beschaffen (eigene Aufnahme, ausdrückliche Erlaubnis des Muʾaḏḏin oder freie Lizenz), oder
- Nutzer wählt eine eigene Audiodatei aus (keine Bündelung, nur Auswahl über den System-Dateidialog).

**Grobplan** (Details in der eigenen Sitzung): an die bestehende Erinnerungsplanung anhängen, Standard aus, je Gebet schaltbar (Šurūq ohne Aḏān), Duʿāʾ an/aus, Alarm- oder Benachrichtigungskanal, Stopp per Knopf und Lautstärketaste, Foreground-Service `mediaPlayback`, Nicht-stören und Audiofokus beachten.

**Lizenzsuche** (andere Sitzung): erster Kandidat ist eine CC0-Aufnahme auf Freesound; die Lizenzseite selbst ist noch nicht geprüft.

## 4. Dauerbenachrichtigung: Stadt raus

**Befund.** „Noch 50+ Min bis…“ wird gekürzt, weil rechts daneben „Istanbul“ steht.

**Ursache.** `OngoingText.kt` setzt die Stadt als `subText` (`ongoingSubText(city)`), `PrayerNotifier.kt:336` gibt sie an `setSubText`. Je nach Hersteller (hier Samsung) steht der Untertitel in derselben Zeile wie der Titel.

**Vorschlag.** `subText` weglassen. Wer mehrere Orte nutzt, sieht den Ort weiter in der App und im Widget. Die Vorschau in der Ersteinrichtung (`OngoingPreviewUi.kt`) zieht automatisch nach. Tests in `OngoingText` anpassen.

## 5. Widget: Platz und mehr Einstellungen

**Befund.** Das Widget ist knapp gesetzt: „dann ʿAṣr um 15:38“ bricht um, und die Uhrzeit wird unten abgeschnitten.

**5a. Platzierung reparieren** (zuerst, klein). In `NextPrayerWidget.kt` und `CompactFit.kt`: Innenabstände prüfen, die letzte Zeile einzeilig halten und bei zu wenig Platz kürzen statt abschneiden (z. B. nur „ʿAṣr 15:38“). Prüfen in den Größen 2x1, 2x2, 3x2, 4x2 und mit großer Schrift.

**5b. Mehr Gestaltung** (groß, neu). Heute gibt es nur `widgetTransparent` (an/aus). Neu:
- **Farbthemen:** mehrere feste Paletten statt nur Tag/Nacht, z. B. Papier (Creme), Nacht (Schwarz), Wald (Grün), Sand (Gold/Ocker), „wie das Tagesbild“ (Farbe aus dem aktuellen Bild, wie beim Countdown in Punkt 7), „System“ (Material You). Jede Palette muss den Kontrasttest bestehen (`PaletteContrastTest` erweitern).
- **Formate:** feste Anordnungen zur Auswahl, z. B. „Nächstes Gebet groß“ (heute), „Countdown groß“, „Tagesliste“ (alle Zeiten), „Schmal“ (eine Zeile). Kein freier Baukasten: feste Formate lassen sich testen.
- **Farben und Transparenz:** Hintergrund-Deckkraft als Schieberegler (0 bis 100 %) statt Schalter, Ecken rund/eckig. Freie Farbwahl nur als Akzent; Text- und Hintergrundfarbe kommen aus dem Thema, sonst lässt sich der Kontrast nicht garantieren.
- **Wo einstellen:** Konfigurations-Activity beim Hinzufügen des Widgets (Android-Standard) mit Live-Vorschau, später über Lange-drücken > Einstellungen änderbar. Einstellungen je Widget-Instanz, damit zwei Widgets verschieden aussehen können.
- Offene Frage: Bild im Widget (wie heute in `WidgetPhotoStyle.kt`) je Thema an/aus?

## 6. Qibla-Kompass: Kalibrierung

**Antwort auf deine Frage.** Die App startet keine Kalibrierung, und Android bietet Apps dafür auch keinen Befehl. Das System kalibriert den Magnetsensor laufend im Hintergrund. Die App liest nur mit, wie genau der Sensor sich selbst einschätzt (`Compass.kt`), und zeigt erst bei „ungenau“ einen Hinweis (`qibla_compass_inaccurate`). Wenn du nichts siehst, war der Sensor genau genug. Du musst also nicht jedes Mal kalibrieren.

**Vorschlag.** Das sichtbar machen, damit niemand auf Verdacht kalibriert:
- Kleine Genauigkeitsanzeige unter dem Kompass: „Kompass genau“ (Akzent) / „Kompass ungenau“ (Gold).
- Nur bei „ungenau“ eine Anleitung: Achterbewegung als kleine Animation (mit `prefers-reduced-motion` bzw. „Animationen entfernen“ als Standbild), dazu „Telefon in einer Acht bewegen, bis die Anzeige auf genau springt.“
- Der Hinweis verschwindet von selbst, sobald der Sensor wieder genau meldet.
- Offene Frage: zusätzlich ein Knopf „Kalibrieren“, der die Anleitung jederzeit öffnet?

## 7. „Heute“: Bild in das Layout einbauen, Countdown hoch

**Befund.** Das Tagesbild steht groß oben und trägt wenig bei. Darunter stehen Name, Countdown und nächstes Gebet; die Zeitenliste beginnt erst weit unten, man muss scrollen.

**Vorschlag.**
- Das Bild wird zum Hintergrund des Kopfbereichs: oben randlos oder mit großem Radius, darauf Gebetsname („Ẓuhr“), Countdown („noch 26 Min“) und „dann ʿAṣr um 15:38“.
- Die Textfarbe wird aus dem Bild abgeleitet (hellster bzw. dunkelster Ton des Bilds, z. B. über Palette), mit Prüfung auf mindestens 4,5:1 und einem weichen Verlauf zum Grund (Schwarz/Creme) als Absicherung. Ohne Bild oder bei zu wenig Kontrast fallen die Farben auf ink/stone zurück.
- Der Satz unter dem Bild („Die Sonne hat den Zenit überschritten…“) bleibt, kleiner, direkt unter dem Countdown oder als Bildunterschrift im Bild.
- Die Zeitenliste rückt hoch, so dass auf einem 360x640-Gerät alle Hauptgebete ohne Scrollen sichtbar sind (Nebenzeilen dürfen darunter weitergehen).
- Abhängig: Punkt 5b („wie das Tagesbild“) und die Dauerbenachrichtigung können dieselbe Bildfarbe nutzen.
- Beibehalten aus DESIGN.md: Serife für Name und Countdown, keine Uhrzeiten im Bild selbst.
- Prüfung: Screenshots Tag/Nacht, 360x640 und großes Gerät, große Schrift; Kontrast für alle Tagesbilder per Test.

## Reihenfolge

1. Ortsfeld (klein, klarer Fehler). **Erledigt:** „Stadt“ steht über dem Feld, `labelPosition` gibt es in Material3 1.4 nur für die TextFieldState-Variante. Auf dem Emulator geprüft.
2. Dauerbenachrichtigung ohne Stadt (klein). **Erledigt:** `subText` samt `city`-Kette entfernt, Test sichert, dass `setSubText` nicht zurückkommt. Auf dem Emulator geprüft.
3. Widget-Platzierung 5a (klein, sichtbarer Fehler). **Erledigt im Code:** Kurzform „ʿAṣr 15:38“ bei zu wenig Breite, `maxLines = 1`, Tests in `CompactFitTest`. Auf dem Gerät noch nicht angesehen.
4. „?“-Icon (klein). **Erledigt für die Nebenzeilen**, auf dem Emulator geprüft. Hauptgebete offen.
5. Kompass-Genauigkeit und Anleitung (mittel). **Erledigt:** „Kompass genau, keine Kalibrierung nötig“ bzw. „Kompass ungenau“ mit liegender Acht (Punkt läuft sie ab, steht bei „Animationen entfernen“ still). Vor der ersten Messung keine Aussage. „Genau“ auf dem Emulator geprüft; „ungenau“ lässt sich dort nicht auslösen, nur per Test abgesichert. Kein dauerhafter Kalibrieren-Knopf.
6. „Heute“ mit Bild im Kopf (groß, Gestaltung; vorher Entwurf als Screenshot zur Freigabe)
7. Widget-Themen und Formate 5b (groß, nutzt die Bildfarbe aus 6)
8. Aḏān (groß, wartet auf die Lizenzfrage, läuft in eigener Sitzung)

## Offene Entscheidungen

- Hauptgebete antippbar mit „?“ (Punkt 2)?
- Zeile „Aktuell eingestellt: …“ in der Ersteinrichtung streichen (Punkt 1)?
- Widget: Bild je Thema an/aus; welche Themen und Formate genau (Punkt 5b)?
- Kompass: dauerhafter Knopf „Kalibrieren“ (Punkt 6)?

## Weitere Punkte

(folgen)
