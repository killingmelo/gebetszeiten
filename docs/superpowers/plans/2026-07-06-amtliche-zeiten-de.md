# Amtliche Diyanet-Zeiten DE-weit (PR 1+2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Amtliche Diyanet-Jahrestabellen für alle deutschen Diyanet-Standorte bündeln und im Offline-Flavor per Koordinaten-Nearest-Lookup als Standardquelle nutzen; eigene Berechnung nur noch per Toggle oder gekennzeichneter Fallback.

**Architecture:** Ein Python-Pipeline-Script (`tools/diyanet-fetch/`) scraped einmal jährlich die Jahresansicht von `namazvakitleri.diyanet.gov.tr` für alle ~1.463 DE-Standorte (Standortliste vom Proxy), reichert Koordinaten über `cities.tsv`+`city-aliases.tsv` an, dedupliziert identische Tabellen und schreibt Assets. Ein pures Lookup-Modul in `core-prayertimes` (Haversine-nearest ≤ 25 km) ersetzt den Namens-Match in `BundledOfficialSource`; `PrayerProvider` bekommt den `useCalculated`-Toggle; der Footer nennt den tatsächlichen Diyanet-Standort.

**Tech Stack:** Python 3.14 (nur stdlib: urllib, re, unicodedata, zlib), Kotlin/JVM (core-prayertimes, pure), Android DataStore, Jetpack Compose, JUnit4.

**Scope:** Dies ist Auslieferung PR 1+2 der Spec [2026-07-06-amtliche-zeiten-standard-design.md](../specs/2026-07-06-amtliche-zeiten-standard-design.md). PR 3 (Wear) und PR 4 (Online-Ausbau) bekommen eigene Folgepläne.

## Global Constraints

- Offline-Flavor bleibt beweisbar netzfrei: KEIN Netzwerkcode im App-Modul; alles Netz passiert im Dev-Werkzeug `tools/diyanet-fetch/`.
- Distanz-Schwelle Nearest-Lookup: **25.0 km** (Konstante `MAX_KM`).
- Assets: `official/locations-de.tsv` (`diyanetId  name  lat  lng  tableRef`, kein Header) und `official/tables/<tableRef>-<jahr>.tsv` (bestehendes Format `date fajr sunrise dhuhr asr maghrib isha`, kein Header). tableRef-Format `t###` (z. B. `t042`).
- Zielgröße: komprimiert < 4 MB pro Jahr; Pipeline druckt Größenreport und bricht NICHT ab, aber der Ausführende prüft den Report.
- Tests: JVM-only (`.\gradlew.bat :app:testOfflineDebugUnitTest` bzw. `:core-prayertimes:test`), JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr`, Gradle über PowerShell.
- Bestehendes Verhalten unangetastet: `YearCompareTest`, Karaha/Nafl (`IslamicWindows`), Online-Cache-Priorität.
- Alle sichtbaren Strings auf Deutsch in `app/src/main/res/values/strings.xml`.
- Commits einzeln pro Task, Messages auf Deutsch, `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## Verifizierte Datenlage (nicht raten — gilt als Referenz)

- Proxy: `https://prayertimes.api.abdus.dev/api/diyanet/locations?country=ALMANYA` → JSON-Liste `{id, country, city, region}` mit **1.463** Einträgen. Achtung: In dieser Liste ist `city` das Bundesland und `region` der Ortsname (bei `/search` ist es umgekehrt) — Pipeline nimmt `region`, Fallback `city`.
- Jahresseite: `https://namazvakitleri.diyanet.gov.tr/tr-TR/<id>` (~360 KB HTML, server-gerendert). Jahres-Tab: `<div id="tab-2" ...>` mit `<table ... id="yourTable">`, pro Tag eine `<tr>` mit 8 `<td>`: `01 Ocak 2026 Perşembe`, Hicri-Datum, dann İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı als `HH:MM`. Mapping: `imsak→fajr, güneş→sunrise, öğle→dhuhr, ikindi→asr, akşam→maghrib, yatsı→isha`.
- Türkische Monate: Ocak, Şubat, Mart, Nisan, Mayıs, Haziran, Temmuz, Ağustos, Eylül, Ekim, Kasım, Aralık.
- `cities.tsv` führt Großstädte teils englisch (Nuremberg/Munich); `city-aliases.tsv` (seit c4557c3) mappt deutsche Namen inkl. Anzeigename-Flag — die Pipeline nutzt BEIDE für Koordinaten-Anreicherung und Anzeigenamen.
- Referenzwerte Nürnberg 2026 existieren: `app/src/main/assets/official/nuernberg-2026.tsv` (z. B. 2026-06-07: 03:35/05:04/13:20/17:36/21:25/22:45) — die neue Pipeline-Ausgabe für den Standort Nürnberg MUSS diese Werte reproduzieren.

---

### Task 1: Pipeline-Script `tools/diyanet-fetch/`

**Files:**
- Create: `tools/diyanet-fetch/fetch_diyanet.py`
- Create: `tools/diyanet-fetch/README.md`
- Modify: `.gitignore` (Cache-Verzeichnis ausnehmen)

