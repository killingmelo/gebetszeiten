# Neuer Play-Store-Auftritt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Neue Store-Texte (de-DE), ein neues App-Icon (Gerät und Store aus einer Quelle), ein Feature-Bild und fünf Telefon-Screenshots „Ein Tag in fünf Bildern“.

**Architecture:** Die Icon-Form liegt einmal als Polygon-Geometrie in `tools/app-icon/geometry.json`; ein Generator schreibt daraus die Vektoren des adaptiven Symbols und rendert das Store-Icon. Ein Panorama-Modul in `playstore/` liefert den Himmel für Feature-Bild und Screenshots. Kotlin-Unit-Tests prüfen Store-Texte und Icon-Dateien im Arbeitsbaum, wie es das Projekt schon für die Countdown-Symbole tut.

**Tech Stack:** Python 3 mit Pillow und OpenCV (`cv2` 5.0, vorhanden), Android VectorDrawable, Kotlin/JUnit-Unit-Tests über Gradle, Emulator + adb.

**Spec:** `docs/superpowers/specs/2026-10-30-playstore-auftritt-design.md`

## Global Constraints

- Sichtbare Texte ohne U+2014 und U+2013, auch nicht in Zeitspannen („11:45 bis 12:05“); keine KI-Floskeln.
- DMG-Umschrift mit ʿ = U+02BF und ʾ = U+02BE: Faǧr, Šurūq, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, Ḍuḥā, Makrūh, Zawāl, Ǧamāʿa, Auwābīn, Tahaǧǧud (Schreibweisen wie in `app/src/main/res/values/strings.xml`). Einzige Ausnahme: der Suchwörter-Satz.
- Titel exakt `Gebetszeiten Islam`; Kurzbeschreibung exakt `Amtliche Diyanet-Zeiten, gegengeprüft. Ohne Werbung, ohne Konto, akkusparend.`; Längen ≤ 30 / ≤ 80 / ≤ 4000.
- Farben: Waldgrün `#224228`, Creme `#F7F1E8`, Gold `#DDAA61`, Ocker `#BC924C`, Ton `#6B443D`, Nacht `#000000`.
- Schriften: `app/src/main/res/font/serif_light.ttf` (Überschriften), `sans_regular.ttf` (Zeilen).
- Nur echte amtliche Diyanet-Zeiten auf Bildern; keine Codeänderung an der App, um Screenshots herzustellen.
- `app_name` bleibt `Gebetszeiten`; Benachrichtigungs-Sichel, Wear-Symbol, Desktop-Symbol und `fastlane/.../en-US/` bleiben unverändert.
- Quelldateien nie über PowerShell Get-Content/Set-Content bearbeiten. Neue Bild-Erzeugungen (Generator) oder neue Werkzeuge nur nach Rückfrage beim Nutzer.
- Unit-Tests `:app:testOfflineDebugUnitTest` und `:app:testOnlineDebugUnitTest` bleiben grün (Gradle über PowerShell: `.\gradlew.bat …`).

## Review Focus

1. Themen-Icons (Android 13+): Die `monochrome`-Ebene wird nur über Alpha eingefärbt; das Motiv muss ohne Creme-Grund als Silhouette lesbar bleiben. Test: Task 3, Emulator-Schritt mit aktivierten Themen-Icons.
2. Kleine Größen: 48 px und 32 px dürfen nicht zu Rauschen werden (Pinselkanten, dünne Zehen). Test: Task 2, Freigabe-Vorschau mit 32 px.
3. Nahtstellen: Die fünf Screenshots müssen nebeneinander ein Panorama ohne Sprung ergeben. Test: Task 5, Prüfung der Randspalten.
4. Glyphen: ʿ, ǧ, Ẓ, ġ, Š, ʾ müssen in den Bildüberschriften aus der Serife gerendert werden, nicht als Ersatzzeichen. Test: Task 5, Glyphenprüfung.
5. Uhrzeit auf den Aufnahmen: Emulator steht auf UTC; ohne Zeitzone Europe/Berlin zeigt „Heute“ das falsche laufende Gebet. Test: Task 5, Abgleich Anzeige gegen Tabelle.

