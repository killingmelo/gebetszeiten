# Gebetszeiten für die Windows-Taskleiste

Kleines Windows-11-Programm: ein Feld links neben der Uhr zeigt das nächste Gebet und
die Restzeit (bzw. die Karaha-Zeile), genau wie die Benachrichtigung der Handy-App.
Dazu ein Tray-Symbol (Heutige Zeiten, Einstellungen, Feld an/aus, Töne 1 Stunde aus,
Ezan stoppen, Benachrichtigung testen, Beenden), Erinnerungen als eigenes Banner oder
Windows-Benachrichtigung mit Tönen, optional ein Ezan.

Erinnerungen und Hinweise:

- Gebetszeit, Vorlauf, „Gebetszeit endet“ (X Min vor Ende; ʿAṣr endet mit der Iṣfirār-Karāha),
  Karāha-Warnung.
- Ǧumʿa (Donnerstagabend, Freitagmorgen, vor dem Ǧumʿa-Gebet), Sunna-Fasten am Vorabend
  (Mo/Do, al-Ayyām al-Bīḍ), Ḍuḥā und Tahaǧǧud.
- Aus dem Hiǧra-Kalender: besondere Nächte (Qadr, Barāʾa, Miʿrāǧ, Raġāʾib, Maulid),
  Ramaḍān (Saḥūr, Ifṭār), ʿĀšūrāʾ, ʿArafa, ʿĪd al-Fiṭr/al-Aḍḥā, Hiǧra-Neujahr. Das
  Hiǧra-Datum kommt nur von der Diyanet-Seite (`hijri.txt`); fehlt es, gibt es keine
  Hiǧra-Hinweise. Alle Begriffe in DMG-Umschrift.
- Lesetexte zu jedem Anlass aus `src/main/resources/hinweise/texte.tsv` (aus Shamela,
  arabisch + sinngemäße Übersetzung + Quelle), täglich wechselnd.
- Tageshinweise (Ǧumʿa, ʿĪd …) werden nachgeholt, wenn das Programm erst später startet.

Nur amtliche Diyanet-Zeiten: abgerufene Zeiten aus dem Cache, sonst die mitgelieferte
Tabelle. Ohne beides zeigt das Feld „Keine Zeiten“, nie eine berechnete Zahl.

## Bauen

```powershell
Set-Location <Repo>
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat --console=plain :desktop:test          # Tests
.\gradlew.bat --console=plain :desktop:packageApp    # Programmordner
```

Ergebnis: `desktop\build\package\Gebetszeiten\Gebetszeiten.exe` (Programmordner mit
eigener Java-Laufzeit, kein Installer – ein MSI bräuchte das WiX-Toolset). Den ganzen
Ordner `Gebetszeiten` kopieren, nicht nur die .exe.

`packageApp` braucht `jpackage` aus einem vollen JDK; das JBR von Android Studio hat
keins. Standard ist eine Java-25-Toolchain (auf dem Entwicklungs-PC Temurin 25 unter
`C:\Program Files\Eclipse Adoptium`, Gradle findet es selbst). Anderes JDK:
`-PjpackageJdk=<Version>`.

## Starten

- `Gebetszeiten.exe` doppelklicken. Ein zweiter Start beendet sich still (Lock-Datei).
- Beim ersten Start ohne Ort öffnet sich das Einstellungsfenster; das Feld zeigt bis
  dahin „Keine Zeiten“.
- Autostart: Häkchen in den Einstellungen; setzt `HKCU\...\CurrentVersion\Run\Gebetszeiten`.
  Wirkt nur aus der gepackten `Gebetszeiten.exe`, nie aus Gradle/IDE.
- Entwicklungslauf ohne Paket: `.\gradlew.bat :desktop:run`. Handproben:
  `--demo-field`, `--demo-tray` (mit `--shot=popup.png --banner-shot=banner.png`),
  `--demo-settings` (mit `--shot=s.png`: ein Bild je Reiter).

## Wo die Daten liegen

Standard `%APPDATA%\Gebetszeiten\`:

| Datei | Inhalt |
|---|---|
| `settings.properties` | Einstellungen (UTF-8) |
| `cache.txt` | abgerufene Diyanet-Zeiten |
| `hijri.txt` | Hiǧra-Datum je Tag von der Diyanet-Seite (heute bis +30 Tage, täglich nachgeholt) |
| `lock` | Einzelinstanz-Sperre |
| `gebetszeiten.log` | Protokoll des letzten Laufs (die .exe hat keine Konsole) |

Anderer Ordner (Tests, zweites Profil): Systemeigenschaft `-Dgebetszeiten.dataDir=<Ordner>`
oder Umgebungsvariable `GEBETSZEITEN_DATA_DIR=<Ordner>` (die Eigenschaft gewinnt).

## Wie es läuft

- Ein Takt-Faden rechnet die Anzeige neu: zur nächsten Anzeigeänderung, zum nächsten
  Erinnerungszeitpunkt, spätestens jede Minute. Das Feld wird nur neu gezeichnet, wenn
  sich die Anzeige ändert. Tageswechsel: es wird immer der heutige und der morgige Tag
  gelesen.
- Abruf (Diyanet) beim Start, nach jedem Gebet, nach Mitternacht und nach dem Speichern
  der Einstellungen (dann erzwungen) – im Hintergrund, Fehler landen nur im Protokoll.
- Ein Wächter läuft jede Minute: springt die Wanduhr (Ruhezustand, Uhr gestellt,
  Zeitzone), wird sofort neu gerechnet; das Feld wird wieder in die Taskleiste
  eingehängt, falls es herausgefallen ist.
- Explorer-Neustart: Der Wächter erkennt ihn an einem neuen `Shell_TrayWnd`-Handle
  (oder an einem fehlenden Tray-Symbol) und legt das Tray-Symbol neu an; das Feld
  hängt sich über `reattachIfNeeded` neu ein.
- Ezan und Töne nur, wenn Windows nicht auf „Nicht stören“/Fokus steht und nicht
  „Töne 1 Stunde aus“ gewählt ist; die Anzeige kommt immer. „Ezan stoppen“ im Banner
  oder im Tray-Menü beendet ihn.
- Farben folgen Windows hell/dunkel (`AppsUseLightTheme`): Popup und Banner bei jedem
  Öffnen, das Einstellungsfenster beim Programmstart.

## Bekannte Grenzen

- Windows-Update kann das Feld verschieben: Das Feld sitzt per `SetParent` in der
  Taskleiste, deren Aufbau Windows nicht zusagt. Nach einem großen Update kann es
  falsch sitzen oder fehlen; dann „Feld in der Taskleiste“ im Tray-Menü aus- und
  wieder einschalten, notfalls abschalten – die Zeiten bleiben im Tray-Symbol.
- Windows-Benachrichtigungen (Einstellung „Windows-Benachrichtigung“) kommen über das
  Tray-Symbol (`TrayIcon.displayMessage`), haben keine Stopp-Taste und Windows spielt
  dazu seinen eigenen Ton. Wer nur den Programmton will: in Windows unter
  Benachrichtigungen den Ton für „Java“/„Gebetszeiten“ abschalten, oder das Banner nehmen.
- „Nicht stören“ wird über eine undokumentierte Windows-Schnittstelle erkannt; falls
  der Ezan trotzdem spielt: Ezan-Datei leeren oder Lautstärke senken.