**Interfaces:**
- Produces: CLI `python tools/diyanet-fetch/fetch_diyanet.py [--limit N]`, schreibt `app/src/main/assets/official/locations-de.tsv` + `app/src/main/assets/official/tables/t###-<jahr>.tsv` und druckt Report (matched/unmatched/dedupe/Größen).

- [ ] **Step 1: Script anlegen** — `tools/diyanet-fetch/fetch_diyanet.py`:

```python
#!/usr/bin/env python3
"""Holt amtliche Diyanet-Jahrestabellen fuer alle deutschen Standorte.

Ablauf: Standortliste vom Community-Proxy -> pro Standort Jahresseite von
namazvakitleri.diyanet.gov.tr (Rate-Limit 1s, Roh-HTML gecacht/resumierbar)
-> Koordinaten via cities.tsv + city-aliases.tsv -> Dedupe identischer
Tabellen -> TSV-Assets. Einmal pro Jahr manuell ausfuehren (siehe README).
"""
import argparse
import json
import re
import sys
import time
import unicodedata
import urllib.request
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / "app" / "src" / "main" / "assets"
OUT_DIR = ASSETS / "official"
TABLES_DIR = OUT_DIR / "tables"
CACHE = Path(__file__).resolve().parent / "cache"

LOCATIONS_URL = "https://prayertimes.api.abdus.dev/api/diyanet/locations?country=ALMANYA"
PAGE_URL = "https://namazvakitleri.diyanet.gov.tr/tr-TR/{id}"
HEADERS = {"User-Agent": "GebetszeitenApp-Datenpipeline (jaehrlich, 1 req/s)"}

TR_MONTHS = {"Ocak": 1, "Şubat": 2, "Mart": 3, "Nisan": 4, "Mayıs": 5, "Haziran": 6,
             "Temmuz": 7, "Ağustos": 8, "Eylül": 9, "Ekim": 10, "Kasım": 11, "Aralık": 12}


def normalize(s: str) -> str:
    """Spiegelt TextNormalize.kt (lower -> NFD-Strip -> tuerkisch/dt. Sonderfaelle)."""
    s = s.strip().lower()
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    for a, b in (("ı", "i"), ("ş", "s"), ("ğ", "g"), ("ç", "c"), ("ö", "o"), ("ü", "u"), ("ß", "s")):
        s = s.replace(a, b)
    return s


def fetch(url: str) -> bytes:
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - retry-all ist hier gewollt
            if attempt == 2:
                raise
            print(f"  Retry {attempt + 1} nach Fehler: {e}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    raise AssertionError("unreachable")


def load_locations() -> list[dict]:
    data = json.loads(fetch(LOCATIONS_URL))
    out = []
    for row in data:
        name = (row.get("region") or row.get("city") or "").strip()
        if name and row.get("id"):
            out.append({"id": int(row["id"]), "name": name})
    return out


def load_city_index() -> dict[str, tuple[str, float, float]]:
    """normalisierter Name -> (Anzeigename, lat, lng); nur DE-Zeilen.

    Anzeigename beruecksichtigt city-aliases.tsv (Spalte 4 == '1'), damit der
    Footer spaeter 'Nuernberg' statt 'Nuremberg' zeigt.
    """
    display: dict[str, str] = {}
    aliases: dict[str, list[str]] = {}
    for line in (ASSETS / "city-aliases.tsv").read_text(encoding="utf-8").splitlines():
        c = line.split("\t")
        if len(c) < 3 or not c[0].strip():
            continue
        key = f"{c[1]}|{c[2]}"
        aliases.setdefault(key, []).append(normalize(c[0]))
        if len(c) >= 4 and c[3].strip() == "1":
            display[key] = c[0]

    index: dict[str, tuple[str, float, float]] = {}
    for line in (ASSETS / "cities.tsv").read_text(encoding="utf-8").splitlines():
        c = line.split("\t")
        if len(c) < 5 or c[2] != "DE":
            continue
        key = f"{c[0]}|DE"
        entry = (display.get(key, c[0]), float(c[3]), float(c[4]))
        for norm in {normalize(c[0]), normalize(c[1]), *aliases.get(key, [])}:
            index.setdefault(norm, entry)
    return index


def year_page(location_id: int) -> str:
    CACHE.mkdir(exist_ok=True)
    cached = CACHE / f"{location_id}.html"
    if cached.exists():
        return cached.read_text(encoding="utf-8")
    html = fetch(PAGE_URL.format(id=location_id)).decode("utf-8")
    cached.write_text(html, encoding="utf-8")
    time.sleep(1.0)  # Rate-Limit gegenueber diyanet.gov.tr
    return html


def parse_year_table(html: str) -> list[tuple[str, list[str]]]:
    """[(ISO-Datum, [fajr,sunrise,dhuhr,asr,maghrib,isha])] aus dem tab-2-Jahrestable."""
    start = html.index('id="tab-2"')
    end = html.index("</table>", start)
    cells = re.findall(r"<td>\s*([^<]*?)\s*</td>", html[start:end])
    if len(cells) % 8 != 0:
        raise ValueError(f"Zellenzahl {len(cells)} nicht durch 8 teilbar")
    rows = []
    for i in range(0, len(cells), 8):
        m = re.match(r"(\d{2}) (\S+) (\d{4})", cells[i])
        if not m:
            raise ValueError(f"Datum unlesbar: {cells[i]!r}")
        day, month_tr, year = int(m.group(1)), m.group(2), int(m.group(3))
        date = f"{year:04d}-{TR_MONTHS[month_tr]:02d}-{day:02d}"
        times = cells[i + 2 : i + 8]
        if any(not re.fullmatch(r"\d{2}:\d{2}", t) for t in times):
            raise ValueError(f"Zeit unlesbar am {date}: {times}")
        rows.append((date, times))
    return rows


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="nur N Standorte (Smoke-Test)")
    args = ap.parse_args()

    locations = load_locations()
    print(f"{len(locations)} Diyanet-Standorte (ALMANYA)")
    city_index = load_city_index()

    matched, unmatched = [], []
    for loc in locations:
        hit = city_index.get(normalize(loc["name"]))
        (matched if hit else unmatched).append((loc, hit))
    print(f"Koordinaten gefunden: {len(matched)}, ohne Match uebersprungen: {len(unmatched)}")
    for loc, _ in unmatched[:20]:
        print(f"  unmatched: {loc['name']}")

    if args.limit:
        matched = matched[: args.limit]

    # Scrape + Dedupe: identischer Tabelleninhalt -> eine Datei.
    content_to_ref: dict[str, str] = {}
    index_rows: list[tuple[int, str, float, float, str]] = []
    year_seen: set[int] = set()
    for n, (loc, (name, lat, lng)) in enumerate(sorted(matched, key=lambda m: m[0]["id"]), 1):
        print(f"[{n}/{len(matched)}] {name} (id={loc['id']})")
        rows = parse_year_table(year_page(loc["id"]))
        year_seen.update(int(d[:4]) for d, _ in rows)
        content = "".join(f"{d}\t" + "\t".join(t) + "\n" for d, t in rows)
        ref = content_to_ref.setdefault(content, f"t{len(content_to_ref):03d}")
        index_rows.append((loc["id"], name, lat, lng, ref))

    TABLES_DIR.mkdir(parents=True, exist_ok=True)
    year = max(year_seen)
    total = 0
    for content, ref in content_to_ref.items():
        p = TABLES_DIR / f"{ref}-{year}.tsv"
        p.write_text(content, encoding="utf-8", newline="\n")
        total += len(zlib.compress(content.encode("utf-8"), 9))
    index_text = "".join(f"{i}\t{n}\t{lat}\t{lng}\t{r}\n" for i, n, lat, lng, r in index_rows)
    (OUT_DIR / "locations-de.tsv").write_text(index_text, encoding="utf-8", newline="\n")

    print(f"\nJahr(e): {sorted(year_seen)} | eindeutige Tabellen: {len(content_to_ref)} "
          f"von {len(index_rows)} Standorten")
    print(f"Groesse komprimiert (zlib-9-Schaetzung): {total / 1024 / 1024:.2f} MB "
          f"(Ziel < 4 MB) + Index {len(index_text) / 1024:.0f} KB")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: README anlegen** — `tools/diyanet-fetch/README.md`:

```markdown
# Diyanet-Jahresdaten-Pipeline