---

### Task 1: Store-Texte (de-DE)

**Files:**
- Create: `app/src/test/kotlin/de/gebetszeiten/store/StoreListingTextTest.kt`
- Modify: `fastlane/metadata/android/de-DE/title.txt`, `short_description.txt`, `full_description.txt`
- Modify: `app/build.gradle.kts` (Test-Eingaben, Block `tasks.withType<Test>().configureEach`)
- Modify: `playstore/listing_de.md` (Abschnitt „App-Name“: `Gebetszeiten Islam`, Alternativen streichen)

**Interfaces:**
- Produces: nichts für spätere Tasks.

- [ ] **Step 1: Write the failing test** `StoreListingTextTest` liest die drei Dateien aus `../fastlane/metadata/android/de-DE/` (UTF-8, Zeilenende normiert, Rand-Leerraum entfernt) und prüft:

```kotlin
@Test fun titel() = assertEquals("Gebetszeiten Islam", title)
@Test fun kurzbeschreibung() = assertEquals(
    "Amtliche Diyanet-Zeiten, gegengeprüft. Ohne Werbung, ohne Konto, akkusparend.", short)
@Test fun laengen() { assertTrue(title.length <= 30); assertTrue(short.length <= 80); assertTrue(full.length <= 4000) }
@Test fun keineStriche() = listOf(title, short, full).forEach { assertFalse(it.contains('—') || it.contains('–')) }
@Test fun suchwoerterGenauEinmal() {
    assertEquals(1, Regex("Namaz Vakitleri").findAll(full).count())
    assertEquals(1, Regex("\\bSalah").findAll(full).count())
}
@Test fun nichtVonDiyanet() = assertTrue(full.contains("nicht von Diyanet herausgegeben"))
@Test fun keineFremdUmschrift() {
    val verboten = listOf("Fajr", "Isha", "Dhuhr", "Maghrib", "Duha", "Makruh", "Cemaat", "Awwabin", "Tahajjud", "Karaha")
    verboten.forEach { assertFalse(it, Regex("\\b$it\\b").containsMatchIn(full)) }
}
```

- [ ] **Step 2: Run, expect FAIL** (Titel ist heute `Gebetszeiten`, Text enthält „—“ und „Fajr“)
Run: `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "de.gebetszeiten.store.StoreListingTextTest"`

- [ ] **Step 3: Texte schreiben.** `title.txt` und `short_description.txt` exakt wie oben. `full_description.txt` neu, Reihenfolge laut Spec (Quelle; ohne Werbung, Konto, Tracking, Analytics, Standort-Berechtigung; Akku inkl. Satz zur Ausnahme von der Akku-Optimierung und zur Restzeit-Anzeige; Funktionen in DMG; Wear OS; Suchwörter-Satz; „Die App wird nicht von Diyanet herausgegeben.“). Fakten nur aus dem bisherigen Text und dem Code (über 940 Orte mit eingebauten Tabellen, Notausgang mit Faǧr 18°, ʿIšāʾ 17°, Ǧamāʿa mit Vorlauf usw.), nichts dazuerfinden. Keine Bindestrich-Aufzählungszeichen „–“; Aufzählungen mit `*`.

- [ ] **Step 4: Gradle-Eingabe ergänzen**, damit der Test nicht UP-TO-DATE bleibt, wenn nur ein Text sich ändert: `inputs.dir(rootProject.file("fastlane/metadata/android/de-DE")).withPropertyName("storeTexte").withPathSensitivity(PathSensitivity.RELATIVE)`; im Kommentarblock darüber `StoreListingTextTest -> fastlane/metadata/android/de-DE` ergänzen.

- [ ] **Step 5: Run, expect PASS** (gleicher Befehl), dann beide Flavors: `.\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest`

- [ ] **Step 6: Den langen Text dem Nutzer zum Lesen vorlegen** (Spec: vor dem Hochladen). Änderungswünsche einarbeiten, Test erneut laufen lassen.

