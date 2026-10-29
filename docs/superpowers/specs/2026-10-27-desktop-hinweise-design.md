# Desktop: Hinweise, Töne, Einblendungen, neuer Stil

Stand 27.10.2026. Baut auf `2026-10-25-desktop-taskleiste-design.md` auf.

## Warum

Das Taskleisten-Programm zeigt heute nur das nächste Gebet, Karaha und schickt
schlichte Windows-Toasts. Der Nutzer will am PC durch den Tag begleitet werden:
hörbare Erinnerungen, Warnungen („Vakit endet“), islamische Hinweise (Cuma,
Kandil, Ramazan, Sunnet-Fasten, Nachtgebet) mit gehaltvollen Texten aus der
Gelehrtenliteratur, und ein Erscheinungsbild, das dazu passt.

## Grundsätze (vom Nutzer)

- Alle Begriffe in DMG-Umschrift (Ǧumʿa, Ramaḍān, Saḥūr …), nicht türkisch. Die
  türkischen Beispiele unten sind die ursprüngliche Fassung; maßgeblich ist der Code.

- Amtlich oder gar nicht: Gebetszeiten und das Hicri-Datum kommen von Diyanet.
  Fehlt das Hicri-Datum für einen Tag, gibt es an dem Tag **keine** Hicri-Hinweise,
  nie ein berechnetes Datum.
- Texte (Ayet, Hadis, Gelehrtenwort) nur aus Shamela/geprüften Quellen, mit
  Quellenangabe. Nichts aus dem Gedächtnis.
- Anzeige wählbar: eigenes Banner oder Windows-Benachrichtigung.

## Ziele

1. **Hinweise aus den Gebetszeiten** (immer verfügbar)
   - *Vakit endet*: X Min (Standard 20) vor Ende jeder Gebetszeit.
     Sabah → Güneş, Öğle → İkindi, İkindi → Beginn İsfirar (Kerahat; ab da ist das
     Gebet makruh – Text sagt beides), Akşam → Yatsı, Yatsı → Imsak des Folgetags.
     Ist „Vor Karaha warnen“ auch an, ersetzt die İkindi-Warnung die İsfirar-Warnung
     (ein Ereignis, nicht zwei).
   - *Cuma*: Donnerstag nach Akşam („Cuma gecesi“), Freitag beim Güneş-Ende
     (İşrak) mit Hinweis auf Kehf/Salavat, Freitag 45 Min vor Öğle („Cuma namazı“).
   - *Sunnet-Fasten*: Sonntag und Mittwoch nach Yatsı: „Morgen ist Montag/Donnerstag“.
   - *Nafile-Fenster*: Beginn Kuşluk (Duha) und Beginn Teheccüd (letztes Nachtdrittel).
     Teheccüd nur, wenn das Folgetag-Imsak bekannt ist.
2. **Hinweise aus dem Hicri-Kalender** (nur mit Diyanet-Hicri-Datum)
   - Ramazan: Sahur-Erinnerung X Min vor Imsak (Standard 45), İftar-Vorlauf
     (Standard 10 Min) und İftar zur Akşam-Zeit.
   - Kandil-Nächte, jeweils am Vorabend nach Akşam (die Nacht beginnt mit Akşam):
     Mevlid (12 Rebiülevvel), Regaib (Nacht auf den ersten Freitag im Recep),
     Miraç (27 Recep), Berat (15 Şaban), Kadir (27 Ramazan).
   - Besondere Tage morgens: Hicri-Neujahr (1 Muharrem), Aşure (10 Muharrem, mit
     Hinweis auf 9./11. zum Fasten), Ramazan-Beginn, Arefe (9 Zilhicce),
     Ramazan Bayramı (1–3 Şevval), Kurban Bayramı (10–13 Zilhicce), erste zehn Tage
     Zilhicce (am 1.).
   - Eyyam-ı Bîz: Vorabend des 13., 14., 15. (nach Yatsı) als Fasten-Hinweis.
   - Hicri-Datum im Tages-Popup.