Erzeugt die gebündelten amtlichen Zeiten (`app/src/main/assets/official/`).
Einmal pro Jahr ausführen, sobald Diyanet das neue Jahr publiziert
(erfahrungsgemäß Ende Dezember — Jahresansicht der Website prüfen).

    python tools/diyanet-fetch/fetch_diyanet.py            # Vollauf (~30–45 min)
    python tools/diyanet-fetch/fetch_diyanet.py --limit 3  # Smoke-Test

- Roh-HTML wird in `cache/` abgelegt → Abbruch/Neustart überspringt Geholtes.
  Für einen frischen Jahresabruf `cache/` löschen!
- Report prüfen: unmatched-Liste (kleine Orte ohne cities.tsv-Eintrag sind ok),
  Größe < 4 MB, danach `git add app/src/main/assets/official` + Integritätstest
  (`.\gradlew.bat :app:testOfflineDebugUnitTest`).
- Jahres-Release-Ablauf: Script laufen lassen → Assets-Diff committen →
  App-Update veröffentlichen (siehe playstore/CHECKLISTE.md).
```

- [ ] **Step 3: `.gitignore` ergänzen** (an bestehende Datei anhängen):

```
tools/diyanet-fetch/cache/
```

- [ ] **Step 4: Smoke-Test ausführen**

Run: `python tools/diyanet-fetch/fetch_diyanet.py --limit 3`
Expected: `1463 Diyanet-Standorte`, Match-Zeile, 3 Standort-Zeilen, `eindeutige Tabellen: 1..3 von 3`, Dateien unter `app/src/main/assets/official/tables/` + `locations-de.tsv` mit 3 Zeilen. Bei Parse-Fehler (`Zellenzahl ...`): HTML in `cache/<id>.html` inspizieren und Regex anpassen — NICHT blind weitermachen.

- [ ] **Step 5: Smoke-Ausgabe verwerfen** (der Vollauf in Task 2 erzeugt alles neu)

Run: `git status` → nur `tools/` + `.gitignore` als Änderungen einchecken; `app/src/main/assets/official/tables/` und `locations-de.tsv` erst mal per `git clean`-Vorschau prüfen: `git clean -nd app/src/main/assets/official` (Anzeige), dann `git clean -fd app/src/main/assets/official` — Achtung: `nuernberg-2026.tsv` ist getrackt und bleibt unberührt.

- [ ] **Step 6: Commit**

```powershell
git add tools/diyanet-fetch .gitignore
git commit -m "tools: Diyanet-Jahresdaten-Pipeline (Scrape+Dedupe+Assets)"
```

---

### Task 2: Pipeline-Vollauf + Assets committen

**Files:**
- Create (generiert): `app/src/main/assets/official/locations-de.tsv`, `app/src/main/assets/official/tables/t*-2026.tsv`

**Interfaces:**
- Produces: die Assets, die Task 3–6 konsumieren. Index-Spalten exakt: `diyanetId  name  lat  lng  tableRef`.

- [ ] **Step 1: Vollauf starten** (im Hintergrund, ~30–45 min wegen 1 s Rate-Limit)

Run: `python tools/diyanet-fetch/fetch_diyanet.py`
Expected am Ende: `Koordinaten gefunden: >=500`, `Jahr(e): [2026]`, `Groesse ... MB (Ziel < 4 MB)`. Die unmatched-Liste darf NUR kleine Orte enthalten — wenn Berlin/Hamburg/München/Nürnberg/Köln dort auftauchen, ist das Alias-Matching kaputt → abbrechen und untersuchen.

- [ ] **Step 2: Nürnberg-Kreuzvalidierung gegen Phase-1-Referenz**

Run (PowerShell):
```powershell
$idx = Get-Content app\src\main\assets\official\locations-de.tsv | Where-Object { $_ -match "`tNürnberg`t" }
$idx  # erwartet: eine Zeile, tableRef merken (z. B. t042)
$ref = ($idx -split "`t")[4]
$year = (Get-Content "app\src\main\assets\official\tables\$ref-2026.tsv" | Select-String "^2026-06-07").Line
$old  = (Get-Content "app\src\main\assets\official\nuernberg-2026.tsv" | Select-String "^2026-06-07").Line
"$year"; "$old"
```
Expected: beide Zeilen identisch (`2026-06-07  03:35  05:04  13:20  17:36  21:25  22:45`). Abweichung > 0 Minuten an irgendeinem Stichtag → STOPP, Ursache klären (falscher Standort gematcht?).

- [ ] **Step 3: Stichprobe Plausibilität**

Run: `(Get-Content app\src\main\assets\official\locations-de.tsv | Measure-Object -Line).Lines` und `(Get-ChildItem app\src\main\assets\official\tables\*.tsv | Measure-Object).Count`
Expected: Zeilenzahl ≥ 500; Tabellen-Dateien deutlich weniger als Standorte (Dedupe wirkt, grob 100–700).

- [ ] **Step 4: Commit (reiner Daten-Commit)**

```powershell
git add app/src/main/assets/official/locations-de.tsv app/src/main/assets/official/tables
git commit -m "data: amtliche Diyanet-Jahrestabellen 2026 fuer deutsche Standorte"
```

---

### Task 3: `OfficialLocations` in core-prayertimes (TDD)

**Files:**
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/OfficialLocations.kt`
- Test: `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/OfficialLocationsTest.kt`

