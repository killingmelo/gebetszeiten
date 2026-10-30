# Neuer Play-Store-Auftritt: Name, Texte, Icon, Screenshots

Stand: 30.10.2026. Branch `feat/app-design-review`. Mockups und Bildentwürfe der Abstimmung liegen in
`.superpowers/brainstorm/1673-1793359476/` (nicht eingecheckt, `.superpowers/` steht in `.gitignore`).

## Absicht und Warum

Der Store-Eintrag zeigt eine App, die es so nicht mehr gibt: Icon (weiße Sichel auf Türkisgrün #0E7A5F),
Screenshots vom 20.09.2026 im alten Material-Design („Duha (Kuşluk)“, „Dhuhr“, „12:54–13:14“) und eine
Beschreibung voller Gedankenstriche und englisch-türkischer Begriffe. Seit der Design-Neuausrichtung
(`2026-10-28-design-neuausrichtung-design.md`) sieht die App anders aus: Creme, Waldgrün, Serife,
DMG-Umschrift.

Der Nutzer will einen Auftritt, der sich von den vielen Gebets-Apps absetzt und mehrere Suchende abholt
(„Gebetszeiten“, „Namaz Vakitleri“, „Salah“). Was die App unterscheidet, soll vorn stehen: amtliche
Diyanet-Zeiten, gegengeprüft; keine Werbung; akkusparend.

## Ziele

1. Neuer Titel und neue Kurzbeschreibung (entschieden, siehe unten).
2. Neuer langer Store-Text (de-DE) nach festem Aufbau.
3. Neues App-Icon, gleich im Store (512 × 512) und auf dem Gerät (adaptives Symbol mit einfarbiger Ebene).
4. Neue Telefon-Screenshots als zusammenhängende Reihe „Ein Tag in fünf Bildern“.
5. Neues Feature-Bild (1024 × 500) passend zu Icon und Screenshots.

## Nicht-Ziele

- Der englische Store-Eintrag (`fastlane/metadata/android/en-US/`) bleibt vorerst unverändert
  (Nutzer: „englisch zunächst egal“).
- Keine Lokalisierung der App, kein türkischer Store-Eintrag.
- Keine Änderung an Funktionen oder Zeitlogik der App.
- Das Hochladen in die Play Console macht der Nutzer selbst. Geliefert werden die Dateien.

## Anti-Ziele (Fehlschlag, auch wenn es technisch läuft)

- Ein Icon, das wie jede andere Gebets-App aussieht: Sichel, Moschee-Silhouette, Zwiebelkuppel, Gold auf
  Schwarz. Alle diese Richtungen hat der Nutzer in der Abstimmung verworfen („zu generisch“, „katastrophal“,
  Sichel „zu klischee“).
- Eine Figur, die seitlich am Miḥrāb vorbei betet. Die Figur betet zum Miḥrāb hin.
- Eine Figur, die massig wirkt („etwas zu dick“), oder eine, die nur noch aus Bogen und Punkt besteht
  („too abstrakt“).
- Eine Uhrzeit auf einem Bild, die nicht echt in der App aus amtlichen Diyanet-Daten angezeigt wird.
- Gedankenstrich (U+2014) oder Halbgeviertstrich (U+2013) in irgendeinem sichtbaren Text, auch nicht in
  Zeitspannen; KI-Floskeln.
- Begriffe in türkischer oder englischer Umschrift statt DMG (einzige Ausnahme: der Suchwörter-Satz, siehe
  unten).

## Feste Rahmenbedingungen

- DESIGN.md (Oura-Vorlage mit Abweichungen) und Palette aus `ui/theme/Color.kt`. Waldgrün #224228,
  Creme #f7f1e8, Gold #ddaa61 / #966417, Ocker #bc924c, Ton #6b443d, Nachtgrund #000000.
- Schriften wie in der App: Noto Serif Display (Light 300) für Überschriften, Inter für Fließtext.
  Beide sind gebündelt (`R.font.serif_light` usw.) und decken alle DMG-Zeichen ab.
- DMG-Umschrift mit ʿ = U+02BF und ʾ = U+02BE (Faǧr, Šurūq, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, Ḍuḥā, Makrūh).
- Nur amtliche Diyanet-Zeiten. Bilder zeigen keine selbst berechneten Uhrzeiten.
- Play-Grenzen: Titel 30 Zeichen, Kurzbeschreibung 80, langer Text 4000. Telefon-Screenshots
  höchstens 2:1, für Hervorhebung mindestens 1080 px an der kurzen Seite. Icon 512 × 512 PNG.
  Feature-Bild 1024 × 500.
- Quelldateien nie über PowerShell Get-Content/Set-Content bearbeiten (zerstört UTF-8).
- Weitere Bild-Erzeugungen über den Bildgenerator kosten Geld und brauchen vorher die Zustimmung des
  Nutzers. Ein Werkzeug, das nicht installiert ist (etwa zum Vektorisieren), wird nur nach Rückfrage
  installiert.

## Vom Nutzer entschieden

### Titel und Kurzbeschreibung

- Titel: **Gebetszeiten Islam** (18 Zeichen). „Diyanet“ kommt nicht in den Titel, weil das nach einem
  offiziellen Diyanet-Angebot aussehen könnte.
- Kurzbeschreibung: **Amtliche Diyanet-Zeiten, gegengeprüft. Ohne Werbung, ohne Konto, akkusparend.**
  (77 Zeichen). „Akkusparend“ ist im Code geprüft: kein Hintergrunddienst, kein Polling, Weckvorgänge
  nur zu Gebetsübergängen (`PrayerAlarmScheduler`, etwa ein Dutzend am Tag), Widget ereignisgetrieben.

### Suchwörter

Der lange Text darf die Suchwörter „Namaz Vakitleri“ und „Salah“ in genau einem gekennzeichneten Satz
nennen, obwohl die App selbst streng bei DMG bleibt. Beispiel: „Manche suchen nach Namaz Vakitleri,
Salah-Zeiten oder Gebetszeiten Islam. Gemeint ist dasselbe.“ Mehr nicht (Google verbietet
Keyword-Stuffing).

### Icon

Gewählt ist der Entwurf **„Mitte 2“**, Vorlage: `.superpowers/brainstorm/1673-1793359476/icon-vorlage-mitte2.png`
(1280 × 720, Bildgenerator, Rohentwurf). Was es zeigt und was daran festliegt:

- Eine Figur in Sujūd, **von schräg hinten gesehen**, die **diagonal nach Nordost** (nach oben rechts ins
  Bild hinein) auf einen Miḥrāb zu kniet. Die Fußsohlen sind links unten angedeutet.
- Anatomie: Knie und Zehen am Boden, Oberschenkel fast senkrecht, Hüfte höchster Punkt, Rücken fällt zum
  Kopf ab, Stirn auf dem Teppich, Unterarme neben dem Kopf.
- Mittlere Abstraktion: wenige schlanke Sumi-e-Pinselzüge, kein gefüllter Körper, aber Oberschenkel,
  Rücken, Kopf und Füße bleiben lesbar.
- Der Miḥrāb ist ein **andalusischer Hufeisenbogen** (unten deutlich eingezogen, wie in Córdoba), als
  Pinselzug, dicht hinter der Figur. Kein Zwiebel- oder Spitzbogen. Die Vorlage hat oben eine Spitze;
  nachgezogen wird er **oben rund** wie der Miḥrāb von Córdoba (Entscheidung aus der Abstimmung, der
  Nutzer hat sie mit „weiter“ offen gelassen).
- Kurzer Teppich als flacher Strich.
- **Waldgrün #224228 auf Creme #f7f1e8.** Keine Sichel, kein Gesicht, kein Gold, kein Schwarz.
- Figur und Bogen sollen die Kachel tragen, also groß genug sein, dass das Motiv auch als 48-px-Kachel
  noch erkennbar ist (der Nutzer wollte ausdrücklich „größer“).

### Screenshots: „Ein Tag in fünf Bildern“

Mockup: `.superpowers/brainstorm/1673-1793359476/content/screenshots-tag.html`.

- Fünf Telefon-Screenshots, Hochformat. Ein Himmel läuft als **durchgehendes Panorama** über alle fünf
  Bilder: von grauer Dämmerung über Creme und Gold bis Schwarz. Die Sonnenbahn läuft mit (Faǧr unter dem
  Horizont, Ẓuhr am höchsten, ʿAṣr im Abstieg, Maġrib auf dem Horizont, ʿIšāʾ Nacht mit Sternen).
- Jedes Bild: oben der Gebetsname in DMG groß in Noto Serif Display Light, darunter eine kleine Zeile in
  Inter, darunter eine echte Aufnahme der App in einem schlichten Rahmen.

| # | Überschrift | Zeile | Bildschirm |
|---|---|---|---|
| 1 | Faǧr | Welches Gebet gerade gilt und wie lange noch | „Heute“ am frühen Morgen, Foto Faǧr |
| 2 | Ẓuhr | Warum die Zeit dann beginnt | „Zeiten verstehen“, kürzester Schatten, Zitat, Sonnenuhr |
| 3 | ʿAṣr | Auf dem Startbildschirm und am Handgelenk | Startbildschirm mit Widget, dazu die Uhr-Kachel |
| 4 | Maġrib | Amtliche Diyanet-Zeiten, gegengeprüft | „Monat“ mit Quellenzeile |
| 5 | ʿIšāʾ | Keine Werbung, kein Konto, akkusparend | „Heute“ nachts in AMOLED-Schwarz |

- Die Zeiten auf den Aufnahmen sind echte amtliche Zeiten eines echten Ortes und Tages.

### Langer Store-Text (de-DE)

Neu geschrieben, nicht ausgebessert, in dieser Reihenfolge:

1. Quelle: amtliche Diyanet-Zeiten, gegen zwei unabhängige Quellen geprüft; wo nichts Amtliches vorliegt,
   steht ein Hinweis statt einer Schätzung; Notausgang in den Einstellungen.
2. Ohne Werbung, Konto, Tracking, Analytics und Standort-Berechtigung.
3. Akku, mit einem ehrlichen Satz, warum die App um die Ausnahme von der Akku-Optimierung bittet
   (damit Hersteller-Energiesparer die Erinnerungen nicht verschlucken), und dass die optionale
   Restzeit in der Statusleiste mehr Weckvorgänge kostet.
4. Funktionen, durchgehend in DMG.
5. Wear OS.
6. Der Suchwörter-Satz.
7. Ein Satz, dass die App nicht von Diyanet herausgegeben wird.

## Vom Bauenden zu entscheiden (Vorschläge aus der Abstimmung, nicht vom Nutzer bestätigt)

- **Icon nachziehen.** Die Vorlage wird als saubere Grafik nachgezogen, nicht als Rohbild übernommen:
  Hufeisenbogen geometrisch korrekt, Figur größer, Pinselstruktur so weit beruhigt, dass sie in 48 px
  nicht rauscht. Ob als VectorDrawable (bevorzugt, falls die Pfade handhabbar bleiben) oder als
  Bitmap-Ebenen je Dichte, entscheidet der Bauende.
- **Abnahme des Icons vor allem anderen** (Entscheidung aus der Abstimmung, nicht vom Nutzer verlangt):
  Das nachgezogene Icon ist eine neue Zeichnung. Bevor Startbildschirm-Symbol, Store-Icon, Feature-Bild
  und Screenshot 3 darauf aufbauen, sieht der Nutzer es in 512, 192, 48 und 32 px nebeneinander und gibt
  es frei. Zum Nachziehen ist OpenCV (`cv2`) vorhanden; ein weiteres Werkzeug oder neue
  Bild-Erzeugungen nur nach Rückfrage.
- **Adaptives Symbol:** Hintergrund Creme, Vordergrund das Motiv in der 66-dp-Schutzzone,
  einfarbige Ebene (`<monochrome>`) mit denselben Formen. Heute erzeugt
  `tools/notification-icons/build_icons.py` die Sichel für Statusleiste **und** Startbildschirm, und
  `playstore/make_assets.py` liest dieselbe Definition. Nach dem Umbau gibt es weiterhin genau eine Quelle
  für das Startbildschirm-Symbol und das Store-Icon; die Kommentare in beiden Skripten werden angepasst.
- **Benachrichtigungs-Symbol** (`ic_notification.xml`, Sichel in der Statusleiste) bleibt vorerst. Eine
  Pinselfigur ist in 24 dp nicht lesbar. Ein passendes Statusleisten-Symbol ist eine eigene Aufgabe.
- **Wear-Symbol** (`wear/.../drawable/ic_prayer.xml`) und das Windows-Programm bleiben vorerst.
- **Name unter dem Icon** (`app_name` in App und Uhr) bleibt „Gebetszeiten“. „Gebetszeiten Islam“ ist nur
  der Store-Titel (Entscheidung aus der Abstimmung, vom Nutzer offen gelassen).
- **Feature-Bild:** dasselbe Panorama in klein, links das neue Icon, rechts „Gebetszeiten Islam“ in der
  Serife.
- **Screenshots herstellen:** Aufnahmen vom Emulator (Ablauf in `playstore/make_screenshots.py` und in
  der Erinnerung „Emulator-UI-Verifikation“), dann ein Skript, das Panorama, Überschrift, Zeile und
  Aufnahme zu 1080 × 1920 zusammensetzt. Wie die App auf die fünf Tageszeiten gebracht wird (Uhrzeit des
  Emulators stellen o. ä.), entscheidet der Bauende. Ort: ein Ort mit eingebauten amtlichen Tabellen,
  etwa Nürnberg. `make_screenshots.py` wird ersetzt oder erweitert, es bleibt ein Ort für die Ausgabe
  (`fastlane/metadata/android/de-DE/images/phoneScreenshots/`); alte Dateien dort werden entfernt.
- **Wear-Screenshots** im eigenen Store-Fach werden mit der aktuellen Uhr-App neu aufgenommen, ohne
  Panorama.
- **Store-Texte** liegen in `fastlane/metadata/android/de-DE/` (`title.txt`, `short_description.txt`,
  `full_description.txt`); `playstore/listing_de.md` (App-Name „Lightweight Gebetszeiten“, Grafikpfade)
  und `playstore/CHECKLISTE.md` werden auf den neuen Stand gebracht.
- Den fertigen langen Text bekommt der Nutzer vor dem Hochladen zu lesen.

## Prüfung

- Kein U+2014/U+2013 in den neuen Texten und Bildbeschriftungen (per Skript prüfen).
- Längen: Titel ≤ 30, Kurzbeschreibung ≤ 80, langer Text ≤ 4000 Zeichen.
- Icon in 512, 192, 48 und 32 px nebeneinander angesehen; einfarbige Ebene im Emulator mit Themen-Icons.
- Screenshots: Seitenverhältnis ≤ 2:1, kurze Seite ≥ 1080 px; die fünf Bilder nebeneinander ergeben ein
  durchgehendes Panorama ohne Sprung an den Kanten.
- Unit-Tests `:app:testOfflineDebugUnitTest` und `:app:testOnlineDebugUnitTest` bleiben grün.