3. **Inhalte**: Zu jedem Anlass eine kleine Sammlung Texte (arabisch + deutsche
   Übersetzung + Quelle), aus Shamela gezogen, bevorzugt von Gelehrten der
   Herzensbildung (z. B. Ibn Raǧab *Laṭāʾif al-Maʿārif* – nach Jahreszeiten
   gegliedert –, al-Ġazālī *Iḥyāʾ*, Ibn al-Qayyim, an-Nawawī *al-Aḏkār*,
   Ibn ʿAṭāʾillāh). Je Anlass rotiert der Text nach Tag. Die deutsche Fassung ist
   eine eigene, sinngemäße Übersetzung und wird so gekennzeichnet.
4. **Töne**: Je Kategorie (Gebetszeit-Erinnerung, Vorlauf, Warnung, Hinweis) ein Ton:
   aus / eingebaut (im Code erzeugte sanfte Klänge, keine fremden Dateien) / eigene
   Datei. Eigene Lautstärke. Ezan bleibt wie bisher eigener Schalter.
   Bei „Nicht stören“ kein Ton und kein Ezan.
5. **Anzeige wählbar**: Banner (Standard) oder Windows-Benachrichtigung.
   Banner: randloses Fenster unten rechts über der Taskleiste im App-Stil, Titel,
   Text, aufklappbarer Lesetext mit Quelle, Knöpfe „Ezan stoppen“ (nur wenn er
   läuft) und „Schließen“; schließt nach einstellbarer Zeit (Standard 30 s,
   0 = bleibt), nimmt keinen Fokus. Mehrere gleichzeitig stapeln sich.
   Bei „Nicht stören“: Banner erscheint trotzdem, aber still.
6. **Stil**: Ein gemeinsames Farbschema für Popup, Banner und Einstellungen;
   hell/dunkel folgt Windows (`AppsUseLightTheme`). Tages-Popup neu: Ort, Datum +
   Hicri-Datum, nächstes Gebet groß mit Restzeit, sechs Zeiten, Karaha- und
   Nafile-Fenster, Zeile „Heute“ (Cuma/Kandil/Ramazan …), Text des Tages.
   Einstellungen in Reitern: Ort · Erinnerungen · Hinweise · Töne & Ezan · Programm,
   mit „Probe“-Knopf für Ton und Banner.
7. **Tray**: zusätzlich „Töne 1 Stunde aus“ (Umschalter) und „Banner testen“.

## Nicht-Ziele

- Keine berechnete Gebetszeit, kein berechnetes Hicri-Datum.
- Kein Installer, keine Änderung an Handy-App-Verhalten (nur gemeinsamer Code
  wandert nach `core-prayertimes`, die App ruft ihn auf).
- Kein Ändern des Cache-Formats, das die Handy-App teilt.
- Keine Texte ohne Quelle, keine Fiqh-Urteile über Meinungsunterschiede hinaus.

## Technik (Builder-Entscheidungen, meine Wahl)

- Hicri-Datum: eigene Datei `hijri.txt` im Datenordner (`yyyy-MM-dd\tTag\tMonat\tJahr`).
  `DiyanetYearPageParser.parseHijri(html)` liest Spalte 2 aus tab-1/tab-2. Der Desktop
  holt die Seite für die eigene Diyanet-ID höchstens einmal täglich, wenn das Hicri-Datum
  für heute + 30 Tage fehlt. Unabhängig vom Zeiten-Quorum (Kalender ist ortsunabhängig).
- Ereignisse bleiben rein und getestet: `notificationEvents(today, tomorrow, hijri, settings)`
  in `notify/`, neue `EventKind`s; Hicri-Regeln in eigener Datei `IslamicDays.kt`.
- `IslamicWindows.nafl` wandert als reine Funktion nach `core-prayertimes`
  (`Nafile.kt`); die App delegiert.
- Inhalte: `desktop/src/main/resources/hinweise/texte.tsv`
  (`anlass\tarabisch\tdeutsch\tquelle\tbuch_id\tseite\thinweis`). Leer oder fehlend →
  Hinweise ohne Lesetext.
- Töne: PCM-Synthese → `Clip`, gleiche Lautstärkekurve wie der Ezan.
- Theme: Registry-Wert per JNA `Advapi32Util`, beim Öffnen jedes Fensters gelesen.

## Offen für den Builder

Genaue Banner-Maße, Klangfarben der eingebauten Töne, Reihenfolge der Reiter-Inhalte.
