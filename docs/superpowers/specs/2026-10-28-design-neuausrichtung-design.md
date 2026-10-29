# Design-Neuausrichtung der Android-App: „Heute“ auf einen Blick, „Zeiten verstehen“

Stand: 28.10.2026. Grundlage: Branch `feat/app-design-review` (Commit b8fb783, enthält die
Design-Korrekturen und die DMG-Hijri-Monate), der wiederum auf `feat/desktop-hinweise` aufsetzt.
Mockups der Abstimmung: `.superpowers/brainstorm/1271-1793177361/content/` (`fotos-oura.html`,
`haupt-ohne-uhr.html`, `sonnenuhr-oura-v2.html`).

## Absicht und Warum

Die Korrekturrunde (Farben, Kontrast, Tippziele) hat die App technisch verbessert, sah aber „fast
so aus wie vorher“. Der Nutzer will ein spürbar besseres, eigenständiges Design. Sein Kern:

> „ich will auf einen blick wissen, wann ist das aktuelle gebet und wann das nächste, wie viel
> zeit habe ich noch“

Die Bildsprache soll aus dem kommen, womit die klassischen Fiqh-Werke die Zeiten bestimmen: Licht
am Horizont, Sonne im Zenit, Schatten, Sonnenuntergang, erloschene Dämmerung, Sterne. Erklärungen
dazu sollen als direkte Zitate hanafitischer Gelehrter erscheinen, nicht in eigenen Worten.

## Ziele

1. Reiter „Heute“ beantwortet auf einen Blick: laufendes Gebet, Restzeit, nächstes Gebet mit Uhrzeit.
2. Jede Gebetszeit hat ein Foto, das ihre Fiqh-Bestimmung zeigt (Ẓuhr: kürzester Schatten,
   ʿAṣr: lange Schatten, Maġrib: Sonne am Horizont, ʿIšāʾ: Sterne, Faǧr: Licht am Horizont).
3. Neuer Reiter „Zeiten verstehen“: je Gebetszeit Foto, Zitat (Arabisch + Deutsch) mit Quelle,
   dazu eine berechnete Sonnenuhr als Anschauung.
4. Ein durchgängiges, professionelles Erscheinungsbild nach einer festen Vorlage (DESIGN.md
   „Oura“) über alle Bildschirme.

## Nicht-Ziele

- Keine neuen Zeitquellen, keine Änderung der Zeitlogik (`PrayerProvider.daily` bleibt die einzige
  Quelle).
- Keine Lokalisierung in weitere Sprachen.
- Keine verwässernden Funktionen (Tasbih, Quran, Soziales).
- Wear-App und Desktop-Programm sind nicht Teil dieser Runde.

## Anti-Ziele (wäre ein Fehlschlag, auch wenn es technisch läuft)

- Selbst gezeichnete, laienhaft wirkende Illustrationen. Die Sonnenuhr ist nur zulässig, weil sie
  geometrisch exakt berechnet ist; sie steht nicht auf „Heute“ („die sonnenuhr checkt keiner“).
- Text, der über einem Foto steht und dadurch schlecht lesbar ist.
- Eine berechnete Uhrzeit, die als Gebetszeit erscheint. Auch für ʿAṣr mit doppeltem Schatten
  wird keine Uhrzeit angezeigt.
- Zitate, die nicht aus einem Werkzeug-Ergebnis (Shamela) stammen, oder eigene Paraphrasen als
  Gelehrtenaussage.
- KI-Sprache in Texten: Floskeln, Dreierketten, „nicht nur … sondern“, Pathos.

## Feste Rahmenbedingungen

- **Nur amtliche Diyanet-Zeiten** (Grundsatz des Nutzers). Bilder und Sonnenuhr veranschaulichen,
  sie liefern keine Zahlen.
- **DMG-Umschrift** für alle Begriffe (Faǧr, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, Ḍuḥā, Makrūh, Zawāl …).
- **Schreibregeln für alle sichtbaren Texte:** keine Gedankenstriche (—) und keine
  Halbgeviertstriche (–), auch nicht in Zeitspannen („11:45 bis 12:05“); keine KI-Floskeln.
  Bestehende Strings mit solchen Strichen werden in dieser Runde mit umgestellt.