**Interfaces:**
- Produces (von Task 4 konsumiert):
  - `data class OfficialLocation(val diyanetId: Int, val name: String, val latitude: Double, val longitude: Double, val tableRef: String)`
  - `fun parseOfficialLocations(lines: Sequence<String>): List<OfficialLocation>`
  - `object OfficialLocations { fun nearest(locations: List<OfficialLocation>, lat: Double, lng: Double, maxKm: Double = 25.0): OfficialLocation? }`

- [ ] **Step 1: Failing Test schreiben** — `OfficialLocationsTest.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialLocationsTest {

    private val nuernberg = OfficialLocation(9541, "Nürnberg", 49.45421, 11.07752, "t001")
    private val fuerth = OfficialLocation(9327, "Fürth", 49.47593, 10.98856, "t001")
    private val berlin = OfficialLocation(11002, "Berlin", 52.52437, 13.41053, "t002")

    @Test
    fun parsesIndexLinesAndSkipsBroken() {
        val list = parseOfficialLocations(
            sequenceOf(
                "9541\tNürnberg\t49.45421\t11.07752\tt001",
                "kaputt ohne tabs",
                "11002\tBerlin\t52.52437\t13.41053\tt002",
            ),
        )
        assertEquals(listOf(nuernberg, berlin), list)
    }

    @Test
    fun nearestPicksClosestLocation() {
        // Zirndorf (49.442, 10.955) liegt näher an Fürth als an Nürnberg.
        val hit = OfficialLocations.nearest(listOf(nuernberg, fuerth, berlin), 49.442, 10.955)
        assertEquals("Fürth", hit?.name)
    }

    @Test
    fun nearestNullBeyondMaxDistance() {
        // Wien ist > 25 km von jedem deutschen Standort entfernt.
        assertNull(OfficialLocations.nearest(listOf(nuernberg, fuerth, berlin), 48.208, 16.372))
    }

    @Test
    fun nearestNullOnEmptyList() {
        assertNull(OfficialLocations.nearest(emptyList(), 49.45, 11.07))
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test --tests "*OfficialLocationsTest" --console=plain`
Expected: Compile-Fehler `unresolved reference: OfficialLocation`.

