# Play Console für 0.4.0 (24): was nur du eintragen kannst

Gebaut und geprüft am 05.11.2026 (Zweig `feat/app-design-review`, Merge `46714f0` und Folge-Commits):

| | Telefon | Uhr |
|---|---|---|
| Version | 0.4.0 (versionCode 24) | unverändert 0.1.18 (1018), kein Upload nötig |
| Bundle | `app/build/outputs/bundle/onlineRelease/app-online-release.aab` (21 MB) | |
| Signatur | Upload-Schlüssel CN=Gebetszeiten (apksigner geprüft) | |
| Was ist neu | `fastlane/metadata/android/<sprache>/changelogs/24.txt` | |
| Screenshots | 01, 03, 05 neu (Heute bei Faǧr, Widgets, Heute bei ʿIšāʾ), 02 und 04 unverändert | |

Am Emulator mit Release-Builds geprüft: Update 0.3.0 → 0.4.0 (Ort, amtliche Zeiten, Widget bleiben; „transparent“ wird 45 %; keine neue Ersteinrichtung), Aḏān-Probe als Vordergrunddienst `mediaPlayback` mit Audiofokus, Stopp per Lautstärketaste, Widget hinzufügen mit eigener Einstellung, kein Absturz.

## 1. Neue Berechtigung: Vordergrunddienst „Medienwiedergabe“

Neu im Manifest: `FOREGROUND_SERVICE` und `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Dienst `de.gebetszeiten.adhan.AdhanService`). Play verlangt dafür unter **App-Inhalte › Berechtigungen für Dienste im Vordergrund** eine Erklärung.

- **Art:** Medienwiedergabe (Media playback)
- **Beschreibung (en, so verlangt):**
  > The app plays the call to prayer (Aḏān) at the start of a prayer time, only if the user has switched this on (it is off by default). Playback runs as a media playback foreground service with a visible notification and a Stop button, for the length of the recording (about 4 minutes), and ends by itself. The user can stop it at any time with the notification button or a volume key.
- **Auswirkung, wenn verweigert/aufgeschoben:** Der Aḏān würde bei gesperrtem Bildschirm abgebrochen oder gar nicht abgespielt.
- **Video:** Play fragt meist nach einem Link (YouTube „nicht gelistet“ reicht). Inhalt: Einstellungen › Aḏān einschalten, „Probe hören“ antippen, Meldung mit „Stoppen“ zeigen, Bildschirm aus, Wiedergabe läuft weiter, Stoppen.

## 2. Exakte Wecker (`USE_EXACT_ALARM`), bekannt seit 0.2.0

Unverändert, aber bei jedem Update prüfbar. Falls Play nachfragt:
> Prayer reminders must fire at the exact official prayer time; a delay of even a few minutes means the reminder arrives after the prayer window has started or, for Faǧr, after it has ended. The app schedules one exact alarm per prayer transition and does not poll.

## 3. Datensicherheit

Keine neuen Daten. Die Aḏān-Datei, die man selbst wählt, bleibt auf dem Gerät; die App liest sie nur über den System-Dateidialog. Das bestehende Formular gilt weiter.

## 4. Store-Eintrag

- Vollbeschreibung de/en ergänzt (Aḏān, Widgets, Kompass, Akku-Satz zum Wiedergabedienst). Die englische Fassung ist neu übersetzt und sagt, dass die App selbst deutsch ist.
- Phone-Screenshots neu: `fastlane/metadata/android/de-DE/images/phoneScreenshots/`.
- Bekannte Unschärfe: die Uhr im Screenshot 03 stammt vom 30.10. (Maġrib 17:06), das Telefon vom 05.11. (16:56).

## 5. Rechte an der mitgelieferten Aufnahme

`res/raw/adhan_almakki.ogg` (ʿAbd al-Karīm al-Makkī, YouTube 7FG4q8xyKvA). Nutzung mit Quellenangabe hat der Herausgeber am 05.11.2026 entschieden; die Quelle steht unter Einstellungen › Über. Eine schriftliche Erlaubnis liegt im Repository nicht vor. Bei einer Beschwerde: Aufnahme durch eine der frei lizenzierten ersetzen (`adhan-freie-aufnahmen.md` der Aḏān-Sitzung).

## 6. Ausrollen

1. AAB in den **internen Test** (läuft bereits) hochladen.
2. **Pre-Launch-Bericht** abwarten und lesen (Abstürze, Darstellung, Bedienbarkeit).
3. Auf dem eigenen Telefon aus dem Test-Track installieren und eine Nacht laufen lassen: Faǧr-Erinnerung und Aḏān bei gesperrtem Bildschirm, Widget am Morgen.
4. Produktion **gestaffelt** (z. B. 20 %), ein bis zwei Tage Android Vitals beobachten, dann 100 %.

## 7. Offen und nur auf echten Geräten prüfbar

- Android 16 meldet für den Aḏān-Dienst bei automatischem Start aus dem Hintergrund „AudioHardening … would be ignored / would be muted“ (Warnung, noch nicht durchgesetzt). Auf dem Telefon prüfen, ob er bei gesperrtem Bildschirm hörbar ist.
- Hersteller-Akkusparer (OnePlus/OPPO): Erinnerungen und Aḏān nach einer Nacht.
- Lautstärketaste bei dunklem Bildschirm (auf dem Emulator ohne Wirkung).