- Barrierefreiheit wie in der Korrekturrunde: Kontrast ≥ 4,5:1 für Text, Tippziele ≥ 48 dp,
  TalkBack-Beschreibungen, Schriftskalierung bis 1,4 ohne abgeschnittene Inhalte,
  `rememberAnimationsEnabled()` für jede Bewegung.
- Nachts AMOLED-Schwarz.
- Offline-Flavor hat keine INTERNET-Permission: alle Fotos und Schriften werden gebündelt.
- Quelldateien nie über PowerShell Get-Content/Set-Content bearbeiten (UTF-8).
- Unit-Tests `:app:testOfflineDebugUnitTest` und `:app:testOnlineDebugUnitTest` bleiben grün.

## Vom Nutzer entschieden

1. **Hauptansicht ohne Sonnenuhr**, typografisch: oben Ort und Datum als kleine Zeile, darunter
   groß das laufende Gebet, „noch 1 Std 18 Min“, „dann ʿAṣr um 14:38“, darunter die Tagesliste
   mit feinen Trennlinien; vergangene Zeiten verblasst, laufendes Gebet mit grünem Punkt.
2. **Fotos je Gebet** oben auf „Heute“ als abgerundetes Bild mit kurzer Bildunterschrift; der Text
   steht darunter auf ruhigem Grund. Bestätigt mit „sehr schön. passt gut.“
3. **Vierter Reiter „Zeiten verstehen“** mit Sonnenuhr und Erklärungen.
4. **Zitate auf Arabisch mit deutscher Übersetzung.**
5. **Bei ʿAṣr beide Schattenlängen berücksichtigen**; Diyanet liefert nach Kenntnis des Nutzers
   nur den einfachen Schatten (ʿAṣr-ı evvel). Angezeigt wird nur die amtliche Zeit.
6. **Stil-Vorlage:** DESIGN.md „Oura“ aus VoltAgent/awesome-design-md (vom Nutzer im Mockup
   angenommen).
7. **Widget und Benachrichtigungen bekommen den vollen Neuentwurf** („lass all das ebenfalls
   überarbeiten, ja“).
8. **Platzhalter-Fotos dürfen heruntergeladen und eingebaut werden**; die Endauswahl wird vor einer
   Veröffentlichung getauscht („ja“).

## Gestaltungssystem (aus der Oura-Vorlage abgeleitet)

Quelle: `https://raw.githubusercontent.com/VoltAgent/awesome-design-md/main/design-md/oura/DESIGN.md`.
Laut Nutzer-CLAUDE.md kommt die Datei als `DESIGN.md` in den Projekt-Root und wird in der
Projekt-CLAUDE.md per `@`-Import eingebunden. Abweichungen werden dort oben vermerkt.

**Farben (Tag):** Grund `#f7f1e8` (Papier-Creme), Fläche `#ffffff`, Tinte `#1c1b1a`, Stein
`#4a4741`, Asche `#838280` (nur für große oder nicht-wesentliche Texte, Kontrast prüfen),
Haarlinie `#e1dcd3`, Akzent Waldgrün `#224228` (laufendes Gebet, Auswahl), Sonne Gold `#af751b`
und Ocker `#bc924c` (Makrūh, ʿAṣr-Linie), Fehler `#e33a22`.
**Farben (Nacht):** Grund `#000000`, Fläche `#19191c`, Text `#f7f1e8`, gedämpft `#a8a5a0`,
Haarlinie `#232326`, Akzent Salbei `#849671`, Gold `#ddaa61`.
**Abweichung von Oura:** Ouras einzelnes Aktionsblau `#2a72de` entfällt; Aktionen nehmen Waldgrün.
Die bisherige grüne Markenfarbe `#0E7A5F` und die Hochkontrast-Schemata werden auf diese Palette
umgestellt (Hochkontrast: Tinte `#000000` auf `#ffffff` bzw. umgekehrt, Akzente abgedunkelt bzw.
aufgehellt bis ≥ 7:1).

