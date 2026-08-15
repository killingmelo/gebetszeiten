# Play-Store-Veröffentlichung — Checkliste

Die signierten Release-Bundles sind frisch gebaut (online-Flavor, R8/Minify,
lintVital + voller Lint grün, signiert mit `keystore/gebetszeiten.jks`,
Zertifikat gültig bis 2053). Neu bauen jederzeit mit:
`.\gradlew.bat :app:bundleOnlineRelease :wear:bundleRelease`.

Alles Vorbereitete liegt in diesem Ordner (`playstore/`):

| Artefakt | Pfad |
|---|---|
| Phone-Bundle (AAB, signiert, aktueller Stand laut git tag) | `app/build/outputs/bundle/onlineRelease/app-online-release.aab` |
| Wear-Bundle (AAB, signiert, aktueller Stand laut git tag) | `wear/build/outputs/bundle/release/wear-release.aab` |
| App-Icon 512×512 | `playstore/icon_512.png` |
| Feature-Graphic 1024×500 | `playstore/feature_1024x500.png` |
| Phone-Screenshots (9:16) | `playstore/screenshots/phone/` |
| Wear-Screenshots (1:1) | `playstore/screenshots/wear/` |
| Listing-Texte | `playstore/listing_de.md` |
| Datenschutzerklärung (URL fürs Pflichtfeld) | https://github.com/killingmelo/gebetszeiten/blob/main/PRIVACY.md |

## Schritte (nur du kannst sie machen)

### 1. Developer-Konto
- https://play.google.com/console → Konto anlegen (einmalig **25 USD**).
- ⚠️ **Wichtig für neue Privatkonten:** Google verlangt vor der Produktions-
  Freigabe einen **geschlossenen Test mit mindestens 12 Testern über 14 Tage**.
  Plane das ein: Closed-Track anlegen, 12 Freunde/Familie per E-Mail-Liste
  einladen, 14 Tage laufen lassen, dann Produktionszugang beantragen.

### 2. App anlegen
- „App erstellen" → Name **Gebetszeiten**, Standardsprache **Deutsch**,
  App (kein Spiel), **kostenlos**.

### 3. Store-Eintrag (Texte aus `listing_de.md` kopieren)
- App-Name, Kurz- und Vollbeschreibung einfügen.
- Icon (`icon_512.png`), Feature-Graphic (`feature_1024x500.png`),
  Phone-Screenshots hochladen.
- Kategorie **Lifestyle**, Kontakt-E-Mail, Datenschutz-URL (oben) eintragen.

### 4. Formulare (Antworten vorbereitet)
**Datensicherheit (Data Safety) — Stand Online-First:**
- „Erhebt oder teilt deine App Nutzerdaten?" → **Ja** (Datenerhebung im
  Play-Sinn = Übertragung vom Gerät).
- Datentyp: **Standort → ungefährer Standort** (manuell gewählter Ort als
  Diyanet-Standort-Kennung/Städtename an diyanet.gov.tr bzw. Fallback-Proxy;
  **seit 0.1.19 zusätzlich:** getippter Ortsname an geocoding-api.open-meteo.com,
  nur wenn die eingebaute Ortsliste keinen Treffer hat — gleicher Datentyp,
  gleiche Antworten, keine neue Kategorie nötig).
- Zweck: **App-Funktionen** (amtliche Gebetszeiten). Erhebung **optional**
  (in den Einstellungen abschaltbar). **Keine Weitergabe** zu Werbe-/
  Analysezwecken, **kein Verkauf**, Übertragung **verschlüsselt (HTTPS)**,
  Daten **nicht mit Nutzern verknüpft** (keine Konten/Kennungen),
  **Löschung entfällt** (es wird nichts serverseitig gespeichert, was der
  App zuordenbar wäre — Antwort: Daten werden nicht gespeichert).
- Abschnitt 5: Upload-Datei ist ab jetzt das **online**-Bundle.
- **Stand 0.1.20 — keine Änderung nötig.** Der neue weltweite Standortindex ist
  ein *gebündeltes* Asset, kein zusätzlicher Abruf: die Netz-Endpunkte sind
  unverändert dieselben drei (diyanet.gov.tr, der Fallback-Proxy,
  open-meteo für die Ortssuche). Datentyp, Zweck und alle Antworten oben
  bleiben also wie sie sind.