- [ ] **Step 7: Commit** `feat(store): neue Store-Texte de-DE` (nur wenn der Nutzer Commits freigegeben hat).

---

### Task 2: Icon-Geometrie und Freigabe

**Files:**
- Create: `tools/app-icon/trace_vorlage.py` (einmalig: Vorlage → Rohkonturen)
- Create: `tools/app-icon/geometry.json`
- Create: `tools/app-icon/build_app_icon.py` (in diesem Task nur Laden, Prüfen, Rendern, Vorschau)
- Create: `tools/app-icon/README.md` (wozu, wie neu erzeugen, warum Polygone)

**Interfaces:**
- Produces:
  - `geometry.json`: `{"viewport": 108, "shapes": [{"name": str, "points": [[x, y], ...]}]}` in Koordinaten 0 bis 108; Füllfarbe für alle Formen Waldgrün.
  - `load_geometry(path: Path = GEOMETRY) -> list[Shape]` mit `Shape = tuple[str, list[tuple[float, float]]]`
  - `render_png(size: int, background: str | None = "#F7F1E8", fill: str = "#224228") -> PIL.Image.Image` (4-fach überabgetastet, dann LANCZOS)
  - Konstanten `CREAM = "#F7F1E8"`, `FOREST = "#224228"`, `SAFE_RADIUS = 33.0` (66-dp-Schutzzone um 54,54)

- [ ] **Step 1: `trace_vorlage.py <png> <out.json>`** liest `.superpowers/brainstorm/1673-1793359476/icon-vorlage-mitte2.png`, schwellt (dunkel = Tinte), `cv2.findContours` + `cv2.approxPolyDP`, wirft den Bogen weg (Komponenten rechts oberhalb der Figur) und behält Figur, Füße und Teppich. Skaliert so, dass die Figur samt Bogen später in die Schutzzone passt.

- [ ] **Step 2: Bogen geometrisch statt nachgezogen.** In `build_app_icon.py` `horseshoe_arch(cx: float, top: float, width: float, height: float, pinch: float, stroke: float) -> list[tuple[float, float]]`: Pinselzug als Polygon, oben **rund** (Kreisbogen über mehr als einen Halbkreis), unten um `pinch` eingezogen wie in Córdoba, Pfosten senkrecht, Strichstärke an den Enden leicht verjüngt. Lage: dicht hinter dem Kopf der Figur, oben rechts (Figur blickt nach Nordost).

- [ ] **Step 3: Harte Prüfungen beim Laden/Bauen** (Skript bricht ab): jeder Punkt innerhalb `SAFE_RADIUS` um (54, 54); jede Form mindestens 3 Punkte und Fläche > 0; Summe aller Punkte ≤ 600 (VectorDrawable-Pfade bleiben handhabbar).

- [ ] **Step 4: `python tools/app-icon/build_app_icon.py --preview`** schreibt `tools/app-icon/preview.png`: das Icon in 512, 192, 48, 32 px auf Creme in abgerundeter Kachel und als Kreis, daneben die einfarbige Silhouette (Weiß auf Dunkelgrau) in 48 px.

- [ ] **Step 5: Freigabe durch den Nutzer.** Vorschau zeigen (Browser-Begleiter oder Datei). Haltung (Hüfte höchster Punkt, Stirn am Boden, Blick nach Nordost), Schlankheit, Bogenform, Größe in der Kachel. Nachbessern in `geometry.json` bzw. den Bogen-Parametern, bis der Nutzer zustimmt. Keine weiteren Tasks vorher.

- [ ] **Step 6: Commit** `feat(icon): Geometrie des neuen App-Symbols` (nach Freigabe).

---

### Task 3: Startbildschirm-Symbol aus der Geometrie

