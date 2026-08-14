# Quellenbewusste Standortwahl + sichtbare Quellen-Rückmeldung

**Datum:** 2026-08-14
**Status:** Freigegeben (Design-Review mit Nutzer)
**Nachfolger von:** „Online-first weltweit" (2026-07-29), „Amtliche Diyanet-Zeiten als Standard" (2026-07-06)

## Anlass (reproduzierter Bug)

Ort „Serdivan" (Sakarya, Türkei) zeigte `Berechnung: Diyanet-Methode (offline)`
statt amtlicher Zeiten. Ursache ist **kein** Anzeigefehler, sondern eine
Lücke in der Standortauflösung:

`DiyanetProxyFetcher.resolveLocationId("Serdivan")` liefert `null`, weil
Diyanet keinen Standort „Serdivan" führt. Damit gibt
`CompositeDiyanetFetcher.fetch` ein leeres `FetchResult` zurück, der Cache
bleibt leer, und `PrayerProvider.daily` fällt auf die Berechnung. Das Label
war also korrekt — es fehlte die Abdeckung.

Die Auflösungskette (`CompositeDiyanetFetcher.create`) versagte an allen drei
Stellen: DE-Bundle greift nur in Deutschland, der Cache war leer, und die
**Namenssuche als Primärweg** ist strukturell fragil — Diyanets Liste ist
nach Verwaltungsbezirken (İlçe) benannt, die Ortssuche der App kennt
~235.000 GeoNames-Orte. Beide Listen wissen nichts voneinander.

Verschärfend: das Scheitern ist **völlig stumm**. `resolveLocationId` gibt
`null` ohne Log zurück, `CompositeDiyanetFetcher.kt:35` returnt ohne Meldung,
und die UI hat weder Erfolgs- noch Fehleranzeige.

## Ziel

1. **Amtliche Zeiten für jeden Ort, für den Diyanet sie hat** — aufgelöst über
   Koordinaten statt über Namensgleichheit.
2. **Quellenbewusste Ortssuche**: der Nutzer sieht *vor* der Wahl, welche
   Quelle ein Ort liefert — keine stille Überraschung im Footer.
3. **Sichtbare Rückmeldung** über die aktive Quelle, inklusive Zeitstempel des
   letzten Abrufs und Klartext-Fehlergrund.
4. Ortswahl insgesamt intuitiver und schneller (lokal, ohne Netzlatenz).

## Nicht-Ziele (YAGNI)

- **Keine GPS-Ortung.** Die App hat keine Standortberechtigung; das bleibt so.
- **Kein manuelles Diyanet-Standort-Override** in den Einstellungen. Die
  koordinatenbasierte Auflösung plus sichtbares Badge deckt den Bedarf; ein
  Override wäre ein eigenes Feature.
- **Keine gebündelten Jahrestabellen außerhalb Deutschlands.** Es wird nur der
  *Standortindex* gebündelt, nicht die Zeiten — Ausland bleibt on-demand.
- Keine Änderung der Berechnungsmethode, der Karaha-/Nafl-Logik oder des
  Wear-Sync-Vertrags.
- Keine Änderung am offline-Flavor-Verhalten außer der neuen Badge-Anzeige
  (die dort ausschließlich aus dem DE-Bundle gespeist wird).

## Verifizierte Datenlage (2026-08-14, live geprüft)

- `GET /api/diyanet/search?q=Serdivan` → `[]` (leer). **Serdivan existiert in
  Diyanets Standortliste nicht.**
- `GET /api/diyanet/locations?country=TÜRKİYE` → **867 Einträge**, Felder
  `id, country, city, region` (`city` = Provinz, `region` = Bezirk).
  **Kein Koordinatenfeld** — Geokodierung bleibt Aufgabe der Pipeline.
- Provinz Sakarya hat genau 9 Bezirke: AKYAZI, GEYVE, HENDEK, KARASU,
  KAYNARCA, KOCAALİ, PAMUKOVA, **SAKARYA (id 9807)**, TARAKLI. Serdivan wird
  vom Eintrag SAKARYA abgedeckt, Zentrum ca. **7 km** entfernt.
- `GET /api/diyanet/countries` → **205 Länder**.
- Stichprobe der Standortzahlen (für die Index-Größe): ALMANYA 1.195,
  TÜRKİYE 867, FRANSA 530, ABD 482, HOLLANDA 279, BELCIKA 218,
  AVUSTURYA 180, INGILTERE 153, ENDONEZYA 52, MISIR 23 — und
  **SUUDİ ARABİSTAN 0** (es gibt Länder ohne jeden Diyanet-Standort).
  Elf Länder ergeben zusammen ~3.980; hochgerechnet auf 205 Länder mit
  kleinem Rest-Durchschnitt liegt der Index bei **~10.000 Einträgen**.
