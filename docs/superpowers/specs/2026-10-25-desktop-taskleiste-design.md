# Gebetszeiten in der Windows-Taskleiste

**Stand 25.10.2026.** Entwurf, mit dem Nutzer im Chat abgestimmt, noch nicht umgesetzt.

## Warum

Der Nutzer sitzt viel am PC und will dort auf einen Blick sehen, wann das
naechste Gebet ist — so selbstverstaendlich wie die Uhr unten rechts. Vorbild
ist die Dauerbenachrichtigung der Handy-App: „Noch 20+ Min bis Asr", mit
Karaha-Beruecksichtigung. Handy und PC duerfen sich nie widersprechen —
dieselben amtlichen Zeiten, dieselben Karaha-Fenster, dieselben Stufen.

## Entscheidungen des Nutzers

| Frage | Entscheidung |
|---|---|
| Was soll er sehen? | Wie die Handy-App: naechstes Gebet + Restzeit, Karaha beruecksichtigt. |
| Wo? | **Direkt in der Taskleiste**, wie Uhr/Wetter — nicht nur als Tray-Symbol. |
| Risiko durch Windows-Updates? | Akzeptiert. „Wir koennen es ggf. spaeter anpassen." |
| Gesamtbild (Anzeige, Benachrichtigungen, Ezan optional, Ort, Autostart, Kotlin-Modul) | Im Chat bestaetigt („passt mach"). |

## Die technische Grenze

Windows 11 hat die DeskBands abgeschafft; ein Programm kann offiziell keinen
eigenen Text in die Taskleiste schreiben. Das Wetter-Feld links gehoert dem
Widgets-Board von Microsoft und ist fuer Dritte nicht beschreibbar.

Der gangbare Weg ist der von **TrafficMonitor**: ein kleines, randloses,
nicht in der Taskleiste gelistetes Fenster wird in einen freien Bereich der
Taskleiste (`Shell_TrayWnd`) gelegt — als Kindfenster eingehaengt oder
darueber positioniert und mitgefuehrt — so dass es aussieht wie ein Teil der
Leiste. Microsoft sieht das nicht vor; nach grossen Windows-Updates kann die
Position brechen. Deshalb gibt es immer zusaetzlich ein **Tray-Symbol** als
Rueckfall (siehe unten).

## Was gebaut wird

### 1. Feld in der Taskleiste

- Sitzt **links neben dem Infobereich/der Uhr**, im freien Raum der Leiste
  (beim Nutzer: zentrierte Symbole, rechts viel Platz).
- **Zweizeilig, im Stil der Uhr** (Schrift Segoe UI, Groesse und Farbe wie die
  Uhr, Hell/Dunkel-Modus der Taskleiste folgend):
  ```
  Asr
  noch 20+ Min
  ```
- Texte und Stufen **identisch zur Handy-App**: `remainingStepShort`
  („2+ Std", „20+ Min", in den letzten 10 Min die genaue Zahl, in der letzten
  Minute „jetzt"). Gebetsnamen wie in der App (`PrayerNames`). Sonnenaufgang
  ist kein Gebet und wird wie in `PrayerProvider.nextPrayer` uebersprungen.
- **Karaha** nach `core-prayertimes/Karaha.kt` (Warnung 15 Min vorher, aktiv
  bis Fensterende). Waehrend `Soon`/`Active` springt die zweite Zeile um auf
  **genau den Text der kompakten Handy-Oberflaechen** (`KarahaDisplay`):
  „⚠️ Karaha ab 13:01" bzw. „⚠️ Karaha bis 13:21", farbig abgesetzt wie in
  der App. Die erste Zeile behaelt das naechste Gebet. *(Korrektur nach dem
  Builder-Check: im Chat stand versehentlich „Karaha in 12 Min" — der Nutzer
  will es „wie bei meiner app", und die zeigt Uhrzeiten. Das haelt auch die
  Regel „kein Minutentakt" ein.)*
- Dringlichkeit: in den letzten 10 Min vor dem Gebet (`isUrgent`) die
  Dringlichkeitsfarbe der App.
- **Ohne amtliche Zeiten**: statt einer Zahl ein kurzer Hinweis („Keine
  Zeiten"), nie eine berechnete Zeit (Grundsatz „amtlich oder nichts", siehe
  `2026-09-14-amtliche-zeiten-oder-nichts-design.md`).
- **Klick** oeffnet ein kleines Popup ueber der Taskleiste: alle sechs Zeiten
  des Tages, der Ort, die Karaha-Fenster, Quelle/Stand der Daten.
- Aktualisierung nur an den Momenten, an denen sich der Text aendert (Stufen-
  und Karaha-Grenzen), wie bei der App — kein Sekundentakt. Zusaetzlich
  korrekt nach Ruhezustand/Aufwachen und Zeitzonen-/Uhrzeitwechsel.
- Bei Vollbild-Anwendungen (Video, Spiel) blendet Windows die Taskleiste aus;
  das Feld darf dann nicht allein sichtbar ueber dem Vollbild schweben.
- Mehrere Monitore: nur auf der Haupt-Taskleiste.

### 2. Tray-Symbol (Rueckfall und Bedienung)

- Symbol im Infobereich. Tooltip mit dem vollen Text
  („Noch 20+ Min bis Asr · ⚠️ Karaha ab 13:01").
- Rechtsklick-Menue: Heutige Zeiten, Einstellungen, Taskleisten-Feld
  ein/aus, Beenden.
- Bricht das Taskleisten-Feld (Taskleiste nicht gefunden, Explorer-Neustart),
  bleibt das Tray-Symbol voll funktionsfaehig; das Feld haengt sich nach einem
  Explorer-Neustart (`TaskbarCreated`-Nachricht) selbst wieder ein.

### 3. Benachrichtigungen und Ezan (alles optional)

- **Zur Gebetszeit**: normale Windows-Toast-Benachrichtigung. Pro Gebet
  ein/aus, Voreinstellung wie die App (`DEFAULT_REMINDERS`: Fajr, Dhuhr, Asr,
  Maghrib, Isha).
- **Vorlauf** optional (z. B. 10 Min vorher), wie `reminderLeadMinutes`.
- **Karaha-Warnung** optional als Toast.
- **Ezan**: das Repo enthaelt keine Ezan-Aufnahme. Der Nutzer waehlt eine
  eigene Audiodatei (MP3/WAV); optional eine eigene Datei fuer Fajr. Ohne
  Datei nur der normale Windows-Ton. Laufender Ezan ist aus dem Toast bzw.
  Tray-Menue sofort stoppbar.
- Windows-„Nicht stoeren"/Fokus-Modus wird respektiert (Toasts landen dann im
  Benachrichtigungscenter); der Ezan spielt dann nicht.

### 4. Zeiten und Ort

- **Nur amtliche Diyanet-Zeiten**, ueber denselben Abrufweg wie die App
  (Jahresseite von namazvakitleri.diyanet.gov.tr via `DiyanetYearPageParser`
  / `DiyanetDirectFetcher`, Proxy als Rueckfall), lokal gecacht, danach
  offline lauffaehig. Auffrischung nach den Regeln aus
  `core-prayertimes/officialtimes` (Frische, Stempel, Wiederholungsbremse).
- Die gebuendelte Tabelle (`BundledOfficialSource`) als zweite Quelle, wenn
  sie ohne Android-Abhaengigkeit nutzbar ist.
- Eigenberechnung **aus**; hoechstens als ausdruecklich einzuschaltender
  Notausgang wie in der App (`calculationFillsGaps`). Die Waechtertests
  `CalculationIsNotAFallbackTest` gelten sinngemaess.
- **Ort**: einmalige Auswahl in den Einstellungen per Suche im Diyanet-Ortsindex
  (`DiyanetPlaceIndex` / `locations-world.tsv`). Kein Standort-Zugriff noetig.
- Cemaat-Anzeige wie in der App ist **kein** Ziel der ersten Fassung.

### 5. Einstellungen

Ein kleines Fenster: Ort, Benachrichtigungen pro Gebet, Vorlauf,
Karaha-Warnung, Ezan-Datei(en) und Lautstaerke, Taskleisten-Feld ein/aus,
Autostart ein/aus (Voreinstellung: an). Gespeichert in `%APPDATA%`.

## Wie es gebaut wird (Entscheidung Claude, vom Nutzer abgenickt)

- Neues Gradle-Modul **`desktop`** in diesem Repo, Kotlin/JVM.
- Es nutzt **`core-prayertimes` direkt** — eine Wahrheit fuer Karaha, Stufen,
  Cache-Regeln. Was heute nur im `app`-Modul liegt und der Desktop braucht
  und **reine Logik** ist (`remainingStepShort`, `isUrgent`, Gebetsnamen,
  der Text von `KarahaDisplay` ohne den `Context`-Teil), wandert nach
  `core-prayertimes`; die App ruft danach dieselbe Stelle. Kein Kopieren.
- **Reichweite des Umbaus (Entscheidung Claude):** `PrayerProvider`,
  `OfficialTimesCache` und `BundledOfficialSource` bleiben in der App, wie sie
  sind. Der Desktop bekommt eine **eigene, duenne Cache-/Quellen-Verdrahtung**
  (Dateien in `%APPDATA%`), die ausschliesslich die vorhandenen Regeln aus
  core nutzt (`daySourceOrder`, `chooseTarget`, `CacheStore`/Stempel). `wear`
  wird **nicht** angefasst. Die App muss fuer dieses Vorhaben **nicht** in den
  Play Store; es genuegt, dass alle bestehenden Testsuiten gruen bleiben.
- **Zielgruppe (Entscheidung Claude):** nur der eigene PC des Nutzers,
  Windows 11. Unsigniertes Per-User-Installationspaket, keine Auto-Updates,
  kein Windows-10-Zweig. Eine oeffentliche Verteilung waere ein eigenes
  Vorhaben (Signatur, Updates, Datenschutzhinweis).
- **`net-diyanet`** haengt nur in zwei Dateien an Android
  (`CompositeDiyanetFetcher`, `DiyanetPlaceIndex`: `android.content.Context`
  fuer Asset-Zugriff). Der plattformneutrale Teil (Parser, Fetcher, HTTP)
  wird so getrennt, dass `desktop` ihn ohne Android nutzen kann — z. B. als
  reines JVM-Modul plus duenner Android-Adapter. Das Verhalten der App darf
  sich dabei nicht aendern; ihre Tests muessen gruen bleiben.
- Win32-Zugriffe (Taskleiste finden, Fenster einhaengen, Tray, Toasts,
  Autostart-Registry `HKCU\...\Run`) ueber **JNA**. UI-Toolkit (Compose for
  Desktop oder Swing) waehlt der Builder.
- Auslieferung als installierbare **`.exe`/MSI mit mitgeliefertem Java**
  (~60–80 MB, vom Nutzer akzeptiert), gebaut mit `jpackage`.
- Fuer Gradle gilt wie immer: `JAVA_HOME` explizit setzen. Achtung: die
  Android-Studio-JBR enthaelt **kein `jpackage`** — fuers Paketieren wird ein
  volles JDK 17+ gebraucht (Gradle-Toolchain oder separat installiert).

## Ziele

- Ein Blick auf die Taskleiste genuegt: naechstes Gebet und Restzeit, Karaha
  sichtbar, wenn sie zaehlt.
- PC und Handy zeigen fuer denselben Ort zur selben Zeit dasselbe.
- Laeuft still: kaum CPU, kein Sekundentakt, startet mit Windows.

## Nicht-Ziele

- Kein Qibla, kein Kalender, kein Tasbih, kein Quran — die App-Identitaet
  „schlank und fokussiert" gilt auch hier.
- Kein Sync mit dem Handy (Ort wird am PC separat gewaehlt).
- Keine macOS/Linux-Fassung.
- Kein Eintrag im Microsoft-Widgets-Board.

## Anti-Ziele (wuerde es trotz Funktionieren scheitern lassen)

- Eine berechnete Zeit wird stillschweigend als amtlich gezeigt.
- PC und Handy widersprechen sich (andere Stufe, andere Karaha-Grenze).
- Das Feld flackert, ueberdeckt Vollbild, oder frisst spuerbar CPU/Akku.
- Ezan spielt unerwartet laut oder laesst sich nicht sofort stoppen.

## Dem Builder ueberlassen

- UI-Toolkit, genaue Einhaengetechnik (Kindfenster vs. mitgefuehrtes
  Overlay), Installer-Format, Speicherformat der Einstellungen.
- Wie genau `net-diyanet` aufgeteilt wird, solange die App unveraendert
  funktioniert.
- Feinheiten von Farbe und Abstand, angelehnt an die Uhr und die App.