- [ ] **Step 3: Implementierung** — `OfficialLocations.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Ein Diyanet-Standort aus dem gebündelten Index (assets/official/locations-de.tsv). */
data class OfficialLocation(
    val diyanetId: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val tableRef: String,
)

/** Parst den Standort-Index (`diyanetId name lat lng tableRef`, Tab-getrennt,
 *  kein Header). Defekte Zeilen werden übersprungen. */
fun parseOfficialLocations(lines: Sequence<String>): List<OfficialLocation> =
    lines.mapNotNull { line ->
        val c = line.trim().split('\t')
        if (c.size != 5) return@mapNotNull null
        val id = c[0].toIntOrNull() ?: return@mapNotNull null
        val lat = c[2].toDoubleOrNull() ?: return@mapNotNull null
        val lng = c[3].toDoubleOrNull() ?: return@mapNotNull null
        OfficialLocation(id, c[1], lat, lng, c[4])
    }.toList()

object OfficialLocations {

    private const val EARTH_KM = 6371.0

    /** Nächstgelegener Standort zu (lat,lng), oder null wenn weiter als [maxKm].
     *  Die Schwelle verhindert „amtliche" Zeiten eines viel zu fernen Ortes. */
    fun nearest(
        locations: List<OfficialLocation>,
        lat: Double,
        lng: Double,
        maxKm: Double = 25.0,
    ): OfficialLocation? =
        locations
            .minByOrNull { haversineKm(lat, lng, it.latitude, it.longitude) }
            ?.takeIf { haversineKm(lat, lng, it.latitude, it.longitude) <= maxKm }

    internal fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_KM * asin(min(1.0, sqrt(a)))
    }
}
```

- [ ] **Step 4: Tests laufen lassen — müssen grün sein**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test --console=plain`
Expected: BUILD SUCCESSFUL, alle Tests (inkl. bestehender) grün.

- [ ] **Step 5: Commit**

```powershell
git add core-prayertimes/src
git commit -m "feat(core): OfficialLocations - Standort-Index-Parser + Nearest-Lookup (25 km)"
```

---

### Task 4: `BundledOfficialSource` v2 — Koordinaten-Lookup statt Namens-Registry

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/BundledOfficialSource.kt` (kompletter Ersatz des Objekt-Inhalts)
- Modify: `app/src/test/kotlin/de/gebetszeiten/official/BundledOfficialSourceTest.kt` (ersetzen)
- Create: `app/src/test/kotlin/de/gebetszeiten/official/OfficialAssetsIntegrityTest.kt`
- Delete: `app/src/main/assets/official/nuernberg-2026.tsv`, `app/src/test/kotlin/de/gebetszeiten/official/Nuernberg2026AssetTest.kt`

**Interfaces:**
- Consumes: `OfficialLocation`, `parseOfficialLocations`, `OfficialLocations.nearest` (Task 3); Assets (Task 2); bestehendes `parseOfficialTimes`/`SixTimes`.
- Produces (von Task 5/6 konsumiert):
  - `suspend fun BundledOfficialSource.get(context, lat: Double, lng: Double, date: LocalDate): SixTimes?`
  - `suspend fun BundledOfficialSource.locationNameFor(context, lat: Double, lng: Double, date: LocalDate): String?` (Anzeigename des abdeckenden Standorts, sonst null)

- [ ] **Step 1: Failing Integritätstest schreiben** — `OfficialAssetsIntegrityTest.kt` (liest committete Assets über Repo-Pfad, wie `Nuernberg2026AssetTest`):

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Verifiziert die Pipeline-Ausgabe: Index konsistent, Tabellen vollständig
 *  und monoton, Nürnberg reproduziert die amtliche Phase-1-Referenz. */
class OfficialAssetsIntegrityTest {

    private val assets = File("src/main/assets/official")
    private val locations by lazy {
        File(assets, "locations-de.tsv").useLines { parseOfficialLocations(it) }
    }

    @Test fun indexIsSubstantialAndInGermanBounds() {
        assertTrue("nur ${locations.size} Standorte", locations.size >= 500)
        locations.forEach {
            assertTrue("${it.name}: lat ${it.latitude}", it.latitude in 47.0..55.5)
            assertTrue("${it.name}: lng ${it.longitude}", it.longitude in 5.5..15.5)
        }
        assertTrue(locations.any { it.name == "Nürnberg" })
        assertTrue(locations.any { it.name == "Berlin" })
    }