- Es gibt **keine** Endpunkte `/states` oder `/cities` (beide `404`), und
  `search?q=<Provinz>,<Bezirk>` antwortet `500`. Die Länderliste plus
  `locations?country=…` ist der einzige Weg zur vollständigen Liste.
- Der Unterschied ist inhaltlich relevant, nicht kosmetisch — Sakarya
  (id 9807), 14.08.2026:

  | Quelle | Fajr | Isha |
  |---|---|---|
  | Amtlich Diyanet | 04:23 | **21:36** |
  | Eigene Berechnung (App) | 04:22 | **21:41** |

## Komponenten

### 1. Standortindex-Pipeline (neu, `tools/diyanet-index/build_index.py`)

Erzeugt `locations-world.tsv`. Ablauf:

1. `/countries` → 205 Ländernamen.
2. Pro Land `/locations?country=<name>` (Rate-Limit 1 req/s, ~4 min gesamt,
   Roh-JSON in `cache/` → resumierbar wie die bestehende Pipeline). Leere
   Antworten sind normal (z. B. Saudi-Arabien) und kein Fehler.
3. Geokodierung jedes Standorts gegen `app/src/main/assets/cities.tsv` +
   `city-aliases.tsv`. Die Helfer (`normalize`, Matching) werden aus
   `tools/diyanet-fetch/fetch_diyanet.py` **importiert, nicht kopiert**.
4. Der **ISO2-Ländercode fällt bei der Geokodierung gratis an** — er steht in
   der getroffenen GeoNames-Zeile. Keine Handmapping-Tabelle für 205
   türkische Ländernamen nötig.
5. Nicht auflösbare Standorte fallen aus dem Index (mit Report der Anzahl),
   genau wie in der DE-Pipeline. Sie verlieren nichts: für sie greift
   weiterhin die Namenssuche als letzte Stufe.

Ausführung: einmal jährlich zusammen mit `fetch_diyanet.py`; Ergebnis wird
committet und reviewt.

**Entscheidung zur Quelle:** Der Community-Proxy bleibt Quelle der
Standortliste. Für den *Index-Aufbau* ist das unkritisch — es passiert
einmal jährlich offline, das Ergebnis wird gebündelt, committet und im
Review geprüft; zur Laufzeit ist der Proxy dafür nie im Spiel. Alternative,
falls die Abhängigkeit später stört: Diyanets eigene Hierarchie-Seite
parsen (mehr Parser-Arbeit, gleiche Ausgabe) — der Index-Vertrag bleibt
davon unberührt.

### 2. Asset `app/src/online/assets/official/locations-world.tsv`

Format (Tab-getrennt, kein Header), analog `locations-de.tsv`:

```
9807	SAKARYA	SAKARYA	TR	40.7806	30.4033
id	name(Bezirk)	province	iso2	lat	lng
```

Umfang: **~10.000 Zeilen, ca. 400 KB** unkomprimiert (im APK ~130 KB). Zum
Vergleich: `cities.tsv` ist 12,8 MB, die amtlichen Tabellen 13 MB — der Index
fällt neben den bestehenden Assets nicht ins Gewicht.