**Files:**
- Modify: `tools/app-icon/build_app_icon.py` (Schreiben + `--check`)
- Create: `tools/app-icon/icon.sha256`
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml` (erzeugt)
- Modify: `tools/notification-icons/build_icons.py` (Startbildschirm-Teil entfernen: `LAUNCHER_FILE`, `LAUNCHER_RADIUS`, `launcher_xml`, `check_crescent(LAUNCHER_RADIUS)`, Docstring/Kommentare), `tools/notification-icons/icons.sha256` (neu erzeugt), `tools/notification-icons/README.md`
- Modify: `app/src/test/kotlin/de/gebetszeiten/notify/CountdownIconAssetsTest.kt` (erwartete Menge ohne `ic_launcher_foreground.xml`, Kommentar anpassen)
- Create: `app/src/test/kotlin/de/gebetszeiten/notify/AppIconAssetsTest.kt`
- Modify: `app/build.gradle.kts` (Eingabe `tools/app-icon/icon.sha256`, Kommentarblock)

**Interfaces:**
- Consumes: `load_geometry`, Konstanten aus Task 2.
- Produces: `foreground_xml() -> str`, `background_xml() -> str`, `build() -> dict[str, str]` (Dateiname → Inhalt), `digest`/`manifest_text` wie in `build_icons.py` (CRLF-normiert hashen).

- [ ] **Step 1: Write the failing test** `AppIconAssetsTest`:

```kotlin
@Test fun manifestDeckt() = assertEquals(setOf("ic_launcher_foreground.xml", "ic_launcher_background.xml"), erwartet.keys)
@Test fun hashesStimmen() = assertTrue(erwartet.filter { (d, h) -> sha256(File(drawables, d)) != h }.isEmpty())
@Test fun grundIstCreme() = assertTrue(File(drawables, "ic_launcher_background.xml").readText().contains("#FFF7F1E8"))
@Test fun vordergrundOhneTint() = assertFalse(File(drawables, "ic_launcher_foreground.xml").readText().contains("android:tint"))
@Test fun adaptivVerdrahtet() = listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach {
    val x = File("src/main/res/mipmap-anydpi-v26/$it").readText()
    assertTrue(x.contains("@drawable/ic_launcher_background") && x.contains("<monochrome android:drawable=\"@drawable/ic_launcher_foreground\""))
}
```
`sha256` und Manifest-Lesen wie in `CountdownIconAssetsTest` (Zeilenende normiert).

- [ ] **Step 2: Run, expect FAIL** (Manifest fehlt): `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "de.gebetszeiten.notify.*"`

- [ ] **Step 3: Generator vervollständigen.** `foreground_xml()`: 108 × 108, je Form ein `<path android:fillColor="#FF224228" android:pathData="M… L… Z"/>`, kein `android:tint`, Kopfkommentar „Erzeugt von tools/app-icon/build_app_icon.py; nicht von Hand aendern.“ (kein `--` im Kommentar). `background_xml()`: Rechteck `#FFF7F1E8`. `python tools/app-icon/build_app_icon.py` schreibt beide Drawables + `icon.sha256`; `--check` prüft nur. Zweimal laufen ergibt byte-gleiche Dateien.

- [ ] **Step 4: Sichel-Generator entschlacken**, dann `python tools/notification-icons/build_icons.py` (neues Manifest ohne Launcher-Zeile) und `CountdownIconAssetsTest` anpassen. `ic_notification.xml` bleibt byte-gleich.

- [ ] **Step 5: Gradle-Eingabe** `tools/app-icon/icon.sha256` ergänzen (Property `appIconManifest`).

- [ ] **Step 6: Run, expect PASS**: `.\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest`

- [ ] **Step 7: Emulator-Prüfung.** `assembleOnlineDebug`, installieren, Startbildschirm-Screenshot; dann Hintergrund & Stil → „Designfarben/Themen-Symbole“ an und erneut. Erwartet: Motiv auf Creme erkennbar; im Themen-Icon als einfarbige Silhouette lesbar (Review Focus 1). Bilder dem Nutzer zeigen.

- [ ] **Step 8: Commit** `feat(icon): Startbildschirm-Symbol aus tools/app-icon`.

---

### Task 4: Panorama, Store-Icon und Feature-Bild

