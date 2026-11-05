# Uploads über die Play-API: einmalige Einrichtung

Danach lädt `playstore/play_upload.py` Telefon- und Uhr-Bundle samt „Was ist neu“ hoch, auf Wunsch auch Store-Texte und Bilder aus `fastlane/metadata/android/`. Releases landen immer als **Entwurf**; freigeben, in die Produktion bringen und staffeln bleibt in der Play Console.

## 1. Google Cloud (einmal)

1. <https://console.cloud.google.com/> öffnen, ein Projekt anlegen (z. B. „gebetszeiten-play“) oder ein vorhandenes wählen.
2. „APIs und Dienste“ > „Bibliothek“ > **Google Play Android Developer API** > Aktivieren.
3. „IAM und Verwaltung“ > „Dienstkonten“ > Dienstkonto erstellen (Name z. B. „play-upload“). Rollen im Projekt sind nicht nötig.
4. Beim Dienstkonto: „Schlüssel“ > „Schlüssel hinzufügen“ > JSON. Die Datei speichern als
   `keystore/play-dienstkonto.json` im Projektordner. `keystore/` steht in `.gitignore`, der Schlüssel kommt nie ins Repo. Nicht weitergeben, nicht in den Chat kopieren.

## 2. Play Console (einmal)

1. „Nutzer und Berechtigungen“ > „Neue Nutzer einladen“, die E-Mail-Adresse des Dienstkontos eintragen (`…@….iam.gserviceaccount.com`).
2. Unter „App-Berechtigungen“ nur **Gebetszeiten** hinzufügen, mit:
   - Releases für Test-Tracks verwalten (reicht für den internen Test),
   - Store-Eintrag bearbeiten (nur nötig für `--eintrag` und `--bilder`).
   Kontoweite Rechte und Finanzdaten nicht vergeben.
3. Einladung senden. Bis die Rechte greifen, kann es einige Minuten dauern.

## 3. Rechner (einmal)

```bash
python -m pip install -r playstore/requirements-upload.txt
```

## 4. Je Release

```bash
python playstore/play_upload.py --trocken
```
Nur lokal: Bundles vorhanden und neuer als die Gradle-Datei, Versionshinweise je Sprache da, Längen und Striche geprüft.

```bash
python playstore/play_upload.py --pruefen
```
Zugang testen, zeigt alle Tracks mit ihren Releases, ändert nichts.

```bash
python playstore/play_upload.py
```
Lädt hoch und lässt Google die Bearbeitung prüfen, verwirft sie dann. Findet Fehler, ohne etwas zu veröffentlichen.

```bash
python playstore/play_upload.py --ja
```
Lädt hoch und übernimmt: Telefon in `internal`, Uhr in `wear:internal`, beide als Entwurf. Danach in der Console unter „Test und Release“ > „Interner Test“ (Telefon und Wear OS) prüfen und freigeben.

Zusätze: `--eintrag` (Titel, Kurz- und Vollbeschreibung), `--bilder` (ersetzt alle Screenshots je Sprache durch die aus `fastlane/…/images/`), `--nur-telefon`, `--nur-uhr`, `--track`, `--uhr-track`. Verlangt Google beim Übernehmen `changesNotSentForReview`, mit `--nicht-zur-pruefung` wiederholen.

## Nicht über die API

Datensicherheit, Inhaltseinstufung, Zielgruppe, Erklärung zum Vordergrunddienst, Freigabe und Staffelung der Produktion: weiter von Hand in der Console.