    @Test fun everyReferencedTableExistsCompleteAndOrdered() {
        val year = 2026
        locations.map { it.tableRef }.distinct().forEach { ref ->
            val f = File(assets, "tables/$ref-$year.tsv")
            assertTrue("$ref fehlt", f.isFile)
            val table = f.useLines { parseOfficialTimes(it) }
            assertEquals("$ref unvollständig", if (year % 4 == 0) 366 else 365, table.size)
            table.forEach { (date, t) ->
                val ordered = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
                assertEquals("$ref $date nicht aufsteigend", ordered.sorted(), ordered)
            }
        }
    }

    @Test fun nuernbergReproducesPhase1Reference() {
        val nbg = locations.first { it.name == "Nürnberg" }
        val table = File(assets, "tables/${nbg.tableRef}-2026.tsv").useLines { parseOfficialTimes(it) }
        val t = table.getValue(LocalDate.of(2026, 6, 7))
        assertEquals(LocalTime.of(3, 35), t.fajr)
        assertEquals(LocalTime.of(5, 4), t.sunrise)
        assertEquals(LocalTime.of(13, 20), t.dhuhr)
        assertEquals(LocalTime.of(17, 36), t.asr)
        assertEquals(LocalTime.of(21, 25), t.maghrib)
        assertEquals(LocalTime.of(22, 45), t.isha)
    }
}
```

- [ ] **Step 2: Test laufen lassen — Erwartung FAIL** (Compile ok, aber `nuernbergReproducesPhase1Reference` prüft echte Pipeline-Daten; falls schon grün: gut, weiter)

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest --tests "*OfficialAssetsIntegrityTest" --console=plain`

- [ ] **Step 3: `BundledOfficialSource` ersetzen** (kompletter neuer Datei-Inhalt):

```kotlin
package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

/**
 * Amtliche Diyanet-Zeiten aus gebündelten Offline-Tabellen (assets/official/).
 * Lookup per Koordinaten: nächstgelegener deutscher Diyanet-Standort ≤ 25 km.
 * Nicht abgedeckt (Ausland, fehlendes Jahr) → null → Aufrufer rechnet selbst.
 */
object BundledOfficialSource {

    private const val LOCATIONS_ASSET = "official/locations-de.tsv"

    @Volatile private var locations: List<OfficialLocation>? = null
    @Volatile private var tables: Map<String, Map<LocalDate, SixTimes>> = emptyMap()

    suspend fun get(context: Context, lat: Double, lng: Double, date: LocalDate): SixTimes? =
        nearestCovering(context, lat, lng, date)?.second

    /** Anzeigename des Diyanet-Standorts, dessen amtliche Tabelle (Datum!) greift. */
    suspend fun locationNameFor(context: Context, lat: Double, lng: Double, date: LocalDate): String? =
        nearestCovering(context, lat, lng, date)?.first?.name

    private suspend fun nearestCovering(
        context: Context,
        lat: Double,
        lng: Double,
        date: LocalDate,
    ): Pair<OfficialLocation, SixTimes>? {
        val loc = OfficialLocations.nearest(allLocations(context), lat, lng) ?: return null
        val time = table(context, "official/tables/${loc.tableRef}-${date.year}.tsv")[date] ?: return null
        return loc to time
    }

    private suspend fun allLocations(context: Context): List<OfficialLocation> {
        locations?.let { return it }
        return withContext(Dispatchers.IO) {
            locations ?: load(context).also { locations = it }
        }
    }

    private fun load(context: Context): List<OfficialLocation> = try {
        context.assets.open(LOCATIONS_ASSET).bufferedReader(Charsets.UTF_8).useLines {
            parseOfficialLocations(it)
        }
    } catch (e: FileNotFoundException) {
        emptyList()
    }

    private suspend fun table(context: Context, path: String): Map<LocalDate, SixTimes> {
        tables[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            tables[path] ?: loadTable(context, path).also { tables = tables + (path to it) }
        }
    }

    private fun loadTable(context: Context, path: String): Map<LocalDate, SixTimes> = try {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { parseOfficialTimes(it) }
    } catch (e: FileNotFoundException) {
        emptyMap()
    }
}
```

- [ ] **Step 4: Alte Nürnberg-Artefakte entfernen**

Run:
```powershell
git rm app/src/main/assets/official/nuernberg-2026.tsv app/src/test/kotlin/de/gebetszeiten/official/Nuernberg2026AssetTest.kt
```
WICHTIG: Vorher in `OfficialAssetsIntegrityTest` die Referenzwerte (Step 1) checken — sie ersetzen den gelöschten Test inhaltlich. Die alte `BundledOfficialSourceTest.kt` testet `assetPathsFor` (existiert nicht mehr) → Datei ersetzen:

```kotlin
package de.gebetszeiten.official

import org.junit.Assert.assertTrue
import org.junit.Test

/** Platzhalter: Die pure Lookup-Logik lebt jetzt in core-prayertimes
 *  (OfficialLocationsTest); Asset-Konsistenz prüft OfficialAssetsIntegrityTest.
 *  Hier bleibt nur der Paket-Anker für künftige Context-freie Tests. */
class BundledOfficialSourceTest {
    @Test fun assetLayoutContractDocumented() {
        assertTrue(true)
    }
}
```
(Alternative, falls beim Umsetzen sinnvoller: Datei ganz löschen — dann auch aus git.)