**Schrift:** große, leichte Serife für Gebetsname und Restzeit; ruhige Grotesk für alles andere.
Oura nutzt lizenzpflichtige Schriften (Editorial New, Akkurat); ersetzt durch freie Schriften
(OFL), die **alle DMG-Zeichen** sauber setzen: ʿ ʾ (U+02BF/U+02BE), ǧ Ǧ, ḍ ḥ ṣ ṭ ẓ (U+1E0x bis
U+1E9x), ṯ ḫ ḏ ġ š ā ī ū. Im Mockup setzte Source Serif 4 das ʿ nicht sauber; die Wahl muss mit
einem Glyphen-Test belegt werden. Für Arabisch eine Naskh-Schrift (z. B. Noto Naskh Arabic oder
Amiri), rechtsbündig, mit ausreichender Zeilenhöhe.
**Formen:** Haarlinien statt Karten; Bildrahmen Radius 24 dp; Abstände 8/12/16/24/32 dp; keine
harten Schatten.

## Bildschirme

### Heute

- Schmale Kopfleiste bleibt: Ortsname (Antippen öffnet wie bisher die Favoriten, wenn vorhanden),
  rechts das Einstellungs-Symbol. Unten vier Reiter: Heute, Monat, Qibla, Zeiten verstehen.
  *(Entscheidung Claude.)*
- Darunter Datum als Großbuchstaben-Zeile; Hijri-Datum (DMG) dahinter oder darunter.
- Foto des laufenden Abschnitts, Radius 24 dp, darunter eine Bildunterschrift (ein Satz, keine
  Striche).
- Gebetsname groß (Serife, leicht), „noch …“ (Serife), „dann <nächstes Gebet> um HH:MM“.
- Tagesliste: Faǧr, Šurūq (als Moment, nicht als Gebet), Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ. Vergangene
  verblasst (Kontrast ≥ 4,5:1), laufendes Gebet mit Punkt in Akzentfarbe.
- **Titel-Zustände** *(Entscheidung Claude, folgt der heutigen Logik)*: läuft ein Pflichtgebet,
  steht sein Name. Zwischen Sonnenaufgang und Ḍuḥā-Beginn steht „Šurūq“, im Ḍuḥā-Fenster „Ḍuḥā“,
  jeweils mit „dann Ẓuhr um …“; beide nutzen das Šurūq/Ḍuḥā-Foto. Vor Faǧr (nach Mitternacht)
  gilt ʿIšāʾ des Vortags als laufend.
- **Makrūh live** *(Entscheidung Claude)*: fällt „jetzt“ in eine Makrūh-Zeit oder steht sie in
  den nächsten 15 Minuten bevor (bestehende Logik `KarahaCountdown`), erscheint unter der
  Restzeit eine goldene Zeile, z. B. „Makrūh bis 12:05, noch 8 Min“. Keine Farbflächen.
- **Liste** *(Entscheidung Claude)*: immer der ganze Tag; nach ʿIšāʾ als letzte Zeile der Faǧr
  des Folgetags (sofern vorhanden). Das bisherige Auf-/Zuklappen entfällt.
- Makrūh-Zeiten, Ḍuḥā, Auwābīn, Tahaǧǧud und Ǧamāʿa erscheinen als schlichte Nebenzeilen an
  ihrem Platz in der Liste, ohne Chips und ohne Farbflächen; Antippen führt zum passenden Abschnitt
  in „Zeiten verstehen“ (ersetzt die bisherigen Erklärungsdialoge).
- Nach ʿIšāʾ zählt die Restzeit bis Faǧr des Folgetags; fehlt dieser (letzter abgedeckter Tag),
  entfällt die Restzeit, statt etwas zu erfinden (bestehendes Verhalten).
- Hinweise (Benachrichtigungen aus, keine Zeiten vorhanden) bleiben in Funktion und Wortlaut,
  werden im neuen Stil gesetzt.
- Datumswechsel (Vortag/Folgetag) bleibt erhalten. An anderen Tagen kein „noch …“, nur Foto des
  ersten Gebets und Liste.

### Zeiten verstehen (neuer Reiter)

