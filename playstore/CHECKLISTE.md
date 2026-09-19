# Play-Store-Veröffentlichung — Checkliste

Die signierten Release-Bundles sind frisch gebaut (online-Flavor, R8/Minify,
lintVital + voller Lint grün, signiert mit `keystore/gebetszeiten.jks`,
Zertifikat gültig bis 2053). Neu bauen jederzeit mit:
`.\gradlew.bat :app:bundleOnlineRelease :wear:bundleOnlineRelease`.

Alles Vorbereitete liegt in diesem Ordner (`playstore/`):

| Artefakt | Pfad |
|---|---|
| Phone-Bundle (AAB, signiert, aktueller Stand laut git tag) | `app/build/outputs/bundle/onlineRelease/app-online-release.aab` |
| Wear-Bundle (AAB, signiert, aktueller Stand laut git tag) | `wear/build/outputs/bundle/onlineRelease/wear-online-release.aab` |
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
  (in den Einstellungen abschaltbar) — **das gilt nur fürs Telefon**, siehe
  Nachtrag unten: die Wear-App hat seit dem Umbau "amtliche Zeiten oder
  nichts" keinen eigenen Schalter mehr dafür. **Keine Weitergabe** zu Werbe-/
  Analysezwecken, **kein Verkauf**, Übertragung **verschlüsselt (HTTPS)**,
  Daten **nicht mit Nutzern verknüpft** (keine Konten/Kennungen),
  **Löschung entfällt** (es wird nichts serverseitig gespeichert, was der
  App zuordenbar wäre — Antwort: Daten werden nicht gespeichert).
- Abschnitt 5: Upload-Datei ist ab jetzt das **online**-Bundle.
- **Stand 0.1.20 — Datentyp/Endpunkte unverändert, aber NICHT mehr "keine
  Änderung nötig".** Der neue weltweite Standortindex ist weiterhin ein
  *gebündeltes* Asset, kein zusätzlicher Abruf, und die Netz-Endpunkte sind
  unverändert dieselben drei (diyanet.gov.tr, der Fallback-Proxy, open-meteo
  für die Ortssuche) — das stimmt nach wie vor. Neu ist der **Formfaktor**:
  seit dem Umbau "amtliche Zeiten oder nichts" ruft die **Wear-App diese
  Endpunkte selbst ab** (`WearRefresh.kt`, online-Flavor), statt nur die vom
  Telefon gesyncten Zeiten anzuzeigen — und sie hat dafür **keinen eigenen
  Online-Schalter**: weder der "Online-Abgleich"-Schalter des Telefons (wirkt
  nur dort) noch der Notausgang ("Berechnung als Notausgang", der nur
  bestimmt, was OHNE amtliche Zeiten angezeigt wird, nicht ob abgerufen wird)
  stellen diesen Abruf ab. Die Antwort **"Erhebung optional (abschaltbar)"**
  gilt deshalb NICHT mehr für die ganze App — für das Telefon weiterhin ja,
  für die Wear-App nein. Im Formular entsprechend eintragen (Erhebung nicht
  durchgängig als optional kennzeichnen bzw. den Wear-Formfaktor gesondert
  vermerken, je nachdem, welche Granularität die Play-Console-Maske beim
  Ausfüllen tatsächlich anbietet).

**Inhaltseinstufung (Content Rating, IARC):**
- Kategorie: „Referenz, Nachrichten oder Bildung" bzw. „Dienstprogramm".
- Alle Gewalt-/Sex-/Drogen-/Glücksspiel-Fragen: **Nein**.
- Interaktion/Standortweitergabe/Käufe: **Nein**. → Ergebnis: USK 0 / PEGI 3.

**Zielgruppe:** 18+ wählen (einfachster Weg; Apps mit Zielgruppe „Kinder"
haben Zusatzauflagen). Werbung: **keine**.

**Staatliche App / Finanz-App / Gesundheits-App:** jeweils Nein.

### 5. Releases hochladen

**Diese Aussage ist veraltet und stimmt seit dem Umbau "amtliche Zeiten oder
nichts" nicht mehr:** das `wear/`-Modul hat inzwischen dieselbe Quellen-Regel
wie das Telefon bekommen (u. a. Aufgaben 16/17) und **eigene Flavors**
(`offline`/`online`, seit `eccde09`). Das Wear-Bundle ist bei diesem Release
also **ebenfalls neu** und muss erneut hochgeladen werden — "wear unverändert,
kein erneuter Upload nötig" gilt nicht mehr generell, sondern nur für Releases,
die das `wear/`-Modul tatsächlich nicht berühren; das ist von Release zu
Release neu zu prüfen (z. B. per `git log -- wear/` seit dem letzten
hochgeladenen Wear-versionCode).

Neu bauen: `.\gradlew.bat :app:bundleOnlineRelease :wear:bundleOnlineRelease`.

Vor dem Upload geprüft: Testlauf über alle Module und beide Flavors beider
Anwendungen grün, `lintOnlineRelease` **0 Fehler** (nicht nur `lintVital` —
der volle Lint hatte einen Compose-Fehler gefunden, den der Release-Build
allein durchgelassen hätte), Bundles signiert mit `keystore/gebetszeiten.jks`,
Standortindex im Bundle enthalten.

- **Produktion (bzw. zuerst geschlossener Test)** →
  `app/build/outputs/bundle/onlineRelease/app-online-release.aab` (aktueller Stand laut git tag).