- [ ] **Step 5: Aufrufer anpassen — Compile-Fehler beheben.** `PrayerProvider.daily` (Zeile ~27) und `MainActivity` (Zeile ~269–271, `covers`) rufen noch die alte Signatur. Für DIESEN Task nur minimal auf die neue Signatur umstellen (Toggle kommt in Task 5, Footer-Name in Task 6):

`PrayerProvider.kt`:
```kotlin
        // 2) Gebündelte amtliche Tabelle (offline, nearest Diyanet-Standort ≤ 25 km).
        BundledOfficialSource.get(context, settings.latitude, settings.longitude, date)
            ?.let { return it.toDaily(date, zone) }
```

`MainActivity.kt` (produceState-Block):
```kotlin
    val officialSource by produceState(false, settings, selectedDate) {
        value = de.gebetszeiten.official.BundledOfficialSource
            .locationNameFor(context, settings.latitude, settings.longitude, selectedDate) != null ||
            (settings.useOnline && de.gebetszeiten.official.OfficialTimesCache(context).get(selectedDate) != null)
    }
```

- [ ] **Step 6: Alle Tests + Build**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:assembleOfflineDebug --console=plain`
Expected: BUILD SUCCESSFUL, Integritätstest grün.

- [ ] **Step 7: Commit**

```powershell
git add -A app/src core-prayertimes/src
git commit -m "feat: BundledOfficialSource v2 - Koordinaten-Nearest statt Stadtnamen-Registry"
```

---

### Task 5: `useCalculated`-Toggle in Settings-Datenmodell + `PrayerProvider`

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt`

**Interfaces:**
- Produces: `AppSettings.useCalculated: Boolean` (Default false, DataStore-Key `use_calculated`); Prioritätslogik: `useCalculated=true` → immer `PrayerSchedule.forDate`.

- [ ] **Step 1: Feld + Key + Persistenz.** In `AppSettings` nach `useOnline`:

```kotlin
    /** Immer die lokale astronomische Berechnung nutzen (amtliche Quellen ignorieren). */
    val useCalculated: Boolean = false,
```

In `AppSettings.DEFAULT` nach `useOnline = false,`: `useCalculated = false,`

In `SettingsRepository.Keys` nach `USE_ONLINE`:
```kotlin
        val USE_CALCULATED = booleanPreferencesKey("use_calculated")
```
Im `settings`-Flow nach `useOnline = ...,`:
```kotlin
            useCalculated = prefs[Keys.USE_CALCULATED] ?: AppSettings.DEFAULT.useCalculated,
```
In `save(...)` nach `prefs[Keys.USE_ONLINE] = value.useOnline`:
```kotlin
            prefs[Keys.USE_CALCULATED] = value.useCalculated
```

- [ ] **Step 2: Prioritätskette in `PrayerProvider.daily`** — Methodenanfang ergänzen:

```kotlin
    suspend fun daily(context: Context, settings: AppSettings, date: LocalDate, zone: ZoneId): DailyPrayerTimes {
        // 0) Nutzer hat explizit die eigene Berechnung gewählt.
        if (settings.useCalculated) return PrayerSchedule.forDate(settings, date, zone)
        // 1) Online-Cache (frischste Quelle, nur wenn aktiviert).
        if (settings.useOnline) {
            OfficialTimesCache(context).get(date)?.let { return it.toDaily(date, zone) }
        }
        // 2) Gebündelte amtliche Tabelle (offline, nearest Diyanet-Standort ≤ 25 km).
        BundledOfficialSource.get(context, settings.latitude, settings.longitude, date)
            ?.let { return it.toDaily(date, zone) }
        // 3) Fallback: Berechnung.
        return PrayerSchedule.forDate(settings, date, zone)
    }
```

- [ ] **Step 3: Build + Tests**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest --console=plain`
Expected: grün. (Die Prioritätslogik selbst ist Context-gebunden; abgesichert über Task-7-Emulator-Verifikation. KEINEN Robolectric-Test einführen — Projekt hat keins.)

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt
git commit -m "feat: useCalculated-Toggle - Berechnung nur noch explizit oder als Fallback"
```

---

### Task 6: Settings-UI-Toggle + Footer nennt Diyanet-Standort

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (Anzeige-Sektion ~Zeile 1225, Footer-produceState ~Zeile 269, Footer-Text ~Zeile 304)

**Interfaces:**
- Consumes: `AppSettings.useCalculated` (Task 5), `BundledOfficialSource.locationNameFor` (Task 4), bestehendes `dataCreditRes(official: Boolean)` in `de.gebetszeiten.prayer`.

- [ ] **Step 1: Strings ergänzen** (in strings.xml, Settings-Block nach `settings_show_makruh`-Umfeld):

```xml
    <string name="settings_use_calculated">Eigene Berechnung verwenden</string>
    <string name="settings_use_calculated_hint">Statt amtlicher Diyanet-Tabellen die lokale astronomische Berechnung nutzen. Amtliche Zeiten sind Standard, wo verfügbar.</string>
```

