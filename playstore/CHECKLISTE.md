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

### 8. Zeiten-Update (amtliche Diyanet-Tabellen)
**Die Jahresseite liefert immer nur das NAECHSTE Kalenderjahr** (plus 31 Tage
ab heute in einer zweiten Tabelle, die das Skript nicht liest). Den Rest des
LAUFENDEN Jahres gibt sie nie wieder her. Deshalb die eiserne Regel: ein Lauf
**ergaenzt** den vorhandenen Jahrgang, er **ersetzt** ihn nie. Wer sie bricht,
verliert Tage unwiederbringlich — genau das ist in `647fd0d` passiert
(„Jahrgang 2027 loest 2026 ab"), und danach begann die Reserve erst am
01.01.2027.

**Du musst dir den Termin nicht merken:** zwei Unit-Tests in
`OfficialAssetsIntegrityTest` werden in jedem lokalen Build rot —
`bundledYearCoversTheNextTwoMonths`, sobald die Abdeckung in weniger als zwei
Monaten endet, und `coverageHasAlreadyBegun`, sobald die Ankerorte Nuernberg
und Berlin heute keine Reserve mehr haben. Ihre Meldungen wiederholen die
Schritte unten.

Wenn einer der beiden rot wird:
1. `tools/diyanet-fetch/cache/` loeschen (sonst wird das alte Fenster re-emittiert),
2. `python tools/diyanet-fetch/fetch_diyanet.py --out-dir /tmp/neu` laufen
   lassen (~20-60 min) — **in ein temporaeres Verzeichnis**, nicht direkt in
   die Assets. Stichtag und Ende leitet das Skript aus den Daten ab und bricht
   hart ab, wenn ein Standort die Tage dazwischen nicht lueckenlos abdeckt,
2b. zusammenfuehren statt ersetzen:
   `python tools/diyanet-fetch/merge_bundles.py --base shared-assets/official
   --overlay /tmp/neu --out shared-assets/official`. Verbunden wird ueber die
   Diyanet-Standort-Kennung; bei Ueberschneidung gewinnt der neuere Abruf,
3. Report pruefen: >=500 Standorte und **~4.6 KB je Standort und 365 Tage**.
   Nicht die Gesamtgroesse ist der Massstab — sie waechst mit der Zahl der
   Orte und mit der Fensterlaenge (2026: 621 Orte / 2.71 MB ueber 365 Tage,
   2027: 947 Orte / 4.28 MB ueber 365 Tage, zusammengefuehrt 19.09.2026:
   947 Orte / 5.72 MB ueber bis zu 730 Tage). Auffaellig waere nur, wenn der
   Wert JE STANDORT UND JAHR steigt; davor warnt das Skript. `merge_bundles.py`
   nennt ausserdem, wie viele Orte eine Reserve fuer HEUTE haben — nach dem
   Lauf vom 19.09.2026 sind das 621 von 947, weil die uebrigen 326 erst mit
   dem Jahrgang 2027 dazukamen,
4. Tabellen des vorigen Laufs entfernen
   (`git rm shared-assets/official/tables/t*-<alte-kennung>.tsv`, das Skript
   warnt danach), dann `git add shared-assets/official` — dazu gehoert das vom
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
   gruen, inklusive der Stolperdraehte oben). Achte auf
   `onlyOneVintageIsBundled`: er faellt, wenn Schritt 4 vergessen wurde — und
   das waere schlimm, weil die Kennungen (`t000`, `t001`, …) bei jedem Lauf
   neu vergeben werden (Nuernberg wanderte 2026→2027 von `t507` auf `t809`).
   Seit der Dateiname die Lauf-Kennung traegt (`t000-20260919.tsv`, auch im
   `tableRef` von `locations-de.tsv`) kann ein alter Jahrgang keine fremden
   Zeiten mehr ausliefern — er waere aber totes Gewicht im APK,
7. App- UND Wear-Update mit erhoehtem versionCode veroeffentlichen (beide
   Module buendeln dieselben Assets).

Das Bundle bleibt dabei DE-Fallback und Quelle fuer die Wear-App; die
Phone-App selbst holt sich das neue Jahr online, sobald es amtlich verfuegbar ist.