- **Wear OS Form-Faktor:** unter „Releases" den Wear-Track aktivieren
  (Erweiterte Einstellungen → Formfaktoren → Wear OS) und
  `wear/build/outputs/bundle/onlineRelease/wear-online-release.aab` (aktueller
  Stand laut git tag) hochladen — das **online**-Bundle, wie am Telefon, weil
  nur dieser Flavor den amtlichen Abruf überhaupt enthält; Wear-Screenshots
  (1:1) im Store-Eintrag unter Wear OS ergänzen. Wear-Apps durchlaufen eine
  eigene kurze Google-Prüfung.
- Beim ersten Upload fragt Play nach **Play App Signing** → zustimmen;
  unser vorhandener Schlüssel wird automatisch der **Upload-Key**.

**Release-Notiz für dieses Update (0.1.16 der Uhr / entsprechender Phone-Stand)
— bitte in die Store-Ankündigung bzw. Release-Notes übernehmen:**
1. **Die Werkseinstellung ändert ihr Verhalten.** Bisher galt bei
   ausgeschaltetem Notausgang-Schalter „amtlich zuerst, Berechnung als
   Auffang". Künftig heißt ausgeschaltet „amtlich oder gar nichts". Wer nie
   einen Schalter angefasst hat — die Mehrheit — sieht für Orte ohne amtliche
   Abdeckung künftig einen Hinweis statt Zeiten. Das ist beabsichtigt; die
   Nutzer sollten das in den Release-Notes lesen, bevor sie sich wundern.
2. **Geändertes Netzverhalten für eine Bestandsgruppe.** Wer „immer rechnen"
   eingeschaltet hatte, löste nie einen Diyanet-Abruf aus. Nach der Migration
   bedeutet sein Schalter „Lücken füllen", und die App ruft für ihn
   regelmäßig amtliche Zeiten ab. Der Online-Schalter bleibt die Kontrolle
   darüber, ob überhaupt abgerufen wird.

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
  ich baue die Bundles jederzeit (`.\gradlew.bat :app:bundleOnlineRelease :wear:bundleOnlineRelease`).

### 8. Jaehrliches Zeiten-Update (amtliche Diyanet-Tabellen)
Die gebuendelten amtlichen Zeiten (shared-assets/official/) gelten je ein
Kalenderjahr. **Du musst dir den Termin nicht merken:** der Unit-Test
`OfficialAssetsIntegrityTest.bundledYearCoversTheNextTwoMonths` liest
`shared-assets/official/coverage.tsv` und wird in jedem lokalen Build rot,
sobald die Abdeckung in weniger als zwei Monaten endet. Seine Meldung
wiederholt die Schritte unten.

Sobald Diyanet das Folgejahr publiziert (erfahrungsgemaess Ende Dezember,
Jahresansicht auf namazvakitleri.diyanet.gov.tr pruefen):
1. `tools/diyanet-fetch/cache/` loeschen (sonst wird das alte Jahr re-emittiert),
2. `python tools/diyanet-fetch/fetch_diyanet.py --year <jahr>` laufen lassen
   (~30-60 min). `--year` ist Pflicht: die Jahresseite ist ein rollierendes
   ~16-Monats-Fenster, das Skript schreibt nur Zeilen dieses Jahres und bricht
   hart ab, wenn ein Standort das Jahr nicht lueckenlos abdeckt,
3. Report pruefen: >=500 Standorte und **~4.6 KB je Standort**. Nicht die
   Gesamtgroesse ist der Massstab — sie waechst mit der Zahl der Orte (2026:
   621 Orte / 2.71 MB, 2027: 947 Orte / 4.28 MB). Auffaellig waere nur, wenn
   der Wert JE STANDORT steigt; davor warnt das Skript,
4. Tabellen des Vorjahrs entfernen
   (`git rm shared-assets/official/tables/t*-<altjahr>.tsv`, das Skript warnt
   danach), dann `git add shared-assets/official` — dazu gehoert das vom
   Skript neu geschriebene `coverage.tsv`,
5. **Die handgepruefte Referenz neu ablesen.**
   `OfficialAssetsIntegrityTest.nuernbergReproducesTheHandCheckedReference`
   vergleicht sechs Zeiten eines Tages gegen einen Wert, den ein Mensch auf
   `namazvakitleri.diyanet.gov.tr/tr-TR/11024` abgelesen hat. Alle anderen
   Tests pruefen die Pipeline-Ausgabe gegen sich selbst; nur dieser prueft sie
   gegen die Quelle. Datum, Jahrgang und Werte im Test **nachschlagen**, nicht
   aus der neuen Tabelle uebernehmen — sonst prueft er nichts mehr.
   Vorsicht bei Zusammenfassungen durch Werkzeuge: Akscham steigt im Juni um
   eine Minute pro Tag, ein Zeilenversatz sieht dort aus wie ein Parser-Fehler
   (schon einmal passiert, 11.09.2026),
6. Integritaetstest: `gradlew :app:testOfflineDebugUnitTest` (jetzt wieder
   gruen, inklusive des Stolperdrahts oben). Achte auf
   `onlyOneVintageIsBundled`: er faellt, wenn Schritt 4 vergessen wurde — und
   das waere schlimm, weil die Kennungen (`t000`, `t001`, …) bei jedem Lauf
   neu vergeben werden und ein liegengebliebener Jahrgang die Zeiten eines
   FREMDEN Orts ausliefert (Nuernberg wanderte 2026→2027 von `t507` auf `t809`),
7. App- UND Wear-Update mit erhoehtem versionCode veroeffentlichen (beide
   Module buendeln dieselben Assets).

Das Bundle bleibt dabei DE-Fallback und Quelle fuer die Wear-App; die
Phone-App selbst holt sich das neue Jahr online, sobald es amtlich verfuegbar ist.