**Platzierung im online-Flavor, nicht in `shared-assets`.** Begründung:
`shared-assets` ist in `app/build.gradle.kts:87` an den `main`-SourceSet
gehängt und landet damit in *beiden* Flavors. Der offline-Flavor kann mit
Diyanet-IDs nichts anfangen (er scrapet nie) — er bliebe ohne Nutzen
schwerer, mit einem Asset, das nach Netzwerkfähigkeit aussieht. Das
Wear-Modul braucht den Index ebenfalls nicht (es sync't fertige Zeiten).

### 3. Lookup-Modul (neu, `core-prayertimes`)

```kotlin
data class DiyanetPlace(
    val diyanetId: Int,
    val name: String,       // Bezirk, z. B. "SAKARYA"
    val province: String,   // z. B. "SAKARYA"
    val countryCode: String,// ISO2, z. B. "TR"
    val latitude: Double,
    val longitude: Double,
)

fun parseDiyanetPlaces(lines: Sequence<String>): List<DiyanetPlace>

object DiyanetPlaces {
    fun nearest(places: List<DiyanetPlace>, lat: Double, lng: Double, maxKm: Double = 25.0): DiyanetPlace?
    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double
}
```

**Eigener Typ statt Erweiterung von `OfficialLocation`.** Die beiden
beantworten verschiedene Fragen: `OfficialLocation` sagt *welche gebündelte
Tabelle gilt* (`tableRef`, auch im offline-Flavor und auf Wear),
`DiyanetPlace` sagt *welche ID gescraped wird* plus Anzeigedaten
(Provinz/Land). Ein gemeinsamer Typ müsste in beiden Rollen Felder leer
lassen. `haversineKm` wird geteilt — es liegt schon `internal` im selben
Modul (`OfficialLocations.kt`).

Die 25-km-Schwelle übernimmt bewusst die bestehende Konvention aus
`OfficialLocations.nearest`.

### 4. Auflösungskette (`CompositeDiyanetFetcher.create`)

Neuer zweiter Schritt; die Namenssuche verliert ihre Rolle als Primärweg:

| # | Schritt | Serdivan |
|---|---|---|
| 1 | `BundledOfficialSource.nearestLocation` (DE, id-genau) | – |
| 2 | **`DiyanetPlaceIndex.nearest(lat, lng, ≤25 km)`** ← neu | **9807, 7 km ✓** |
| 3 | `OfficialTimesCache.cachedLocationId` | – |
| 4 | Proxy-Namenssuche (Rettung für Index-Lücken) | – |

Bereitstellung über das etablierte Flavor-Muster (`DiyanetPlaceIndex` analog
zu `PlaceSearchProvider`): online = Index aus dem Asset, offline = `null`.
Der Index wird wie `Cities.preload` einmalig vorgewärmt.

Die Diyanet-ID wird **nicht** in `AppSettings` persistiert — sie ist aus den
Koordinaten lokal in Millisekunden herleitbar, und ein zweiter
Wahrheitsspeicher neben dem Cache-Stempel wäre eine Fehlerquelle.

**Ehrlichkeitsregel:** Der nächstgelegene Bezirk kann in der Nachbarprovinz
liegen. Das ist inhaltlich unschädlich (Minutendifferenz auf 25 km) und wird
nicht versteckt: die Anzeige nennt immer den echten Diyanet-Standort samt
Entfernung.

### 5. Ortssuche (`LocationSettings` → eigene Datei)

`LocationSettings` wandert aus der 1.623 Zeilen langen `MainActivity.kt` in
`ui/LocationSettings.kt` — sie wird durch die Badges länger, nicht kürzer.

Jeder Treffer trägt ein **lokal berechnetes** Badge (beide Indizes vorgewärmt,
kein Netz pro Tastendruck):

```
Serdivan     Sakarya · Türkei        [Amtlich · Sakarya, 7 km]
Nürnberg     Bayern · Deutschland    [Amtlich]
Wien         Wien · Österreich       [Berechnet]
```

Die Klassifikation ist eine reine Funktion (testbar ohne Android):

```kotlin
sealed interface TimesSourceBadge {
    data class Bundled(val locationName: String) : TimesSourceBadge
    data class Official(val locationName: String, val distanceKm: Int) : TimesSourceBadge
    data object Calculated : TimesSourceBadge
}
```

Weitere Best-Practice-Punkte, die das Sheet heute vermisst:

- **Autofokus + Tastatur** beim Öffnen des Sheets, `X`-Button zum Leeren.
- **Aktueller Ort** als erste Zeile mit Häkchen; **letzte Orte** (max. 5, in
  DataStore) bei leerem Suchfeld → Ortswechsel ohne einen Tastendruck.
- **Koordinatenfelder in einen „Manuell"-Aufklapper.** Heute stehen sie
  (`MainActivity.kt:1331`) permanent im Weg, obwohl sie der Sonderfall sind.
- Der bestehende 200-ms-Debounce und die Vorwärmung bleiben; die lokale Suche
  braucht kein Netz und keine Ladeanzeige.

### 6. Rückmeldung — drei Ebenen

Ein Toast allein wäre falsch: er ist verschwunden, wenn man ihn braucht.
Deshalb permanente Wahrheit + Details auf Abruf + Hinweis nur bei Änderung.

1. **Footer (immer sichtbar).** `Amtliche Diyanet-Zeiten · Sakarya` bzw.
   `Berechnet · Diyanet-Methode`. **Hier wird ein Nebenbefund mitbehoben:**
   `MainActivity.kt:270` hat für `officialName` nur `settings, selectedDate`
   als `produceState`-Keys. Nach einem erfolgreichen Abruf bleibt der Footer
   deshalb auf „offline" stehen, bis Datum oder Einstellung wechselt. `tick`
   kommt als Key hinzu (wie bei `dayInfo`).
2. **Statuszeile im Einstellungs-Sheet.** Aktive Quelle, Diyanet-Standort +
   ID, abgedeckter Zeitraum, letzter Abrufversuch als Zeitstempel,
   Fehlergrund im Klartext, Button „Jetzt aktualisieren". Dafür bekommt
   `OfficialTimesCache` zwei Felder (`last_attempt`, `last_error`) — der
   DataStore `official_times` existiert bereits.
3. **Snackbar nur beim Zustandswechsel.** „Amtliche Zeiten für Sakarya
   geladen · 365 Tage" nach Ortswechsel oder erstem Erfolg — nicht bei jedem
   Start, das wäre Lärm.

Zusätzlich bekommen die stummen `null`-Rückgaben Log-Warnungen mit Grund:
`CompositeDiyanetFetcher.kt:35` und `DiyanetProxyFetcher.resolveLocationId`.

## Edge Cases

- **Ort > 25 km von jedem Diyanet-Standort** (abgelegene Orte, Ozean-
  Koordinaten): Badge `Berechnet`, Footer nennt die Berechnung. Kein
  „amtlicher" Anspruch aus großer Entfernung.
- **Index-Lücke** (Standort nicht geokodierbar): Stufe 4 (Namenssuche) kann
  weiterhin greifen; scheitert auch die, ist es der bekannte
  Berechnungs-Fallback — jetzt aber mit Fehlergrund in der Statuszeile.
- **Offline-Flavor:** `DiyanetPlaceIndex` ist `null`; Badges entstehen
  ausschließlich aus dem DE-Bundle. Keine Netzwerkpfade, keine neuen Assets.
- **Kein Netz beim Abruf:** unverändert nicht-fatal (leeres Ergebnis →
  Berechnung), plus Zeitstempel „letzter Versuch" und Fehlergrund.
- **Stempel-Schutz bleibt unangetastet:** ein Ortswechsel invalidiert den
  Cache weiterhin über `stampMatches`, es werden nie Zeiten eines anderen
  Orts gezeigt.
- **Nachbarprovinz-Treffer:** erlaubt, aber immer mit echtem Standortnamen
  und Entfernung ausgewiesen.

## Tests

TDD, in der Reihenfolge der Komponenten.

- **core-prayertimes:** `parseDiyanetPlaces` (defekte/kurze Zeilen werden
  übersprungen), `DiyanetPlaces.nearest` genau an der 25-km-Grenze
  (innerhalb/außerhalb), `distanceKm` gegen bekannte Referenzwerte.
- **app/test:** `TimesSourceBadge`-Klassifikation als reine Funktion
  (Bundled / Official / Calculated), Statuszeilen-Formatierung inkl.
  Fehlergrund, Recents-Rotation (max. 5, keine Duplikate).
- **app/testOnline:** `CompositeDiyanetFetcherTest` um die neue
  Kettenreihenfolge erweitern — Index **vor** Cache **vor** Namenssuche, und
  Index-Treffer verhindert jeden Netzaufruf zur Namensauflösung.
- **`OfficialAssetsIntegrityTest`:** Index parst vollständig, keine doppelten
  IDs, Koordinaten in gültigen Bereichen, und **id 9807 „SAKARYA" ist
  vorhanden** — der Regressionswächter für genau diesen Bug.
- **Gerätecheck (manuell):** Serdivan wählen → Badge `Amtlich · Sakarya, 7 km`,
  Footer `Amtliche Diyanet-Zeiten · Sakarya`, Isha **21:36** statt 21:41.
  Screenshot. Gegenprobe Wien → `Berechnet`.

## Auslieferung

1. Index-Pipeline laufen lassen, `locations-world.tsv` committen, Größe und
   unmatched-Report prüfen.
2. Unit-Tests grün (`.\gradlew.bat :app:testOnlineDebugUnitTest`,
   `:app:testOfflineDebugUnitTest`, `:core-prayertimes:test`).
3. Gerätecheck Serdivan + Wien mit Screenshots.
4. Versionsbump + Changelog-Einträge (de-DE/en-US): „Amtliche Diyanet-Zeiten
   jetzt weltweit auch für Orte ohne eigenen Diyanet-Eintrag; Ortssuche zeigt
   die Quelle vorab."
