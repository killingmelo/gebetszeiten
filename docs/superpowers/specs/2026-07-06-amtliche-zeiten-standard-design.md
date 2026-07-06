# Amtliche Diyanet-Zeiten als Standard (Phone + Wear, DE-weit)

**Datum:** 2026-07-06
**Status:** Freigegeben (Design-Review mit Nutzer)
**Nachfolger von:** Phase 1 „Amtliche Diyanet-Zeiten für Nürnberg" (2026-06-23)

## Ziel

Die App zeigt **überall amtliche Diyanet-Zeiten als Standard** — nicht nur für
Nürnberg 2026. Die eigene Berechnung (`DiyanetPrayerTimesCalculator`) bleibt
erhalten, aber nur noch als (a) expliziter Opt-in-Toggle und (b) klar
gekennzeichneter Not-Fallback, wenn keine amtlichen Daten verfügbar sind.

Beschlossener Ansatz (Hybrid, „Ansatz A"):

- **Offline-Flavor** (veröffentlicht, beweisbar netzfrei): amtliche
  Jahrestabellen für **alle ~1.463 deutschen Diyanet-Standorte** als
  deduplizierte Assets gebündelt. Jede Ortsauswahl in Deutschland bekommt
  automatisch die Zeiten des nächstgelegenen Diyanet-Standorts.
- **Online-Flavor**: weltweit on-demand — bei Ortseingabe wird die amtliche
  Tabelle des nächstgelegenen Diyanet-Standorts vom Proxy geladen und gecacht.
- **Wear**: nutzt dieselben gebündelten Tabellen (identische Zeiten wie Phone).

## Nicht-Ziele (YAGNI)

- Keine gebündelten Tabellen außerhalb Deutschlands (Ausland: online on-demand
  bzw. Berechnung mit Kennzeichnung).
- Keine Änderung der Karaha-/Nafl-Logik (`IslamicWindows` leitet weiter aus den
  Tageszeiten ab).
- Kein Phone↔Watch-Sync (FOSS-Constraint, keine Play Services) — Wear bündelt
  die Assets selbst.
- Keine Änderung der Berechnungsmethode selbst (Calculator bleibt wie er ist).

## Verifizierte Datenlage (2026-07-06)

- Proxy `prayertimes.api.abdus.dev/api/diyanet`:
  - `locations?country=ALMANYA` → **1.463 deutsche Standorte** (id, Bundesland,
    Ortsname). Kein Koordinatenfeld.
  - `prayertimes?location_id=<id>` → nur **~31 Tage rollierend**, kein
    Datumsbereichs-Parameter (openapi.json geprüft). **Nicht** als Quelle für
    Jahrestabellen geeignet, aber gut für den Online-Flavor.
- Offizielle Seite `namazvakitleri.diyanet.gov.tr/tr-TR/<id>`: Tab
  **„Yıllık Namaz Vakti"** mit komplettem Kalenderjahr (1. Jan – 31. Dez) —
  Quelle für die gebündelten Jahrestabellen (HTML-Scrape pro Standort).
- Spaltenmapping wie gehabt: `imsak→fajr`, `güneş→sunrise`, `öğle→dhuhr`,
  `ikindi→asr`, `akşam→maghrib`, `yatsı→isha`.

## Komponenten

### 1. Daten-Pipeline (neu, `tools/diyanet-fetch/`)

Entwickler-Werkzeug (kein App-Code), einmal pro Jahr manuell ausgeführt:

1. Standortliste vom Proxy laden (`locations?country=ALMANYA`) →
   `locations-de.tsv` Basisdaten.
2. **Koordinaten-Anreicherung**: Ortsname gegen `cities.tsv` (normalisiert)
   matchen; fehlende Orte per einmaligem Geocoding oder manueller Pflege
   ergänzen. Standorte ohne Koordinaten werden ausgelassen (geloggt).
3. Pro Standort die **Jahresansicht** von `namazvakitleri.diyanet.gov.tr`
   scrapen (Rate-Limit ≥ 1 s/Request, resümierbar bei Abbruch, Cache der
   Roh-Antworten).
4. **Dedupe**: identische Jahrestabellen (Minutengleichheit aller 6 Zeiten an
   allen Tagen) werden zu einer Datei zusammengefasst; der Standort-Index
   referenziert die Tabellen-ID.
5. Ausgabe committen:
   - `official/locations-de.tsv`: `diyanetId  name  lat  lng  tableRef`
   - `official/tables/<tableRef>-<jahr>.tsv`: bestehendes TSV-Format
     (`date fajr sunrise dhuhr asr maghrib isha`, kein Header)
6. Größen-Report ausgeben (Ziel: **< 3–4 MB komprimiert pro Jahr**; wenn
   deutlich darüber → Rückfrage statt stillem Riesen-Bundle).

Gebündelt werden **laufendes Jahr + Folgejahr sobald verfügbar** (Diyanet
publiziert Ende Dezember). Jährlicher Ablauf wird in `playstore/CHECKLISTE.md`
bzw. einer Release-Checkliste dokumentiert: Script laufen lassen → Assets-Diff
committen → App-Update veröffentlichen.

### 2. Geteiltes Lookup-Modul

Parser + Nearest-Suche wandern aus dem app-Modul in geteilten Code, den App
**und** Wear nutzen (bevorzugt `core-prayertimes` erweitert um ein
Android-freies `officialtimes`-Paket; Context-/Asset-Zugriff bleibt in dünnen
Adaptern je Modul):

```kotlin
object OfficialLocations {
    /** Nächstgelegener Diyanet-Standort zu (lat,lng), oder null wenn
     *  weiter als maxKm entfernt. */
    fun nearest(locations: List<OfficialLocation>, lat: Double, lng: Double,
                maxKm: Double = 25.0): OfficialLocation?
}
```

- Distanz: Haversine (Formel existiert bereits in `QiblaMath` — wiederverwenden
  oder dorthin teilen).
- **Schwelle 25 km**: näher → amtliche Zeiten des Standorts; weiter → keine
  amtliche Quelle (Berechnung + Kennzeichnung). Verhindert „amtliche" Zeiten
  vom falschen Ort.

### 3. `BundledOfficialSource` v2 (app + wear)

- Lookup **per Koordinaten** (`settings.latitude/longitude`) statt per
  Stadtname — damit greifen die Tabellen für jede deutsche Ortsauswahl aus
  `cities.tsv` und auch für manuelle Koordinaten.
- Multi-Jahr: lädt `<tableRef>-<jahr>.tsv` je nach angefragtem Datum; Datum
  außerhalb aller gebündelten Jahre → `null` (Fallback Berechnung).
- Lazy-Cache je Tabelle wie bisher (`@Volatile` Map, `Dispatchers.IO`).
- Liefert zusätzlich den **Standortnamen** für den Footer (z. B. „Fürth").
- Die bisherige Nürnberg-Namens-Registry entfällt; `nuernberg-2026.tsv` wird
  durch die Pipeline-Ausgabe ersetzt (Nürnberg ist als Diyanet-Standort in den
  1.463 enthalten).

### 4. Quellen-Priorität + Toggle (`PrayerProvider.daily`)

Neue Einstellung `useCalculated: Boolean` (DataStore, Default **false**),
UI-Label „Eigene Berechnung verwenden" in der Settings-Sektion „Anzeige", mit
Untertext (sinngemäß: „Statt amtlicher Diyanet-Tabellen die lokale
astronomische Berechnung verwenden").

```
useCalculated == true  → PrayerSchedule.forDate(...)          (immer Berechnung)
useCalculated == false →
    1. Online-Cache      (nur online-Flavor + useOnline)
    2. BundledOfficialSource v2 (nearest ≤ 25 km, Jahr gebündelt)
    3. PrayerSchedule    (Not-Fallback, gekennzeichnet)
```

Alle Consumer (Widget, Notification, Alarm, Monat, Wear-Screens) profitieren
automatisch, da sie über `PrayerProvider.daily` bzw. den Wear-Adapter gehen.

### 5. Online-Flavor: on-demand weltweit

`DiyanetProxyFetcher` wird ausgebaut:

- **Stadtwechsel** triggert automatischen Fetch (Suche → Standortliste des
  Landes → nearest per Koordinaten, sonst Namens-Suche wie bisher).
- **Auto-Refresh**: wenn der Cache weniger als ~7 Tage Zukunft abdeckt, beim
  nächsten App-Start/Refresh nachladen (Proxy liefert ~31 Tage rollierend).
- Fehlerfall unverändert: leerer Fetch → Bundle → Berechnung.

### 6. Wear

- Wear bündelt dieselben Assets (Gradle: geteiltes Asset-Verzeichnis via
  `sourceSets.assets.srcDir`, keine Datei-Duplikate im Repo).
- `WearPrayer` konsultiert vor der Berechnung `BundledOfficialSource` v2
  (gleicher geteilter Code) mit den Wear-Settings-Koordinaten.
- Wear-Settings bekommen denselben `useCalculated`-Toggle.
- Watch-AAB wächst um die Assetgröße (~3–4 MB) — bewusst akzeptiert
  (Nutzer-Entscheidung im Review).

### 7. UI/UX

- Footer nennt den tatsächlichen Diyanet-Standort:
  „Amtliche Diyanet-Zeiten · <Standortname>" (dynamisch, nicht mehr
  `settings.city`), sonst „Berechnung: Diyanet-Methode (offline)".
- `covers`-Logik im Footer folgt der neuen Prioritätskette inkl. Toggle
  (Toggle an → immer „Berechnung"-Label).
- Keine weiteren UI-Umbauten (Warn-Banner o. Ä. bewusst nicht — Footer
  reicht, war Nutzer-Entscheidung).

## Edge Cases

- **Ausland offline**: kein Standort ≤ 25 km → Berechnung + Footer-Label.
- **Ausland online**: Proxy-Suche; findet Diyanet keinen Standort ≤ 25 km,
  ebenfalls Berechnung (keine „amtlichen" Zeiten vom falschen Ort).
- **Jahreswechsel offline**: Folgejahr nicht gebündelt (App nicht
  aktualisiert) → ab 1. Januar Berechnung + Label; Online-Cache überbrückt
  im online-Flavor ~30 Tage.
- **Schaltjahr**: Tabellen 2028 haben 366 Zeilen — Parser ist datumsbasiert,
  Integritätstest prüft 365/366 je Jahr.
- **DST/Zeitzone**: Diyanet publiziert lokale Wanduhrzeit inkl.
  Sommerzeit — unverändert übernommen; Annahme Gerät in Europe/Berlin für
  DE-Standorte (dokumentiert, wie Phase 1).
- **Manuelle Koordinaten außerhalb DE** mit Stadtname „Nürnberg": Koordinaten
  entscheiden (kein Namens-Match mehr) — korrektes Verhalten.
- **Defekte/fehlende Asset-Datei**: Parser überspringt defekte Zeilen; fehlende
  Datei → `null` → Berechnung (kein Crash).

## Tests

1. **Pipeline-Ausgabe** (Asset-Integrität, JVM-Test im app-Modul): jeder
   `tableRef` im Index existiert als Datei, jede Tabelle hat 365/366 parsebare
   Zeilen, Zeiten je Tag streng aufsteigend, Index-Koordinaten in DE-Bounds.
2. **Nearest-Lookup**: bekannte Koordinaten (Nürnberg-Zentrum → Standort
   Nürnberg; Dorf bei Fürth → Fürth; Wien → null wegen > 25 km).
3. **Provider-Priorität**: Toggle an → Berechnung trotz vorhandener Tabelle;
   Toggle aus + Tabelle vorhanden → amtlich; Datum 2030 → Berechnung.
4. **Regression**: `YearCompareTest` bleibt (validiert weiterhin den
   Calculator-Fallback gegen Diyanet-Referenz).
5. **Wear-Parität**: gleiche Koordinaten/Datum → Wear-Adapter liefert dieselben
   `SixTimes` wie App-Adapter.
6. **Footer-Label**: amtlich vs. berechnet vs. Toggle-an.

## Auslieferung

Mehrere PRs in dieser Reihenfolge (Details im Implementierungsplan):

1. Pipeline + erzeugte Assets (größter Diff, reiner Daten-/Tools-Commit).
2. Geteilter Lookup + `BundledOfficialSource` v2 + Provider-Toggle + Footer.
3. Wear-Integration.
4. Online-Flavor-Ausbau (Auto-Fetch/Refresh).

Sichtbares Ergebnis: Für jeden Ort in Deutschland zeigen Phone **und** Watch
amtliche Diyanet-Zeiten mit Standortnamen im Footer; Berechnung nur noch per
Toggle oder als gekennzeichneter Fallback.
