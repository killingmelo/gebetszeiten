# Play-Store-Veröffentlichung — Checkliste

Alles Vorbereitete liegt in diesem Ordner (`playstore/`) bzw. in `~/Downloads`:

| Artefakt | Pfad |
|---|---|
| Phone-Bundle (AAB, signiert) | `Downloads/Gebetszeiten-0.1.13-phone.aab` |
| Wear-Bundle (AAB, signiert) | `Downloads/Gebetszeiten-0.1.10-wear.aab` |
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
**Datensicherheit (Data Safety):**
- „Erhebt oder teilt deine App Nutzerdaten?" → **Nein** (für alles).
- Verschlüsselung/Übertragung: entfällt (keine Datenerhebung).
- Begründung falls gefragt: App hat keine Internet-Berechtigung.

**Inhaltseinstufung (Content Rating, IARC):**
- Kategorie: „Referenz, Nachrichten oder Bildung" bzw. „Dienstprogramm".
- Alle Gewalt-/Sex-/Drogen-/Glücksspiel-Fragen: **Nein**.
- Interaktion/Standortweitergabe/Käufe: **Nein**. → Ergebnis: USK 0 / PEGI 3.

**Zielgruppe:** 18+ wählen (einfachster Weg; Apps mit Zielgruppe „Kinder"
haben Zusatzauflagen). Werbung: **keine**.

**Staatliche App / Finanz-App / Gesundheits-App:** jeweils Nein.

### 5. Releases hochladen
- **Produktion (bzw. zuerst geschlossener Test)** → `Gebetszeiten-0.1.12-phone.aab`.
- **Wear OS Form-Faktor:** unter „Releases" den Wear-Track aktivieren
  (Erweiterte Einstellungen → Formfaktoren → Wear OS) und
  `Gebetszeiten-0.1.9-wear.aab` hochladen; Wear-Screenshots (1:1) im
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

### 7. Nach dem Einreichen
- Prüfung dauert typischerweise 1–7 Tage (erste App eines neuen Kontos eher länger).
- Updates später: einfach neues AAB mit höherem versionCode hochladen —
  ich baue die Bundles jederzeit (`gradlew :app:bundleOfflineRelease`).