**Inhaltseinstufung (Content Rating, IARC):**
- Kategorie: „Referenz, Nachrichten oder Bildung" bzw. „Dienstprogramm".
- Alle Gewalt-/Sex-/Drogen-/Glücksspiel-Fragen: **Nein**.
- Interaktion/Standortweitergabe/Käufe: **Nein**. → Ergebnis: USK 0 / PEGI 3.

**Zielgruppe:** 18+ wählen (einfachster Weg; Apps mit Zielgruppe „Kinder"
haben Zusatzauflagen). Werbung: **keine**.

**Staatliche App / Finanz-App / Gesundheits-App:** jeweils Nein.

### 5. Releases hochladen

**Stand dieses Release: 0.1.20 (versionCode 21).** Nur das **Phone**-Bundle ist
neu. Das `wear/`-Modul wurde in diesem Umbau nicht angefasst — sein Bundle
(versionCode 1015) ist unverändert gültig und muss **nicht** erneut hochgeladen
werden. Neu bauen falls nötig: `.\gradlew.bat :app:bundleOnlineRelease`.

Vor dem Upload geprüft: 252 Unit-Tests grün, `lintOnlineRelease` **0 Fehler**
(nicht nur `lintVital` — der volle Lint hatte einen Compose-Fehler gefunden,
den der Release-Build allein durchgelassen hätte), Bundle signiert mit
`keystore/gebetszeiten.jks`, Standortindex im Bundle enthalten.

- **Produktion (bzw. zuerst geschlossener Test)** →
  `app/build/outputs/bundle/onlineRelease/app-online-release.aab` (aktueller Stand laut git tag).
- **Wear OS Form-Faktor:** unter „Releases" den Wear-Track aktivieren
  (Erweiterte Einstellungen → Formfaktoren → Wear OS) und
  `wear/build/outputs/bundle/release/wear-release.aab` (aktueller Stand laut git tag) hochladen; Wear-Screenshots (1:1) im
  Store-Eintrag unter Wear OS ergänzen. Wear-Apps durchlaufen eine eigene
  kurze Google-Prüfung.
- Beim ersten Upload fragt Play nach **Play App Signing** → zustimmen;
  unser vorhandener Schlüssel wird automatisch der **Upload-Key**.

### 6. Hinweis zur Signatur (wichtig zu wissen)
Play signiert die ausgelieferte App mit einem eigenen Google-Schlüssel.
Folge: Die **Play-Version und die GitHub-APK können sich nicht gegenseitig
aktualisieren** (unterschiedliche Signatur). Das ist normal — Nutzer sollten
sich für eine Quelle entscheiden. F-Droid (später) nutzt wieder unseren
eigenen Schlüssel und bleibt mit der GitHub-APK kompatibel.

### 6b. versionCode-Schema (wichtig seit 0.1.19)
Phone und Wear teilen sich die applicationId `de.gebetszeiten` — Play verlangt
**paketweit eindeutige versionCodes über alle Bundles**. Die Phone-App hatte
15..19 schon verbraucht, daher kollidierte Wear-versionCode 15.
- **Phone:** weiter fortlaufend (20, 21, …) — bleibt dauerhaft **unter 1000**.
- **Wear:** eigener **1000er-Block** (1015, 1016, …).

### 7. Nach dem Einreichen
- Prüfung dauert typischerweise 1–7 Tage (erste App eines neuen Kontos eher länger).
- Updates später: einfach neues AAB mit höherem versionCode hochladen —
  ich baue die Bundles jederzeit (`.\gradlew.bat :app:bundleOnlineRelease :wear:bundleRelease`).

### 8. Jaehrliches Zeiten-Update (amtliche Diyanet-Tabellen)
Die gebuendelten amtlichen Zeiten (shared-assets/official/) gelten je ein
Kalenderjahr. Sobald Diyanet das Folgejahr publiziert (erfahrungsgemaess Ende
Dezember, Jahresansicht auf namazvakitleri.diyanet.gov.tr pruefen):
1. `tools/diyanet-fetch/cache/` loeschen (sonst wird das alte Jahr re-emittiert),
2. `python tools/diyanet-fetch/fetch_diyanet.py` laufen lassen (~30-60 min),
3. Report pruefen (>=500 Standorte, < 4 MB), `git add shared-assets/official`,
4. Integritaetstest: `gradlew :app:testOfflineDebugUnitTest`,
5. App- UND Wear-Update mit erhoehtem versionCode veroeffentlichen (beide
   Module buendeln dieselben Assets).

Das Bundle bleibt dabei DE-Fallback und Quelle fuer die Wear-App; die
Phone-App selbst holt sich das neue Jahr online, sobald es amtlich verfuegbar ist.