- Je Abschnitt: Foto, Titel (DMG), arabisches Zitat, deutsche Übersetzung, Quellenangabe
  (Werk, Autor, Sterbejahr, Band/Seite wie von `shamela_get_citation` geliefert, inkl. Hinweis
  „bei Shamela automatisch nummeriert“, wo zutreffend).
- Abschnitte: Faǧr, Šurūq und Makrūh beim Aufgang, Ḍuḥā, Ẓuhr und Zawāl, ʿAṣr (einfacher und
  doppelter Schatten; Satz, dass die angezeigte Zeit die amtliche Diyanet-Zeit ist), Makrūh beim
  Vergilben, Maġrib, ʿIšāʾ, Tahaǧǧud, Auwābīn, Ǧamāʿa am Morgen.
- Sonnenuhr (im ʿAṣr- und Ẓuhr-Abschnitt): horizontale Sonnenuhr, berechnet für die Breite des
  eingestellten Orts, mit Datumsbögen (21. Dez, Tagundnachtgleiche, 21. Juni), Mittagslinie,
  ʿAṣr-Kurven für einfachen und doppelten Schatten, heutigem Sonnenpfad und aktueller
  Schattenspitze. Keine Uhrzeiten in der Grafik. Referenz-Geometrie:
  scratchpad-Skript `dial.py`/`mk.py` dieser Sitzung (Formeln: Schattenspitze
  = −h·(Ost, Nord)/Höhe; ʿAṣr bei cot(Höhe) = k + cot(Mittagshöhe), k = 1 bzw. 2). Prüfwert:
  Nürnberg, 28.10.2026, einfacher Schatten ≈ 14:34, amtlich 14:38.
- Fehlt in den bevorzugten Werken ein passendes Zitat *(Entscheidung Claude)*: weitere
  hanafitische Werke der lokalen Shamela-Bibliothek (z. B. Ibn ʿĀbidīn, aṭ-Ṭaḥṭāwī), aus
  shamela-extern nur Qualitätsstufe A oder B. Findet sich nichts, bekommt der Abschnitt kein
  Zitat und keinen Ersatztext. Die Recherche gehört zur Umsetzung.
- Quellen der Zitate: aus den in Shamela heruntergeladenen hanafitischen Werken, bevorzugt
  aš-Šurunbulālī, „Marāqī l-falāḥ“ (book_id 412, Gebetszeiten ab page_id 80), al-Qudūrī,
  „Muḫtaṣar“ (124336), al-Marġīnānī, „al-Hidāya“ (11820). Nur Text aus Werkzeug-Ergebnissen; Matn
  und Fußnote getrennt halten.
- Die deutsche Übersetzung ist eine eigene Übersetzung und wird als solche gekennzeichnet; der
  Nutzer prüft sie vor dem Merge.

### Monat, Qibla, Einstellungen, Ersteinrichtung

Gleiche Palette, Schrift und Haarlinien. Monat: Tabelle mit Haarlinien, heute mit Akzentpunkt und
Fettung statt Farbfläche. Qibla: Kompass in Tinte/Gold, gleiche Typografie. Einstellungen und
Ersteinrichtung: Abschnitte mit Haarlinien und Großbuchstaben-Überschriften statt Karten; alle
bestehenden Steuerungen und ihr Verhalten bleiben unverändert.

### Widget

Voller Neuentwurf nach dem Vorbild von „Heute“: kleine Größe mit Gebetsname groß, Restzeit,
„dann … um …“; große Größe zusätzlich mit dem Foto des laufenden Abschnitts (dunkler Verlauf
unter der Schrift) und der Tagesliste. Glance kann keine eigenen Schriftdateien laden; dort gilt
die Systemschrift. Bestehendes Verhalten bleibt: transparente Variante, System-Chronometer für
„Genau“, Dringlichkeitsfarbe, Leer-Darstellung ohne Zeiten, Antippen öffnet die App. Neue
Widget-Vorschau (`widget_preview.xml`) entsprechend anpassen.

### Benachrichtigungen (Daueranzeige und Gebets-Meldung)