**Files:**
- Create: `playstore/panorama.py`
- Modify: `playstore/make_assets.py` (komplett neu; keine absoluten Pfade, keine Segoe-Schrift, kein Import aus `build_icons`)
- Modify: `playstore/icon_512.png`, `playstore/feature_1024x500.png` (erzeugt)
- Modify: `playstore/listing_de.md` (Grafik-Abschnitt: woher Icon und Feature-Bild kommen)

**Interfaces:**
- Consumes: `render_png(size, ...)` aus `tools/app-icon/build_app_icon.py`.
- Produces (für Task 5):
  - `STOPS: list[tuple[float, str]]` Farbverlauf links → rechts: `(0.00, "#C8CBC6"), (0.14, "#ECE6DA"), (0.30, "#F7F1E8"), (0.48, "#F4E6C9"), (0.62, "#E2BF86"), (0.76, "#B9775A"), (0.88, "#3A2A2C"), (1.00, "#000000")` (wie Mockup `screenshots-tag.html`).
  - `render_panorama(width: int, height: int, horizon: float = 0.74, sun: bool = True) -> PIL.Image.Image`: horizontaler Verlauf, Horizontlinie (Tinte 18 % Deckkraft), gestrichelte Sonnenbahn als kubische Bézierkurve über das erste bis vierte Fünftel, Sonnenpunkte bei Faǧr (Schimmer unter dem Horizont), Ẓuhr (Scheitel), ʿAṣr (Abstieg), Maġrib (auf dem Horizont), Sterne im letzten Fünftel. Alle Lagen relativ zu `width`/`height`, damit 1024 × 500 und 5400 × 1920 dasselbe Bild zeigen.
  - `font(role: str, px: int) -> ImageFont.FreeTypeFont` mit `role in {"serif", "sans"}` aus `app/src/main/res/font/`.

- [ ] **Step 1: Selbstprüfung zuerst** in `panorama.py` unter `if __name__ == "__main__"`: `render_panorama(5400, 1920)` hat Größe 5400 × 1920; Pixel ganz links ≈ `#C8CBC6`, ganz rechts `#000000`; Spalte x und x+1 an jeder Fünftel-Grenze unterscheiden sich um höchstens 3 Farbstufen je Kanal (keine Naht). Run: `python playstore/panorama.py`, erwartet zunächst Fehler (Funktion fehlt), nach Step 2 „ok“.

- [ ] **Step 2: `render_panorama` und `font` implementieren**; Run wie oben → „ok“.

- [ ] **Step 3: `make_assets.py`**: `icon_512.png` = `render_png(512)` (vollflächig Creme, Play maskiert selbst). `feature_1024x500.png` = `render_panorama(1024, 500, sun=True)`, links das Icon 300 px in abgerundeter Kachel, rechts „Gebetszeiten Islam“ in `font("serif", 72)` Tinte `#1C1B1A`. Prüfungen: Größen exakt, RGB ohne Alpha. Run: `python playstore/make_assets.py` → „ok“.

- [ ] **Step 4: Ansehen** beider Bilder und dem Nutzer zeigen.

- [ ] **Step 5: Commit** `feat(store): Store-Icon und Feature-Bild aus Icon-Geometrie und Panorama`.

---

### Task 5: Telefon- und Uhr-Screenshots

**Files:**
- Modify: `playstore/make_screenshots.py` (Telefon: Zusammensetzen statt Beschneiden; Uhr unverändert)
- Modify: `fastlane/metadata/android/de-DE/images/phoneScreenshots/*`, `wearScreenshots/*` (erzeugt; alte Dateien entfernt)
- Modify: `playstore/CHECKLISTE.md` (Stand, neue Abläufe)

**Interfaces:**
- Consumes: `render_panorama`, `font` aus `playstore/panorama.py`.
- Produces: `SHOTS: list[tuple[str, str, str]]` = (Rohdatei, Überschrift, Zeile):