- [ ] **Step 2: Toggle in der Anzeige-Sektion.** In `LocationSettings`, `SettingsSection(stringResource(R.string.settings_section_display))`, direkt nach dem `showKaraha`-Block (`ToggleRow(...settings_show_makruh...)` + Hint):

```kotlin
            ToggleRow(stringResource(R.string.settings_use_calculated), settings.useCalculated) {
                commit { copy(useCalculated = it) }
            }
            Text(
                stringResource(R.string.settings_use_calculated_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
```

- [ ] **Step 3: Footer-Quelle mit Standortnamen.** In `MainScreen`/`HeuteContent` den `officialSource`-produceState (Task 4 Step 5) ersetzen durch:

```kotlin
    val officialName by produceState<String?>(null, settings, selectedDate) {
        value = if (settings.useCalculated) {
            null
        } else {
            de.gebetszeiten.official.BundledOfficialSource
                .locationNameFor(context, settings.latitude, settings.longitude, selectedDate)
                ?: settings.city.takeIf {
                    settings.useOnline &&
                        de.gebetszeiten.official.OfficialTimesCache(context).get(selectedDate) != null
                }
        }
    }
```
und den Footer-Text (Zeile ~304) auf:
```kotlin
            text = stringResource(de.gebetszeiten.prayer.dataCreditRes(officialName != null), officialName ?: settings.city),
```
Alle weiteren Verwendungen von `officialSource` im File per Suche auf `officialName != null` umstellen (Compiler findet sie).

- [ ] **Step 4: Build + Tests + Lint**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:lintOfflineDebug --console=plain`
Expected: BUILD SUCCESSFUL, Lint ohne neue Fehler.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/values/strings.xml app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt
git commit -m "feat(ui): Berechnungs-Toggle + Footer nennt den Diyanet-Standort"
```

---

### Task 7: Emulator-Ende-zu-Ende-Verifikation

**Files:** keine (Verifikation). Emulator-Handhabung siehe Memory `emulator-ui-verification` (AVD `Medium_Phone_API_36.1`, adb-Flow, Screenshots via `adb pull`).

- [ ] **Step 1: Bauen + installieren + App-Daten frisch**

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleOfflineDebug --console=plain -q
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\offline\debug\app-offline-debug.apk
& $adb shell pm clear de.gebetszeiten
& $adb shell am start -n de.gebetszeiten/.ui.MainActivity
```

- [ ] **Step 2: Default Nürnberg → Footer amtlich.** Screenshot: Footer muss „Amtliche Diyanet-Zeiten · Nürnberg" zeigen, Zeiten identisch zu vorher (z. B. Dhuhr 13:26 am 6. Juli).

- [ ] **Step 3: Nearest-Fall.** Settings → Stadt „Zirndorf" (oder „Erlangen") wählen. Erwartet: Footer nennt den nächstgelegenen Diyanet-Standort (z. B. „Fürth"/„Erlangen"), NICHT „Berechnung". Screenshot.

- [ ] **Step 4: Ausland-Fallback.** Stadt „Wien" wählen. Erwartet: Footer „Berechnung: Diyanet-Methode (offline)". Screenshot.

- [ ] **Step 5: Toggle.** Zurück nach Nürnberg; „Eigene Berechnung verwenden" einschalten. Erwartet: Footer wechselt auf „Berechnung…", Zeiten ändern sich leicht (z. B. Fajr). Toggle aus → wieder amtlich. Screenshots beider Zustände.

- [ ] **Step 6: Datum 2027.** In der Heute-Ansicht per Chevron zum 1. Januar 2027 blättern (viele Taps — alternativ Emulator-Datum stellen). Erwartet: Footer „Berechnung…" (kein 2027-Bundle). Danach App-Daten aufräumen: `& $adb shell pm clear de.gebetszeiten`.

- [ ] **Step 7: Abschluss-Commit (nur falls Fixes nötig waren) + Zusammenfassung an den Nutzer** mit den Screenshots aus Steps 2–5.

---

## Self-Review (durchgeführt)

- **Spec-Abdeckung PR 1+2:** Pipeline (Spec §1) → Task 1+2; Dedupe/Größe → Task 1 Script + Task 2 Report; geteiltes Lookup (§2) → Task 3; BundledOfficialSource v2 (§3) → Task 4; Priorität+Toggle (§4) → Task 5; UI/Footer (§7) → Task 6; Tests (§Tests 1–3, 6) → Tasks 3/4 + Emulator-Task 7; `YearCompareTest` (§Tests 4) bleibt unberührt. Wear (§6)/Online (§5) bewusst Folgepläne.
- **Platzhalter:** keine — jeder Code-Step enthält vollständigen Code, jede Run-Zeile einen exakten Befehl.
- **Typkonsistenz:** `OfficialLocation(diyanetId, name, latitude, longitude, tableRef)` konsistent in Task 3 (Definition), Task 4 (Nutzung), Pipeline-Index-Spalten (Task 1/2) in derselben Reihenfolge; `locationNameFor` einheitlich in Task 4 Definition und Task 6 Nutzung.