Android gibt Schrift und Grundaufbau vor (eigene Layouts ab Android 12 stark beschnitten). Neu:
Akzentfarbe der Palette, eigenes kleines Symbol je Gebet (Vektor, einfarbig, mit
Restzeit-Symbolen konsistent), Wortlaut nach den Schreibregeln, beim Aufklappen das Foto des
Gebets (`BigPictureStyle` oder gleichwertig). Wecklogik, Kanäle und Stufen bleiben unverändert;
der Akku-Grundsatz (keine zusätzlichen Weckvorgänge) gilt weiter.

### Qibla-Genauigkeit (Fehlerbehebung)

- Die Gradzahl (Großkreis-Anfangsrichtung, `QiblaMath.bearing`) ist korrekt; die Erdkrümmung ist
  berücksichtigt, die Ellipsoid-Abweichung (≤ 0,2°) bleibt bewusst unberücksichtigt.
- **Fehler:** `Compass.kt` liefert die Richtung zum magnetischen Nordpol, die Qibla-Rechnung
  bezieht sich auf geografisch Nord. Korrektur über `android.hardware.GeomagneticField`
  (Breite, Länge, Höhe ≈ 0, aktuelle Zeit) mit `declination`; keine neue Berechtigung. Test für
  die Umrechnung (reine Funktion).
- Sensorgenauigkeit aus `onAccuracyChanged` auswerten; bei geringer oder unzuverlässiger
  Genauigkeit Hinweis „Kompass ungenau. Handy in Form einer Acht bewegen.“
- Ein ruhiger Satz zu Störquellen (Metall, Magnethüllen, Lautsprecher).
- Kontrolle über den Sonnenstand über der Kaaba ist eine **offene Idee** für „Zeiten verstehen“,
  nur auf ausdrücklichen Wunsch des Nutzers.

## Fotos

- Sechs Motive: Faǧr, Šurūq/Ḍuḥā, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ. Platzhalter aus der Abstimmung
  (Unsplash): Ẓuhr `kGXST_-n36w` (Palmenschatten), ʿAṣr `KhyzfY9h4hc` (lange Schatten), Maġrib
  `x1FCSyl6GJU` (Minarett vor Sonnenuntergang), ʿIšāʾ `Cq5NaI0yKBE` (Sterne), Faǧr `pbuMoKhf07U`
  (Licht am Horizont). Für Šurūq/Ḍuḥā fehlt noch ein Motiv.
- Platzhalter dürfen heruntergeladen und eingebaut werden (Nutzer: „ja“); Endauswahl mit dem
  Nutzer vor einer Veröffentlichung; eigene Fotos des Nutzers sind möglich. Für Šurūq/Ḍuḥā wählt
  der Umsetzer ein Morgensonnen-Motiv aus Unsplash.
- Gebündelt als WebP, zusammen höchstens etwa 1,5 MB; Fotografen-Nennung in den Einstellungen
  unter einem Abschnitt „Über“ (Unsplash-Lizenz verlangt sie nicht, sie ist aber fair).
- Jedes Foto hat eine `contentDescription`, die sagt, was es zeigt.

## Dem Umsetzer überlassen

- Komponentenschnitt in Compose, Dateiaufteilung, Navigation zum Abschnitt per Anker.
- Konkrete Schriftwahl innerhalb der Bedingungen oben (mit Glyphen-Test).
- Animation des Fotowechsels beim Gebetswechsel (dezent, abschaltbar über reduced motion).
- Reihenfolge der Umsetzung in sinnvollen, einzeln prüfbaren Schritten.

## Nachweis „fertig“

- Vorher/Nachher-Screenshots im Emulator: hell, dunkel, Schrift 1,3, kleines Handy 360×640 dp,
  für Heute, Zeiten verstehen, Monat, Qibla, Einstellungen, Ersteinrichtung, Widget (klein und
  groß) und Benachrichtigung (zu- und aufgeklappt).
- Kontrastmessung der neuen Palette (Skript wie in der Korrekturrunde) ≥ 4,5:1 für alle Texte.
- Glyphen-Test der Schriften mit allen DMG-Zeichen der App.
- `grep` über `strings.xml` und Kotlin-Texte: keine — und keine – in sichtbaren Texten.
- Beide Unit-Test-Suiten grün.