| Rohdatei (`build/roh/phoneScreenshots/`) | Überschrift | Zeile |
|---|---|---|
| `01_fajr.png` | Faǧr | Welches Gebet gerade gilt und wie lange noch |
| `02_zuhr.png` | Ẓuhr | Warum die Zeit dann beginnt |
| `03_asr.png` (+ `03_asr_uhr.png`) | ʿAṣr | Auf dem Startbildschirm und am Handgelenk |
| `04_maghrib.png` | Maġrib | Amtliche Diyanet-Zeiten, gegengeprüft |
| `05_isha.png` | ʿIšāʾ | Keine Werbung, kein Konto, akkusparend |

  `compose(raw: Image, panel: Image, title: str, line: str, inset: Image | None = None) -> Image` liefert 1080 × 1920.

- [ ] **Step 1: Aufnehmen.** Emulator `Medium_Phone_API_36.1`, `assembleOnlineDebug` installieren, Ort Nürnberg (eingebaute Tabellen). Zeitzone Europe/Berlin und manuelle Uhrzeit über die Systemeinstellung Datum/Uhrzeit setzen (kein `adb root` auf Play-Images; keine App-Änderung). Je eine Aufnahme kurz nach Faǧr („Heute“), mittags („Zeiten verstehen“, Abschnitt Ẓuhr), nachmittags (Startbildschirm mit Widget), abends („Monat“ mit Quellenzeile), nachts mit Nachtmodus/AMOLED („Heute“). Uhr-Kachel vom AVD `WearGebetszeiten` als `03_asr_uhr.png`, dazu neue Uhr-Bilder nach `build/roh/wearScreenshots/`. In Git Bash `MSYS_NO_PATHCONV=1` vor `adb shell screencap`.

- [ ] **Step 2: Zeiten abgleichen** (Review Focus 5): je Aufnahme die angezeigten Zeiten gegen die eingebaute Tabelle für Nürnberg an diesem Datum; laufendes Gebet passt zur eingestellten Uhrzeit. Abweichung → neu aufnehmen.

- [ ] **Step 3: Prüfungen zuerst** in `make_screenshots.py` (harte `assert`): jedes Ergebnis 1080 × 1920; Verhältnis ≤ 2:1; Glyphenprüfung: für jedes Zeichen der Überschriften liefert `font("serif", …).getmask(ch).getbbox()` eine Fläche und ist nicht gleich der Maske von U+FFFD (Review Focus 4); keine U+2013/U+2014 in Überschriften und Zeilen; Nahtprüfung: Randspalte rechts von Bild n und links von Bild n+1 stammen aus benachbarten Panorama-Spalten (Abweichung ≤ 3 je Kanal, Review Focus 3).

- [ ] **Step 4: `compose` implementieren**: Panorama 5400 × 1920 in fünf Streifen à 1080; Überschrift `font("serif", 120)` zentriert oben, Zeile `font("sans", 40)` darunter (Tinte `#1C1B1A`, im fünften Bild `#F7F1E8` / `#D8D2C8`); Aufnahme auf ca. 820 px Breite skaliert in schlichtem Rahmen (Radius wie Gerät, 14 px Rand `#1C1B1A`, im fünften `#2A2A2D`), unten angeschnitten; bei Bild 3 die Uhr-Kachel als runder Einsatz rechts unten. Ausgabe nach `fastlane/.../phoneScreenshots/` mit denselben Dateinamen; Zielordner wird vorher geleert (wie bisher).

- [ ] **Step 5: Run** `python playstore/make_screenshots.py`, erwartet fünf Telefon- und die Uhr-Dateien ohne Assert-Fehler. Die fünf nebeneinander als ein Bild zusammenkleben und ansehen.

- [ ] **Step 6: Dem Nutzer zeigen**; Nachbesserungen an Abstand, Größen oder Aufnahmen.

- [ ] **Step 7: `CHECKLISTE.md`** auf den neuen Stand (Titel, Icon-Quelle `tools/app-icon/`, Panorama, Aufnahmeablauf mit Zeitzone). Dann alle Unit-Tests: `.\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest`.

- [ ] **Step 8: Commit** `feat(store): Screenshots „Ein Tag in fünf Bildern“`.
