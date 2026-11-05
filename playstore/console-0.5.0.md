# Play Console für 0.5.0 (25): was nur du eintragen kannst

Gebaut und geprüft am 05.11.2026 (Zweig `feat/app-design-review`, danach in `main`, Tag `v0.5.0`):

| | Telefon | Uhr |
|---|---|---|
| Version | 0.5.0 (versionCode 25) | 0.1.19 (versionCode 1019) |
| Bundle | `app/build/outputs/bundle/onlineRelease/app-online-release.aab` (21 MB) | `wear/build/outputs/bundle/onlineRelease/wear-online-release.aab` (9 MB) |
| Signatur | Upload-Schlüssel CN=Gebetszeiten (apksigner geprüft) | Upload-Schlüssel CN=Gebetszeiten |
| Was ist neu | `fastlane/metadata/android/<sprache>/changelogs/25.txt` | `…/changelogs/1019.txt` |
| Screenshots | alle fünf deutschen neu (`de-DE/images/phoneScreenshots/`) | unverändert |

Die Uhr muss diesmal mit hochgeladen werden: sie übernimmt die neue Minuten-Verschiebung vom Telefon. Ohne Uhr-Update zeigt sie weiter die amtlichen Zeiten ohne Verschiebung.

Am Emulator mit dem Release-Build geprüft: Update 0.4.0 → 0.5.0 (Ort, amtliche Zeiten bleiben, keine neue Ersteinrichtung, kein Absturz), Minuten-Verschiebung samt Kennzeichnung, „Verstehen“ mit Reitern, neuer Meldungskanal „Nächstes Gebet mit Restzeit“ (Wichtigkeit Standard, ohne Ton und Vibration). Die Uhr ist nur gebaut und getestet (JVM), nicht auf einem Gerät.

## 1. Berechtigungen und Datensicherheit

Keine neuen Berechtigungen, keine neuen Daten. Die Minuten-Verschiebung bleibt auf dem Gerät und geht nur an die eigene gekoppelte Uhr (wie schon der Zeiten-Sync). Das bestehende Datensicherheits-Formular gilt weiter, die Erklärung zum Vordergrunddienst (Aḏān) aus 0.4.0 ebenso.

## 2. Store-Eintrag

- Vollbeschreibung de/en ergänzt: „Heute“ mit Fortschritt und Wischen, „Verstehen“ mit Reitern, Minuten-Verschiebung.
- Deutsche Telefon-Screenshots neu: fünf Bilder, Aussage zur Funktion groß, echte amtliche Zeiten Nürnberg vom 05.11.2026.
- **Noch offen:** Der englische Eintrag hat nur drei alte Screenshots (`en-US/images/phoneScreenshots/`). Entweder die deutschen auch dort verwenden (die App ist deutsch) oder eine englische Fassung der Reihe erstellen.
- **Noch offen aus 0.4.0:** Im Early Access stehen noch die Mockups des Testers („Mock-up · Zeiten hier nur angenähert berechnet“). Spätestens vor der Produktion durch die neue Reihe ersetzen.

## 3. Ausrollen

1. Beide AABs in den **internen Test** hochladen (Telefon und Uhr).
2. **Pre-Launch-Bericht** abwarten und lesen.
3. Auf dem eigenen Telefon aus dem Test-Track installieren (nie den lokalen Release-Build: die Play-Signatur weicht ab). Prüfen: Restzeit-Zahl in der Statusleiste, wenn „Restzeit“ und „Dauerhafte Anzeige“ an sind; eine Verschiebung einstellen und auf der Uhr nachsehen.
4. Produktion gestaffelt (z. B. 20 %), ein bis zwei Tage Android Vitals beobachten, dann 100 %.

## 4. Nur auf echten Geräten prüfbar

- Uhr: Übernahme der Verschiebung (`/time-offsets`), Kachel mit „+5“.
- Statusleiste bei Herstellern mit eigener Oberfläche (OnePlus/OPPO, Samsung): erscheint die Zahl?
- Weiterhin aus 0.4.0: Aḏān bei gesperrtem Bildschirm (AudioHardening-Warnung unter Android 16).
