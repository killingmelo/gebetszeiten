# Quellenbewusste Standortwahl + Quellen-Rückmeldung — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Amtliche Diyanet-Zeiten für jeden Ort, für den Diyanet sie hat — über Koordinaten aufgelöst statt über Namensgleichheit — und eine sichtbare, ehrliche Rückmeldung über die aktive Zeitenquelle.

**Architecture:** Ein gebündelter weltweiter Diyanet-Standortindex (~10.000 Einträge mit Koordinaten) ersetzt die fragile Namenssuche als Primärweg der ID-Auflösung. Die Ortssuche zeigt pro Treffer lokal berechnet, welche Quelle er liefert; die aktive Quelle steht permanent im Footer, ihre Details plus Zeitstempel und Fehlergrund in einer Statuszeile im Einstellungs-Sheet.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, DataStore Preferences, Gradle (Flavors `offline`/`online`), JUnit4, Python 3 (Daten-Pipeline).

**Spec:** [`docs/superpowers/specs/2026-08-14-standortwahl-quellenbewusst-design.md`](../specs/2026-08-14-standortwahl-quellenbewusst-design.md)

## Global Constraints

- **Sprache:** Alle Nutzertexte deutsch. Nur `app/src/main/res/values/strings.xml` (es gibt kein `values-en`); Kommentare und Commit-Messages deutsch, ASCII in Commit-Messages (bestehende Konvention).
- **Flavor-Trennung:** Der offline-Flavor darf **keinen** Netzwerkcode und **kein** Diyanet-ID-Asset bekommen. Netzabhängiges liegt in `app/src/online/`, mit einem No-op-Zwilling in `app/src/offline/`.
- **Distanzschwelle:** `maxKm = 25.0` — dieselbe Konvention wie `OfficialLocations.nearest`.
- **Asset-Pfad des neuen Index:** `app/src/online/assets/official/locations-world.tsv` (NICHT `shared-assets/`, das hängt an `main` und damit an beiden Flavors).
- **TSV-Format des Index:** `diyanetId \t name \t province \t iso2 \t lat \t lng`, tab-getrennt, kein Header, UTF-8, LF.
- **Provinzzentrum-Regel:** Fallback auf das Provinzzentrum NUR wenn `normalize(name) == normalize(province)`. Sonst Eintrag verwerfen. Eine falsche Koordinate ist schlimmer als ein fehlender Eintrag.
- **Fetch wirft nie:** `OfficialTimesFetcher.fetch` bleibt fehlertolerant (leeres Ergebnis → Berechnung). `CancellationException` wird weiterhin durchgereicht, nicht geschluckt.
- **Testkommandos** (PowerShell, Repo-Wurzel):
  - `.\gradlew.bat :core-prayertimes:test`
  - `.\gradlew.bat :app:testOnlineDebugUnitTest`
  - `.\gradlew.bat :app:testOfflineDebugUnitTest`

---

### Task 1: `DiyanetPlace` — Modell, Parser, Nächster-Treffer

**Files:**
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DiyanetPlaces.kt`
- Test: `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DiyanetPlacesTest.kt`

**Interfaces:**
- Consumes: `haversineKm` aus `OfficialLocations.kt` — `internal`, gleiches Modul und gleiches Paket, daher direkt aufrufbar.
- Produces:
  - `data class DiyanetPlace(diyanetId: Int, name: String, province: String, countryCode: String, latitude: Double, longitude: Double)`
  - `fun parseDiyanetPlaces(lines: Sequence<String>): List<DiyanetPlace>`
  - `object DiyanetPlaces { fun nearest(places: List<DiyanetPlace>, lat: Double, lng: Double, maxKm: Double = 25.0): DiyanetPlace?; fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double }`
  - `fun DiyanetPlace.displayName(): String`

**Abweichung von der Spec (bewusst, YAGNI):** Die Spec nennt zusätzlich `DiyanetPlaces.search(query, limit)`. Die wird **nicht** gebaut — der Nutzer sucht GeoNames-Orte, nicht Diyanet-Bezirke; für die Badges genügt `nearest`. Kein Aufrufer, keine Funktion.

- [ ] **Step 1: Write the failing test**

`core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DiyanetPlacesTest.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyanetPlacesTest {

    // Echte Werte aus der Datenpruefung: Diyanet-Standort SAKARYA liegt auf
    // Adapazari, Serdivan 2,1 km entfernt.
    private val sakarya = DiyanetPlace(9807, "SAKARYA", "SAKARYA", "TR", 40.78056, 30.40333)
    private val akyazi = DiyanetPlace(9800, "AKYAZI", "SAKARYA", "TR", 40.685, 30.62222)
    private val places = listOf(sakarya, akyazi)

    @Test fun `parst gueltige Zeilen`() {
        val parsed = parseDiyanetPlaces(
            sequenceOf(
                "9807\tSAKARYA\tSAKARYA\tTR\t40.78056\t30.40333",
                "9800\tAKYAZI\tSAKARYA\tTR\t40.685\t30.62222",
            ),
        )
        assertEquals(listOf(sakarya, akyazi), parsed)
    }

    @Test fun `ueberspringt defekte Zeilen statt zu werfen`() {
        val parsed = parseDiyanetPlaces(
            sequenceOf(
                "",
                "zu\tkurz",
                "keineZahl\tX\tY\tTR\t1.0\t2.0",
                "9807\tSAKARYA\tSAKARYA\tTR\tkeineKoordinate\t30.4",
                "9807\tSAKARYA\tSAKARYA\tTR\t40.78056\t30.40333",
            ),
        )
        assertEquals(listOf(sakarya), parsed)
    }

    @Test fun `nearest findet Serdivan zu Sakarya`() {
        // Serdivan laut cities.tsv
        val hit = DiyanetPlaces.nearest(places, 40.77376, 30.38006)
        assertEquals(sakarya, hit)
    }

    @Test fun `distanceKm Serdivan nach Sakarya rund 2 km`() {
        val km = DiyanetPlaces.distanceKm(sakarya, 40.77376, 30.38006)
        assertTrue("gemessen $km km", km in 1.5..2.5)
    }

    @Test fun `nearest liefert null jenseits der Schwelle`() {
        // Wien: weit weg von beiden
        assertNull(DiyanetPlaces.nearest(places, 48.2082, 16.3738))
    }

    @Test fun `nearest respektiert eine engere Schwelle`() {
        // Serdivan liegt 2,1 km entfernt: bei 1 km kein Treffer, bei 3 km schon.
        assertNull(DiyanetPlaces.nearest(places, 40.77376, 30.38006, maxKm = 1.0))
        assertEquals(sakarya, DiyanetPlaces.nearest(places, 40.77376, 30.38006, maxKm = 3.0))
    }

    @Test fun `displayName macht aus Schreiaufschrift lesbare Namen`() {
        assertEquals("Sakarya", sakarya.displayName())
        assertEquals("İstanbul", DiyanetPlace(9541, "İSTANBUL", "İSTANBUL", "TR", 41.0, 29.0).displayName())
        assertEquals("Mustafakemalpaşa", DiyanetPlace(1, "MUSTAFAKEMALPAŞA", "BURSA", "TR", 40.0, 28.0).displayName())
    }

    @Test fun `displayName verstuemmelt nicht-tuerkische Namen nicht`() {
        // Das tuerkische Locale bildet ASCII-I auf das punktlose i ab: mit
        // pauschalem tr-Locale wuerde "BERLIN" zu "Berlın". Der Index deckt
        // 205 Laender ab, der Fall ist also erreichbar.
        assertEquals("Berlin", DiyanetPlace(2, "BERLIN", "BERLIN", "DE", 52.5, 13.4).displayName())
        assertEquals("Mainz", DiyanetPlace(3, "MAINZ", "RHEINLAND-PFALZ", "DE", 50.0, 8.27).displayName())
    }

    @Test fun `leere Liste ist kein Absturz`() {
        assertNull(DiyanetPlaces.nearest(emptyList(), 40.0, 30.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core-prayertimes:test --tests "*DiyanetPlacesTest*"`
Expected: FAIL — Kompilierfehler, `DiyanetPlace`/`parseDiyanetPlaces`/`DiyanetPlaces` sind unbekannt.

- [ ] **Step 3: Write minimal implementation**

`core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DiyanetPlaces.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import java.util.Locale

/**
 * Ein Diyanet-Standort aus dem weltweiten Index
 * (`app/src/online/assets/official/locations-world.tsv`).
 *
 * Abgrenzung zu [OfficialLocation]: jenes beantwortet „welche gebündelte
 * Jahrestabelle gilt" (Feld `tableRef`, auch offline und auf der Uhr), dieses
 * „welche Diyanet-ID wird abgerufen" plus die Anzeigedaten (Provinz, Land).
 */
data class DiyanetPlace(
    val diyanetId: Int,
    /** Bezirksname wie Diyanet ihn führt, in Großbuchstaben („SAKARYA"). */
    val name: String,
    val province: String,
    /** ISO2, aus der Geokodierung der Pipeline („TR"). */
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
)

/** Türkische Groß-/Kleinschreibung: „İSTANBUL" → „İstanbul", nicht „Istanbul". */
private val TURKISH = Locale.forLanguageTag("tr")

/** Diyanet schreibt alles groß; für die UI lesbar machen. */
fun DiyanetPlace.displayName(): String =
    name.lowercase(TURKISH).replaceFirstChar { it.titlecase(TURKISH) }

/** Parst den Index (`id name province iso2 lat lng`, Tab-getrennt, kein
 *  Header). Defekte Zeilen werden übersprungen — ein Tippfehler im Asset darf
 *  nicht die ganze Ortswahl lahmlegen. */
fun parseDiyanetPlaces(lines: Sequence<String>): List<DiyanetPlace> =
    lines.mapNotNull { line ->
        val c = line.trim().split('\t')
        if (c.size != 6) return@mapNotNull null
        val id = c[0].toIntOrNull() ?: return@mapNotNull null
        val lat = c[4].toDoubleOrNull() ?: return@mapNotNull null
        val lng = c[5].toDoubleOrNull() ?: return@mapNotNull null
        DiyanetPlace(id, c[1], c[2], c[3], lat, lng)
    }.toList()

object DiyanetPlaces {

    /** Nächstgelegener Diyanet-Standort, oder null jenseits von [maxKm].
     *  Die Schwelle verhindert „amtliche" Zeiten eines viel zu fernen Orts. */
    fun nearest(
        places: List<DiyanetPlace>,
        lat: Double,
        lng: Double,
        maxKm: Double = 25.0,
    ): DiyanetPlace? =
        places
            .minByOrNull { distanceKm(it, lat, lng) }
            ?.takeIf { distanceKm(it, lat, lng) <= maxKm }

    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double =
        OfficialLocations.haversineKm(lat, lng, place.latitude, place.longitude)
}
```

Falls `haversineKm` nicht auflösbar ist, weil es `private` statt `internal` ist: in `OfficialLocations.kt` von `internal fun haversineKm` **nicht** auf `public` ändern — es ist bereits `internal`, gleiches Modul genügt.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :core-prayertimes:test --tests "*DiyanetPlacesTest*"`
Expected: PASS (8 Tests)

- [ ] **Step 5: Commit**

```bash
git add core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DiyanetPlaces.kt core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DiyanetPlacesTest.kt
git commit -m "feat(core): DiyanetPlace-Index-Modell mit Naechster-Treffer-Suche"
```

---

### Task 2: Index-Pipeline und Asset

**Files:**
- Create: `tools/diyanet-index/build_index.py`
- Create: `tools/diyanet-index/README.md`
- Create (Pipeline-Ausgabe): `app/src/online/assets/official/locations-world.tsv`
- Modify: `.gitignore` (Cache-Verzeichnis des neuen Tools)
- Test: `app/src/testOnline/kotlin/de/gebetszeiten/official/WorldIndexIntegrityTest.kt`

**Interfaces:**
- Consumes: `normalize`, `fetch` aus `tools/diyanet-fetch/fetch_diyanet.py`; `parseDiyanetPlaces` aus Task 1.
- Produces: das Asset im TSV-Format der Global Constraints.

Der Test liegt in `testOnline`, weil das Asset im online-Flavor liegt. Pfade sind relativ zum `app/`-Modulverzeichnis (wie im bestehenden `OfficialAssetsIntegrityTest`, der `File("../shared-assets/official")` nutzt).

- [ ] **Step 1: Write the failing test**

`app/src/testOnline/kotlin/de/gebetszeiten/official/WorldIndexIntegrityTest.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Wächter über die Pipeline-Ausgabe. Der Serdivan-Test ist der
 *  Regressionsschutz für den Bug, der diesen Umbau ausgelöst hat. */
class WorldIndexIntegrityTest {

    private val places by lazy {
        File("src/online/assets/official/locations-world.tsv")
            .useLines { parseDiyanetPlaces(it) }
    }

    @Test fun indexIstSubstantiell() {
        assertTrue("nur ${places.size} Standorte", places.size >= 6000)
    }

    @Test fun keineDoppeltenIds() {
        val dupes = places.groupBy { it.diyanetId }.filter { it.value.size > 1 }.keys
        assertEquals("doppelte IDs: $dupes", emptySet<Int>(), dupes)
    }

    @Test fun koordinatenUndFelderPlausibel() {
        places.forEach {
            assertTrue("${it.name}: lat ${it.latitude}", it.latitude in -90.0..90.0)
            assertTrue("${it.name}: lng ${it.longitude}", it.longitude in -180.0..180.0)
            assertTrue("${it.name}: id ${it.diyanetId}", it.diyanetId > 0)
            assertEquals("${it.name}: Ländercode", 2, it.countryCode.length)
            assertTrue("leerer Name bei id ${it.diyanetId}", it.name.isNotBlank())
        }
    }

    @Test fun serdivanFindetSakarya() {
        // DER Regressionstest: Serdivan (40.77376/30.38006) hat keinen eigenen
        // Diyanet-Eintrag und muss ueber Adapazari (id 9807) aufgeloest werden.
        val hit = DiyanetPlaces.nearest(places, 40.77376, 30.38006)
        assertNotNull("Serdivan findet keinen Diyanet-Standort", hit)
        assertEquals(9807, hit!!.diyanetId)
        assertTrue("zu weit: ${DiyanetPlaces.distanceKm(hit, 40.77376, 30.38006)} km",
            DiyanetPlaces.distanceKm(hit, 40.77376, 30.38006) < 5.0)
    }

    @Test fun tuerkeiUndDeutschlandBreitAbgedeckt() {
        assertTrue("TR zu duenn", places.count { it.countryCode == "TR" } >= 800)
        assertTrue("DE zu duenn", places.count { it.countryCode == "DE" } >= 900)
    }

    @Test fun grossstaedteVorhanden() {
        // Schreibweisen wortgetreu wie Diyanet sie fuehrt (live geprueft):
        // "NURNBERG" OHNE Umlaut, "İSTANBUL" MIT gepunktetem I. `ignoreCase`
        // gleicht Ü und U nicht aus — hier keine Schreibweise raten.
        listOf("TR" to "İSTANBUL", "TR" to "SAKARYA", "DE" to "NURNBERG", "DE" to "BERLIN")
            .forEach { (cc, name) ->
                assertTrue("$cc/$name fehlt",
                    places.any { it.countryCode == cc && it.name.equals(name, ignoreCase = true) })
            }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "*WorldIndexIntegrityTest*"`
Expected: FAIL — das Asset existiert nicht, `useLines` wirft `FileNotFoundException`.

- [ ] **Step 3: Write the pipeline**

`tools/diyanet-index/build_index.py`:

```python
#!/usr/bin/env python3
"""Baut den weltweiten Diyanet-Standortindex (id, Name, Provinz, ISO2, Koordinaten).

Ablauf: Laenderliste vom Community-Proxy -> pro Land die Standortliste
(Roh-JSON gecacht/resumierbar) -> Laendervotum ISO2 -> dreistufige
Geokodierung gegen cities.tsv -> TSV-Asset. Einmal jaehrlich zusammen mit
fetch_diyanet.py ausfuehren (siehe README).

Wichtig: Stufe 3 (Provinzzentrum) greift NUR wenn Name == Provinz. Ohne diese
Bedingung erbte jeder nicht auflösbare Kleinort die Koordinaten der groessten
Stadt seiner admin1-Region -- in Deutschland waeren das hunderte Kilometer.
"""
import argparse
import collections
import json
import re
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "diyanet-fetch"))
from fetch_diyanet import fetch, normalize  # noqa: E402  (Pfad-Setup muss vorher laufen)

ASSETS = REPO / "app" / "src" / "main" / "assets"
OUT = REPO / "app" / "src" / "online" / "assets" / "official" / "locations-world.tsv"
CACHE = Path(__file__).resolve().parent / "cache"

BASE = "https://prayertimes.api.abdus.dev/api/diyanet"


def variants(name: str) -> list[str]:
    """Namenskandidaten: roh, ohne '(x)'-Suffix, ohne Leerzeichen."""
    base = re.sub(r"\s*\([^)]*\)\s*$", "", name).strip()
    return [base, base.replace(" ", "")]


def load_countries() -> list[str]:
    CACHE.mkdir(exist_ok=True)
    f = CACHE / "countries.json"
    if not f.is_file():
        f.write_bytes(fetch(f"{BASE}/countries"))
        time.sleep(1)
    return json.loads(f.read_text(encoding="utf-8"))


def load_locations(country: str) -> list[dict]:
    """Standorte eines Landes; Roh-JSON gecacht -> Abbruch/Neustart ist billig."""
    CACHE.mkdir(exist_ok=True)
    safe = re.sub(r"[^A-Za-z0-9]+", "_", country)
    f = CACHE / f"loc_{safe}.json"
    if not f.is_file():
        from urllib.parse import quote
        f.write_bytes(fetch(f"{BASE}/locations?country={quote(country)}"))
        time.sleep(1)
    rows = json.loads(f.read_text(encoding="utf-8"))
    out = []
    for r in rows:
        district = (r.get("region") or r.get("city") or "").strip()
        if district and r.get("id"):
            out.append({
                "id": int(r["id"]),
                "name": district,
                "province": (r.get("city") or district).strip(),
            })
    return out


def load_city_index() -> tuple[dict, dict]:
    """(name_index, region_index) ueber ALLE Laender.

    name_index:   (iso2, normName) -> (lat, lng)
    region_index: (iso2, normAdmin1) -> [(lat, lng), ...] in Asset-Reihenfolge
                  (cities.tsv ist populationssortiert -> [0] = groesste Stadt)
    Spalten: name, ascii, iso2, lat, lng, admin1
    """
    names: dict = {}
    regions: dict = collections.defaultdict(list)
    for line in (ASSETS / "cities.tsv").read_text(encoding="utf-8").splitlines():
        c = line.split("\t")
        if len(c) < 6:
            continue
        try:
            coord = (float(c[3]), float(c[4]))
        except ValueError:
            continue
        for n in {normalize(c[0]), normalize(c[1])}:
            names.setdefault((c[2], n), coord)
        regions[(c[2], normalize(c[5]))].append(coord)
    return names, regions


def vote_country_code(locations: list[dict], names: dict) -> str | None:
    """ISO2 mit den meisten Namenstreffern. Eindeutig, nicht knapp:
    fuer TUERKIYE gewinnt TR mit 826, Zweiter ist US mit 5."""
    wanted = {normalize(loc["name"]) for loc in locations}
    votes: collections.Counter = collections.Counter()
    for (cc, n) in names:
        if n in wanted:
            votes[cc] += 1
    return votes.most_common(1)[0][0] if votes else None


def geocode(loc: dict, cc: str, names: dict, regions: dict):
    """(lat, lng, wie) oder None. Stufe 3 nur fuer Provinzeintraege!"""
    for v in variants(normalize(loc["name"])):
        hit = names.get((cc, v))
        if hit:
            return hit[0], hit[1], "name"
    if normalize(loc["name"]) == normalize(loc["province"]):
        rows = regions.get((cc, normalize(loc["province"])))
        if rows:
            return rows[0][0], rows[0][1], "province-center"
    return None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="nur N Laender (Smoke-Test)")
    args = ap.parse_args()

    names, regions = load_city_index()
    countries = load_countries()
    if args.limit:
        countries = countries[: args.limit]

    rows, stats, seen = [], collections.Counter(), set()
    for country in countries:
        locations = load_locations(country)
        if not locations:
            stats["laender_leer"] += 1
            continue
        cc = vote_country_code(locations, names)
        if not cc:
            stats["laender_ohne_votum"] += 1
            print(f"  ! kein ISO2-Votum fuer {country} ({len(locations)} Standorte)")
            continue
        for loc in locations:
            if loc["id"] in seen:
                continue
            hit = geocode(loc, cc, names, regions)
            if not hit:
                stats["verworfen"] += 1
                continue
            seen.add(loc["id"])
            lat, lng, how = hit
            stats[how] += 1
            rows.append(f"{loc['id']}\t{loc['name']}\t{loc['province']}\t{cc}\t{lat:.5f}\t{lng:.5f}")
        print(f"{country:28s} {cc}  {len(locations):5d} Standorte")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8", newline="\n")
    print(f"\n{OUT.relative_to(REPO)}: {len(rows)} Zeilen, "
          f"{OUT.stat().st_size / 1024:.0f} KB")
    print("Statistik:", dict(stats))


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Cache-Verzeichnis ignorieren**

Die `.gitignore` listet Tool-Caches einzeln (`tools/diyanet-fetch/cache/`,
`tools/cities/cache/`). Das neue Verzeichnis fehlt dort noch — ohne diesen
Schritt landen ~205 gecachte JSON-Dateien im Commit. Nach Zeile 33 ergänzen:

```
tools/diyanet-index/cache/
tools/diyanet-index/__pycache__/
```

Prüfen: `git check-ignore -v tools/diyanet-index/cache/countries.json` muss die
neue Regel nennen.

- [ ] **Step 5: Smoke-Test der Pipeline (3 Länder)**

Run: `python tools/diyanet-index/build_index.py --limit 3`
Expected: läuft ohne Traceback durch, gibt pro Land eine Zeile mit erkanntem ISO2-Code aus und schreibt das Asset. Prüfen: die Statistik nennt `name`-Treffer, und `laender_ohne_votum` ist 0 oder klein.

- [ ] **Step 6: Vollauf**

Run: `python tools/diyanet-index/build_index.py`
Expected: ~205 Länder, ~4 Minuten, Ausgabe „~10.000 Zeilen, ~400 KB".
Prüfen vor dem Commit:
- `verworfen` liegt in plausibler Größenordnung (erwartet grob 1.500–2.500 — DE allein steuert ~197 bei),
- `province-center` ist eine **kleine** Zahl (erwartet < 100). Ist sie groß, greift Stufe 3 zu breit — dann stimmt die `name == province`-Bedingung nicht mehr und der Lauf darf **nicht** committet werden.

- [ ] **Step 7: Run integrity test to verify it passes**

Run: `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "*WorldIndexIntegrityTest*"`
Expected: PASS (6 Tests), insbesondere `serdivanFindetSakarya`.

- [ ] **Step 8: README schreiben**

`tools/diyanet-index/README.md`:

```markdown
# Diyanet-Standortindex-Pipeline

Erzeugt `app/src/online/assets/official/locations-world.tsv` — den weltweiten
Index (Diyanet-ID → Koordinaten), über den die App amtliche Zeiten auch für
Orte auflöst, die Diyanet nicht als eigenen Standort führt (z. B. Serdivan →
Adapazarı/SAKARYA).

    python tools/diyanet-index/build_index.py            # Vollauf (~4 min)
    python tools/diyanet-index/build_index.py --limit 3  # Smoke-Test

- Roh-JSON liegt in `cache/` → Abbruch/Neustart überspringt Geholtes.
  Für einen frischen Jahresabruf `cache/` löschen!
- Vor dem Commit prüfen: `province-center` in der Statistik muss klein
  bleiben (< 100). Eine große Zahl heißt, dass Stufe 3 zu breit greift —
  dann erben Kleinorte fremde Koordinaten. Lauf verwerfen.
- Danach `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "*WorldIndexIntegrityTest*"`.
- Jährlich zusammen mit `tools/diyanet-fetch/fetch_diyanet.py` ausführen.
```

- [ ] **Step 9: Commit**

```bash
git add .gitignore tools/diyanet-index app/src/online/assets/official/locations-world.tsv app/src/testOnline/kotlin/de/gebetszeiten/official/WorldIndexIntegrityTest.kt
git commit -m "feat(data): weltweiter Diyanet-Standortindex plus Pipeline"
```

---

### Task 3: `DiyanetPlaceIndex` — Flavor-Provider mit Vorwärmung

**Files:**
- Create: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetPlaceIndex.kt`
- Create: `app/src/offline/kotlin/de/gebetszeiten/official/DiyanetPlaceIndex.kt`
- Test: `app/src/testOnline/kotlin/de/gebetszeiten/official/DiyanetPlaceIndexTest.kt`

**Interfaces:**
- Consumes: `parseDiyanetPlaces`, `DiyanetPlaces.nearest`, `DiyanetPlace` (Task 1); das Asset (Task 2).
- Produces (identische Signatur in beiden Flavors):
  - `suspend fun DiyanetPlaceIndex.preload(context: Context)`
  - `suspend fun DiyanetPlaceIndex.nearest(context: Context, lat: Double, lng: Double): DiyanetPlace?`
  - `fun DiyanetPlaceIndex.distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double` — **nicht** `suspend`, **kein** `Context`: die Berechnung ist reine Arithmetik auf einem bereits geladenen `DiyanetPlace`. So rufen die Code-Blöcke unten und der Konsument in Task 9 sie auf.

Muster wie `OfficialTimesProvider`/`PlaceSearchProvider`: gleiche API, flavorabhängige Implementierung. Ladeschema wie `Cities` (`@Volatile`-Cache + `withContext(Dispatchers.IO)`).

- [ ] **Step 1: Write the failing test**

`app/src/testOnline/kotlin/de/gebetszeiten/official/DiyanetPlaceIndexTest.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Der Index-Loader selbst braucht einen Context; hier wird geprüft, dass das
 *  ausgelieferte Asset über den Parser dieselbe Auflösung liefert, die der
 *  Fetcher später erwartet. Der Context-Pfad wird im Gerätecheck verifiziert. */
class DiyanetPlaceIndexTest {

    private val places by lazy {
        File("src/online/assets/official/locations-world.tsv")
            .useLines { parseDiyanetPlaces(it) }
    }

    @Test fun `Nuernberg loest auf einen deutschen Standort auf`() {
        val hit = de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
            .nearest(places, 49.4521, 11.0767)
        assertEquals("DE", hit?.countryCode)
    }

    @Test fun `Wien liegt in keinem 25-km-Radius eines tuerkischen Standorts`() {
        val hit = de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
            .nearest(places, 48.2082, 16.3738)
        // Wien selbst kann ein Diyanet-Standort sein; wenn ja, dann als AT.
        assertTrue("unerwartet: $hit", hit == null || hit.countryCode == "AT")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "*DiyanetPlaceIndexTest*"`
Expected: PASS oder FAIL je nach Datenlage — dieser Test prüft die Daten, nicht neuen Code. **Wenn er scheitert**, ist die Ursache im Asset (Task 2), nicht hier: dann Statistik prüfen und Task 2 nachziehen.

- [ ] **Step 3: Implement the online index**

`app/src/online/kotlin/de/gebetszeiten/official/DiyanetPlaceIndex.kt`:

```kotlin
package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Online-Flavor: weltweiter Diyanet-Standortindex aus dem gebündelten Asset.
 * Löst Koordinaten in eine Diyanet-ID auf — der Weg, über den Orte ohne
 * eigenen Diyanet-Eintrag (z. B. Serdivan) amtliche Zeiten bekommen.
 */
object DiyanetPlaceIndex {

    private const val ASSET = "official/locations-world.tsv"

    @Volatile private var cache: List<DiyanetPlace>? = null

    /** Vorab laden (beim Öffnen der Einstellungen), damit die erste
     *  Badge-Berechnung nicht an der TSV-Parse-Latenz hängt. */
    suspend fun preload(context: Context) {
        places(context)
    }

    suspend fun nearest(context: Context, lat: Double, lng: Double): DiyanetPlace? {
        val all = places(context)
        return withContext(Dispatchers.Default) { DiyanetPlaces.nearest(all, lat, lng) }
    }

    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double =
        DiyanetPlaces.distanceKm(place, lat, lng)

    private suspend fun places(context: Context): List<DiyanetPlace> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            cache ?: load(context).also { cache = it }
        }
    }

    private fun load(context: Context): List<DiyanetPlace> = try {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines {
            parseDiyanetPlaces(it)
        }
    } catch (e: FileNotFoundException) {
        // Fehlendes Asset darf die Ortswahl nicht sprengen — die Kette fällt
        // dann auf Cache/Namenssuche zurück wie vor diesem Umbau.
        android.util.Log.w("DiyanetPlaceIndex", "Index-Asset fehlt", e)
        emptyList()
    }
}
```

- [ ] **Step 4: Implement the offline no-op twin**

`app/src/offline/kotlin/de/gebetszeiten/official/DiyanetPlaceIndex.kt`:

```kotlin
package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace

/**
 * Offline-Flavor: kein Diyanet-ID-Index. Der Flavor ruft nie ab, also gibt es
 * hier nichts aufzulösen — und kein 400-KB-Asset, das nach Netzfähigkeit
 * aussieht. Badges entstehen offline ausschließlich aus dem DE-Bundle.
 */
object DiyanetPlaceIndex {
    suspend fun preload(context: Context) = Unit
    suspend fun nearest(context: Context, lat: Double, lng: Double): DiyanetPlace? = null
    fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double = Double.MAX_VALUE
}
```

- [ ] **Step 5: Verify both flavors compile**

Run: `.\gradlew.bat :app:compileOnlineDebugKotlin :app:compileOfflineDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/online/kotlin/de/gebetszeiten/official/DiyanetPlaceIndex.kt app/src/offline/kotlin/de/gebetszeiten/official/DiyanetPlaceIndex.kt app/src/testOnline/kotlin/de/gebetszeiten/official/DiyanetPlaceIndexTest.kt
git commit -m "feat: DiyanetPlaceIndex je Flavor (online Asset, offline No-op)"
```

---

### Task 4: Auflösungskette umbauen — hier wird Serdivan geheilt

**Files:**
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt:54-67` (companion `create`) und `:27-39` (`fetch`, Logging)
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetProxyFetcher.kt:26-52` (`resolveLocationId` Logging + private `normalize` durch `TextNormalize.normalize` ersetzen)
- Test: `app/src/testOnline/kotlin/de/gebetszeiten/official/ProxyNameNormalizeTest.kt`
- Test: `app/src/testOnline/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcherTest.kt` (erweitern)

**Interfaces:**
- Consumes: `DiyanetPlaceIndex.nearest` (Task 3), `BundledOfficialSource.nearestLocation`, `OfficialTimesCache.cachedLocationId`.
- Produces: unveränderte öffentliche API (`OfficialTimesFetcher.fetch`) — nur die Reihenfolge der ID-Auflösung ändert sich.

- [ ] **Step 1: Write the failing tests**

An `CompositeDiyanetFetcherTest.kt` anfügen (die bestehenden Tests bleiben unverändert):

```kotlin
    @Test
    fun `leeres Ergebnis wird protokolliert statt still verschluckt`() = runBlocking {
        val logged = mutableListOf<String>()
        val f = CompositeDiyanetFetcher(
            resolveId = { null },
            direct = { yearData },
            proxy = { proxyData },
            log = { msg, _ -> logged.add(msg) },
        )
        val result = f.fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertTrue("kein Log-Eintrag: $logged", logged.any { it.contains("Standort") })
    }
```

Zusätzlich eine neue Datei für die Kettenreihenfolge,
`app/src/testOnline/kotlin/de/gebetszeiten/official/LocationIdChainTest.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reihenfolge der ID-Auflösung: Bundle -> Index -> Cache -> Namenssuche.
 *  Die Namenssuche war der Primärweg und damit die Ursache des
 *  Serdivan-Bugs; sie darf nur noch als letzte Stufe laufen. */
class LocationIdChainTest {

    private val sakarya = DiyanetPlace(9807, "SAKARYA", "SAKARYA", "TR", 40.78056, 30.40333)

    private fun chain(
        bundled: Int? = null,
        index: DiyanetPlace? = null,
        cached: Int? = null,
        byName: (() -> Int?)? = null,
    ): suspend () -> Int? = {
        bundled
            ?: index?.diyanetId
            ?: cached
            ?: byName?.invoke()
    }

    @Test fun `Index greift vor Cache und Namenssuche`() = runBlocking {
        var nameCalls = 0
        val id = resolveLocationIdChain(
            bundledId = null,
            indexPlace = sakarya,
            cachedId = 1234,
            searchByName = { nameCalls++; 5678 },
        )
        assertEquals(9807, id)
        assertEquals("Namenssuche darf nicht laufen", 0, nameCalls)
    }

    @Test fun `Bundle schlaegt den Index`() = runBlocking {
        val id = resolveLocationIdChain(
            bundledId = 11024,
            indexPlace = sakarya,
            cachedId = null,
            searchByName = { null },
        )
        assertEquals(11024, id)
    }

    @Test fun `ohne Bundle und Index kommt der Cache`() = runBlocking {
        var nameCalls = 0
        val id = resolveLocationIdChain(
            bundledId = null,
            indexPlace = null,
            cachedId = 1234,
            searchByName = { nameCalls++; 5678 },
        )
        assertEquals(1234, id)
        assertEquals(0, nameCalls)
    }

    @Test fun `Namenssuche bleibt die letzte Rettung`() = runBlocking {
        val id = resolveLocationIdChain(
            bundledId = null,
            indexPlace = null,
            cachedId = null,
            searchByName = { 5678 },
        )
        assertEquals(5678, id)
    }

    @Test fun `nichts aufloesbar bleibt null`() = runBlocking {
        assertNull(
            resolveLocationIdChain(
                bundledId = null,
                indexPlace = null,
                cachedId = null,
                searchByName = { null },
            ),
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "*LocationIdChainTest*" --tests "*CompositeDiyanetFetcherTest*"`
Expected: FAIL — `resolveLocationIdChain` ist unbekannt (Kompilierfehler), und der Log-Test schlägt fehl.

- [ ] **Step 3: Extract the chain as a pure, testable function**

In `CompositeDiyanetFetcher.kt` ergänzen (Datei-Ebene, damit ohne Context testbar):

```kotlin
/**
 * ID-Auflösung als reine Funktion: Bundle → Index → Cache → Namenssuche.
 *
 * Die Namenssuche war bis 2026-08 der Primärweg — sie scheiterte an jedem Ort,
 * den Diyanet nicht selbst als Standort führt (Serdivan). Der Koordinatenindex
 * steht deshalb VOR ihr; sie bleibt nur für Index-Lücken.
 */
internal suspend fun resolveLocationIdChain(
    bundledId: Int?,
    indexPlace: de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace?,
    cachedId: Int?,
    searchByName: suspend () -> Int?,
): Int? = bundledId ?: indexPlace?.diyanetId ?: cachedId ?: searchByName()
```

Und `create` darauf umstellen:

```kotlin
        fun create(context: Context): CompositeDiyanetFetcher {
            val proxyFetcher = DiyanetProxyFetcher()
            return CompositeDiyanetFetcher(
                resolveId = { settings ->
                    resolveLocationIdChain(
                        bundledId = BundledOfficialSource
                            .nearestLocation(context, settings.latitude, settings.longitude)
                            ?.diyanetId,
                        indexPlace = DiyanetPlaceIndex
                            .nearest(context, settings.latitude, settings.longitude),
                        cachedId = OfficialTimesCache(context)
                            .cachedLocationId(settings.latitude, settings.longitude),
                        searchByName = {
                            withContext(Dispatchers.IO) {
                                proxyFetcher.resolveLocationId(settings.city)
                            }
                        },
                    )
                },
                direct = DiyanetDirectFetcher()::fetchYear,
                proxy = proxyFetcher::fetchById,
            )
        }
```

Hinweis: `bundledId` und `indexPlace` werden hier eifrig ausgewertet (beide sind rein lokal und in Millisekunden fertig), `searchByName` bleibt ein Lambda — der einzige Netzaufruf der Kette darf nur laufen, wenn er gebraucht wird.

- [ ] **Step 4: Add the missing logging**

In `CompositeDiyanetFetcher.fetch` die stille Rückgabe ersetzen:

```kotlin
        val id = try {
            resolveId(settings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Standort-Aufloesung fehlgeschlagen", e)
            null
        }
        if (id == null) {
            // Vorher ein stilles `?: return` — genau deshalb war der
            // Serdivan-Fall unsichtbar.
            log("Kein Diyanet-Standort fuer '${settings.city}' aufloesbar", IllegalStateException("keine ID"))
            return FetchResult(emptyMap(), null)
        }
```

In `DiyanetProxyFetcher.resolveLocationId` den Leerfall protokollieren:

```kotlin
        if (arr.length() == 0) {
            android.util.Log.w("DiyanetFetch", "Namenssuche ohne Treffer fuer '$city'")
            return null
        }
```

**Zusätzlich: die unvollständige private Normalisierung ersetzen.**
`DiyanetProxyFetcher.kt:49-52` hat eine eigene `normalize`, die nur NFD-Marken
entfernt. Das punktlose türkische `ı` (U+0131) ist aber **nicht zerlegbar** — es
überlebt das NFD-Strippen und wird nie auf `i` abgebildet. Mit dem echten JDK
gegengeprüft:

| Vergleich | private `normalize` | `TextNormalize.normalize` |
|---|---|---|
| `Şanlıurfa` ↔ `ŞANLIURFA` | **MISS** | MATCH |
| `Niğde` ↔ `NIĞDE` | MATCH | MATCH |

Der Fehlerfall ist real: `settings.city` kommt aus `cities.tsv` in türkischer
Schreibweise („Şanlıurfa"), Diyanet listet „ŞANLIURFA" — die Namenssuche
verfehlt den Standort, obwohl er existiert. Deshalb die private Funktion löschen
und die bestehende, vollständige Normalisierung nutzen (die `ı`, `ş`, `ğ`, `ç`,
`ö`, `ü`, `ß` explizit abbildet):

```kotlin
import de.gebetszeiten.data.TextNormalize
// …
    private fun normalize(s: String): String = TextNormalize.normalize(s)
```

Damit gilt in der ganzen App **eine** Normalisierung — auch für Nutzer, die mit
deutscher Tastatur „sanliurfa" tippen.

Test dazu in `app/src/testOnline/kotlin/de/gebetszeiten/official/`
(neue Datei `ProxyNameNormalizeTest.kt`):

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.data.TextNormalize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Das punktlose tuerkische ı ist nicht NFD-zerlegbar — ohne explizite
 *  Abbildung verfehlt die Namenssuche jeden Ort mit diesem Buchstaben. */
class ProxyNameNormalizeTest {

    @Test fun `tuerkische Schreibweise trifft die Diyanet-Grossschreibung`() {
        assertEquals(TextNormalize.normalize("ŞANLIURFA"), TextNormalize.normalize("Şanlıurfa"))
        assertEquals(TextNormalize.normalize("NIĞDE"), TextNormalize.normalize("Niğde"))
        assertEquals(TextNormalize.normalize("İSTANBUL"), TextNormalize.normalize("İstanbul"))
    }

    @Test fun `deutsche Tastatur trifft ebenfalls`() {
        assertEquals(TextNormalize.normalize("ŞANLIURFA"), TextNormalize.normalize("sanliurfa"))
        assertEquals(TextNormalize.normalize("NIĞDE"), TextNormalize.normalize("nigde"))
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testOnlineDebugUnitTest`
Expected: PASS — alle bestehenden `CompositeDiyanetFetcherTest`-Tests weiterhin grün, plus die 5 neuen Kettentests und der Log-Test.

- [ ] **Step 6: Gerätecheck — der eigentliche Beweis**

Online-Flavor auf ein Gerät/Emulator bringen, Ort **Serdivan** wählen, App neu starten.
Expected: Isha zeigt **21:36** (nicht 21:41) für den 14.08.2026, und `adb logcat -s DiyanetFetch PrayerProvider` enthält keine „Kein Diyanet-Standort"-Warnung.
Screenshot ablegen.

- [ ] **Step 7: Commit**

```bash
git add app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt app/src/online/kotlin/de/gebetszeiten/official/DiyanetProxyFetcher.kt app/src/testOnline/kotlin/de/gebetszeiten/official/
git commit -m "fix: Diyanet-ID ueber Koordinatenindex statt Namenssuche aufloesen"
```

---

### Task 5: Abruf-Status im Cache festhalten

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt:84-113` (`refreshOfficial`)
- Test: `app/src/test/kotlin/de/gebetszeiten/official/OfficialStatusTest.kt`

**Interfaces:**
- Produces:
  - `data class OfficialStatus(val locationId: Int?, val coveredUntil: LocalDate?, val lastAttemptEpochMs: Long?, val lastError: String?)`
  - `suspend fun OfficialTimesCache.status(lat: Double, lng: Double): OfficialStatus`
  - `suspend fun OfficialTimesCache.recordAttempt(error: String?, nowEpochMs: Long, lat: Double, lng: Double)` — `error = null` heißt Erfolg. Der Versuch trägt seinen EIGENEN Ort: der Erfolgsstempel wird nur von `putAll` gesetzt, ein Fehlschlag würde sonst dem vorherigen Ort zugeschrieben.
  - `fun officialStatusText(status: OfficialStatus, source: TimesSourceBadge, zone: ZoneId = ZoneId.systemDefault()): String` in `prayer/OfficialStatusText.kt` — die aktive Quelle wird **klassifiziert übergeben**, nicht aus dem Cache erraten. Die Einordnung spiegelt `PrayerProvider.daily` (Nutzerwunsch → Online-Cache → gebündelte Tabelle → Berechnung). Zone als Parameter, damit die Funktion deterministisch testbar bleibt.

`recordAttempt` bekommt die Zeit **übergeben** statt `System.currentTimeMillis()` intern zu lesen — sonst ist die Textformatierung nicht testbar.

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/de/gebetszeiten/official/OfficialStatusTest.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.prayer.officialStatusText
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OfficialStatusTest {

    private val now = 1_786_000_000_000L // fester Zeitpunkt, kein System.now

    @Test fun `amtlicher Status nennt Standort und Abdeckung`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            sourceName = "Sakarya",
            nowEpochMs = now,
        )
        assertTrue(text, text.contains("Sakarya"))
        assertTrue(text, text.contains("9807"))
        assertTrue(text, text.contains("31.12.2026"))
    }

    @Test fun `Fehlergrund erscheint im Klartext`() {
        val text = officialStatusText(
            OfficialStatus(null, null, now, "Kein Diyanet-Standort aufloesbar"),
            sourceName = null,
            nowEpochMs = now,
        )
        assertTrue(text, text.contains("Kein Diyanet-Standort aufloesbar"))
    }

    @Test fun `ohne jeden Abruf wird das gesagt statt ein leerer Text`() {
        val text = officialStatusText(
            OfficialStatus(null, null, null, null),
            sourceName = null,
            nowEpochMs = now,
        )
        assertTrue(text, text.isNotBlank())
        assertTrue(text, text.contains("noch kein"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "*OfficialStatusTest*"`
Expected: FAIL — `OfficialStatus` und `officialStatusText` unbekannt.

- [ ] **Step 3: Add the status fields to the cache**

In `OfficialTimesCache.kt` ergänzen:

```kotlin
    private val lastAttempt = longPreferencesKey("last_attempt")
    private val lastError = stringPreferencesKey("last_error")

    /** Alles, was die Statuszeile braucht — in EINEM DataStore-Read. */
    suspend fun status(lat: Double, lng: Double): OfficialStatus {
        val prefs = context.officialStore.data.first()
        val match = stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)
        return OfficialStatus(
            locationId = if (match) prefs[stampId] else null,
            coveredUntil = if (match) prefs[key]?.let { ScheduleText.parse(it).keys.maxOrNull() } else null,
            lastAttemptEpochMs = prefs[lastAttempt],
            lastError = prefs[lastError],
        )
    }

    /** Zeitstempel und Fehlergrund des letzten Abrufversuchs. [error] = null
     *  heißt Erfolg. Zeit wird übergeben, damit Tests nicht an der Systemuhr
     *  hängen. Der Parameter heißt absichtlich NICHT `lastError` — das würde
     *  den gleichnamigen Key beschatten und jede Zeile hier auf `this.`
     *  angewiesen machen. */
    suspend fun recordAttempt(error: String?, nowEpochMs: Long) {
        context.officialStore.edit {
            it[lastAttempt] = nowEpochMs
            if (error != null) it[lastError] = error else it.remove(lastError)
        }
    }
```

Nötige Imports ergänzen: `longPreferencesKey`.

Datei-Ebene, im selben File:

```kotlin
/** Momentaufnahme für die Statuszeile. */
data class OfficialStatus(
    val locationId: Int?,
    val coveredUntil: java.time.LocalDate?,
    val lastAttemptEpochMs: Long?,
    val lastError: String?,
)
```

- [ ] **Step 4: Add the status text formatter**

`app/src/main/kotlin/de/gebetszeiten/prayer/OfficialStatusText.kt`:

```kotlin
package de.gebetszeiten.prayer

import de.gebetszeiten.official.OfficialStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

/**
 * Mehrzeiliger Klartext für die Statuszeile im Einstellungs-Sheet.
 * Reine Funktion (Zeit wird übergeben) — deshalb ohne Android testbar.
 */
fun officialStatusText(status: OfficialStatus, sourceName: String?, nowEpochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val lines = mutableListOf<String>()
    if (status.locationId != null && sourceName != null) {
        lines += "Quelle: amtliche Diyanet-Zeiten · $sourceName (ID ${status.locationId})"
    } else {
        lines += "Quelle: eigene Berechnung (Diyanet-Methode)"
    }
    status.coveredUntil?.let { lines += "Abgedeckt bis: ${DATE.format(it)}" }
    if (status.lastAttemptEpochMs == null) {
        // "noch kein Versuch" nur, wenn es auch keinerlei Beleg fuer einen
        // frueheren Abruf gibt. Liegen Standort oder Abdeckung vor, hat es
        // sehr wohl einen gegeben — seine Aufzeichnung wurde nur vom Versuch
        // fuer einen anderen Ort verdraengt (ein Datensatz fuer alle Orte).
        lines += if (status.locationId != null || status.coveredUntil != null) {
            "Letzter Abruf: unbekannt"
        } else {
            "Letzter Abruf: noch kein Versuch"
        }
    } else {
        val stamp = STAMP.format(Instant.ofEpochMilli(status.lastAttemptEpochMs).atZone(zone))
        lines += "Letzter Abruf: $stamp"
    }
    status.lastError?.let { lines += "Fehler: $it" }
    return lines.joinToString("\n")
}
```

- [ ] **Step 5: Record the attempt in `refreshOfficial`**

In `PrayerProvider.refreshOfficial` den try/catch-Block erweitern, sodass jeder Ausgang festgehalten wird:

```kotlin
        val fetcher = OfficialTimesProvider.fetcher(context) ?: return
        val now = System.currentTimeMillis()
        try {
            withTimeout(25_000) {
                val result = fetcher.fetch(settings)
                if (result.schedule.isEmpty()) {
                    cache.recordAttempt("Keine amtlichen Zeiten erhalten (Standort oder Netz)", now)
                    return@withTimeout
                }
                cache.putAll(result.schedule, settings.latitude, settings.longitude, result.locationId)
                cache.recordAttempt(null, now)
                OfficialTimesProvider.syncToWear(context, result.schedule, settings)
            }
        } catch (e: TimeoutCancellationException) {
            android.util.Log.w("PrayerProvider", "refreshOfficial abgebrochen (Timeout)", e)
            cache.recordAttempt("Zeitüberschreitung beim Abruf", now)
        }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "*OfficialStatusTest*"` und `.\gradlew.bat :app:testOnlineDebugUnitTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt app/src/main/kotlin/de/gebetszeiten/prayer/OfficialStatusText.kt app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt app/src/test/kotlin/de/gebetszeiten/official/OfficialStatusTest.kt
git commit -m "feat: Abrufstatus (Zeitstempel, Fehlergrund) im Cache festhalten"
```

---

### Task 6: Footer zeigt die Quelle zuverlässig

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt:270-281` (`officialName`-`produceState`)
- Modify: `app/src/main/res/values/strings.xml:36`

**Interfaces:**
- Consumes: `DiyanetPlaceIndex.nearest` (Task 3), `displayName()` (Task 1).

Der Bug: `produceState` hat nur `settings, selectedDate` als Keys. Nach einem erfolgreichen Abruf (der parallel in `PrayerViewModel.reschedule` läuft) bleibt der Footer auf „offline", bis Datum oder Einstellung wechselt.

- [ ] **Step 1: Add `tick` as a key and consult the index**

```kotlin
    val officialName by produceState<String?>(null, settings, selectedDate, tick) {
        value = if (settings.useCalculated) {
            null
        } else {
            de.gebetszeiten.official.BundledOfficialSource
                .locationNameFor(context, settings.latitude, settings.longitude, selectedDate)
                ?: officialCacheName(context, settings, selectedDate)
        }
    }
```

Dazu, im selben File auf Datei-Ebene:

```kotlin
/** Name des Standorts, dessen gecachte amtliche Zeiten gerade greifen — oder
 *  null, wenn kein Cache-Treffer vorliegt. Der Name kommt aus dem
 *  Diyanet-Index (echter Standort, z. B. „Sakarya" für Serdivan) und fällt
 *  auf den Ortsnamen der Einstellungen zurück. */
private suspend fun officialCacheName(
    context: android.content.Context,
    settings: AppSettings,
    date: java.time.LocalDate,
): String? {
    if (!settings.useOnline) return null
    // Kein Cache-Treffer = keine amtlichen Zeiten fuer diesen Tag.
    de.gebetszeiten.official.OfficialTimesCache(context)
        .get(date, settings.latitude, settings.longitude) ?: return null
    val place = de.gebetszeiten.official.DiyanetPlaceIndex
        .nearest(context, settings.latitude, settings.longitude)
    return place?.displayName() ?: settings.city
}
```

Import ergänzen: `de.gebetszeiten.core.prayertimes.officialtimes.displayName`.

- [ ] **Step 2: Sharpen the calculated string**

`strings.xml:36` ersetzen:

```xml
    <string name="data_credit_calculated">Berechnet · Diyanet-Methode</string>
```

Begründung im Commit: „(offline)" war irreführend — der Text erschien auch im online-Flavor, wenn nur der Abruf scheiterte.

- [ ] **Step 3: Verify on device**

Online-Flavor bauen, Ort Serdivan, App starten (Cache leer).
Expected: Footer wechselt **ohne** Datums- oder Einstellungswechsel von „Berechnet · Diyanet-Methode" auf „Amtliche Diyanet-Zeiten · Sakarya", sobald der Abruf durch ist (spätestens beim nächsten Minuten-`tick`). Screenshot.

- [ ] **Step 4: Run the full unit suite**

Run: `.\gradlew.bat :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest`
Expected: PASS — insbesondere `DataCreditTest` bleibt grün (es prüft nur die Ressourcen-ID, nicht den Text).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "fix(ui): Footer aktualisiert die Quelle nach dem Abruf, nennt echten Standort"
```

---

### Task 7: `TimesSourceBadge` — Quelle pro Suchtreffer als reine Funktion

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/TimesSourceBadge.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/TimesSourceBadgeTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface TimesSourceBadge` mit `data class Bundled(val locationName: String)`, `data class Official(val locationName: String, val distanceKm: Int)`, `data object Calculated`
  - `fun timesSourceBadge(bundledName: String?, officialPlace: DiyanetPlace?, distanceKm: Double?, useCalculated: Boolean): TimesSourceBadge`

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/de/gebetszeiten/prayer/TimesSourceBadgeTest.kt`:

```kotlin
package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class TimesSourceBadgeTest {

    private val sakarya = DiyanetPlace(9807, "SAKARYA", "SAKARYA", "TR", 40.78056, 30.40333)

    @Test fun `eigene Berechnung schlaegt alles`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge("Nürnberg", sakarya, 2.1, useCalculated = true),
        )
    }

    @Test fun `gebuendelte Tabelle hat Vorrang vor dem Index`() {
        // Spiegelt resolveLocationIdChain (Bundle vor Index). Beide meinen
        // oft denselben Standort; das Bundle hat die bessere Schreibweise.
        assertEquals(
            TimesSourceBadge.Bundled("Nürnberg"),
            timesSourceBadge("Nürnberg", sakarya, 2.1, useCalculated = false),
        )
    }

    @Test fun `ohne gebuendelten Treffer greift der Index`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 2),
            timesSourceBadge(null, sakarya, 2.1, useCalculated = false),
        )
    }

    @Test fun `Index liefert Standortname und gerundete Distanz`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 2),
            timesSourceBadge(null, sakarya, 2.1, useCalculated = false),
        )
    }

    @Test fun `Distanz wird kaufmaennisch gerundet`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 8),
            timesSourceBadge(null, sakarya, 7.6, useCalculated = false),
        )
    }

    @Test fun `ohne Quelle bleibt Berechnung`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge(null, null, null, useCalculated = false),
        )
    }

    @Test fun `Index ohne Distanz ist ein Datenfehler und faellt auf Berechnung`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge(null, sakarya, null, useCalculated = false),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "*TimesSourceBadgeTest*"`
Expected: FAIL — `TimesSourceBadge` unbekannt.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import kotlin.math.roundToInt

/** Welche Quelle ein Ort liefert — für das Badge in der Ortssuche. */
sealed interface TimesSourceBadge {
    /** Amtliche Zeiten aus einer gebündelten Jahrestabelle (Deutschland). */
    data class Bundled(val locationName: String) : TimesSourceBadge
    /** Amtliche Zeiten per Abruf über den nächstgelegenen Diyanet-Standort. */
    data class Official(val locationName: String, val distanceKm: Int) : TimesSourceBadge
    /** Keine amtliche Quelle — eigene Berechnung. */
    data object Calculated : TimesSourceBadge
}

/**
 * Klassifiziert einen Suchtreffer. Reihenfolge spiegelt
 * [resolveLocationIdChain] — die Funktion, die tatsächlich entscheidet, WELCHER
 * Diyanet-Standort abgerufen wird: Nutzerwunsch → gebündelte Tabelle → Index →
 * Berechnung.
 *
 * ACHTUNG, hier wurde schon zweimal falsch abgebogen: `PrayerProvider.daily`
 * fragt zwar den Online-Cache vor der gebündelten Tabelle, aber das ist eine
 * andere Frage — dort geht es um die ZEITEN, und der Cache enthält genau die
 * Zeiten der ID, die zuvor aus dem Bundle kam. Für die Frage, welcher STANDORT
 * benannt wird, gilt Bundle vor Index. Beide führen beim selben Ort oft
 * dieselbe Diyanet-ID (Nürnberg = 11024 in beiden), aber das Bundle hat die
 * bessere Schreibweise („Nürnberg" statt Diyanets „NURNBERG").
 */
fun timesSourceBadge(
    bundledName: String?,
    officialPlace: DiyanetPlace?,
    distanceKm: Double?,
    useCalculated: Boolean,
): TimesSourceBadge = when {
    useCalculated -> TimesSourceBadge.Calculated
    bundledName != null -> TimesSourceBadge.Bundled(bundledName)
    officialPlace != null && distanceKm != null ->
        TimesSourceBadge.Official(officialPlace.displayName(), distanceKm.roundToInt())
    else -> TimesSourceBadge.Calculated
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "*TimesSourceBadgeTest*"`
Expected: PASS (6 Tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/prayer/TimesSourceBadge.kt app/src/test/kotlin/de/gebetszeiten/prayer/TimesSourceBadgeTest.kt
git commit -m "feat: TimesSourceBadge klassifiziert die Quelle eines Suchtreffers"
```

---

### Task 8: Einstellungs-Sheet in eigene Datei (rein mechanisch)

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (Zeilen 1134–1623 entfernen)

**Interfaces:**
- Produces: `LocationSettings(settings: AppSettings, onApply: (AppSettings) -> Unit)` — unveränderte Signatur, jetzt `internal` statt `private`, damit `MainActivity` sie weiter aufrufen kann.

**Kein Verhaltensänderung in diesem Task.** Nur verschieben — die Badges kommen in Task 9. Ein separater Task, weil ein 490-Zeilen-Move und ein Feature im selben Diff nicht mehr reviewbar sind.

- [ ] **Step 1: Verify the cut is clean**

Run:
```bash
grep -n "SettingsSection(\|highlightPrefix(\|countryDisplayName(\|ToggleRow(\|CountdownModeSelector(\|FontSizeSelector(\|BatteryOptimizationCard(" app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt
```
Expected: **jede** Verwendungszeile liegt ≥ 1166 (also innerhalb von `LocationSettings`). Falls eine Verwendung oberhalb auftaucht, bleibt der betroffene Helfer in `MainActivity.kt` und wird `internal`.

- [ ] **Step 2: Move the block**

Zeilen 1134–1623 aus `MainActivity.kt` nach `app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt` verschieben. Kopf der neuen Datei:

```kotlin
package de.gebetszeiten.ui

// Das Einstellungs-Sheet: Ortswahl, Anzeige, Erinnerungen. Aus MainActivity
// herausgelöst (dort waren es 1.623 Zeilen), weil die Ortswahl mit den
// Quellen-Badges weiter wächst.
```

Dann: `private fun LocationSettings` → `internal fun LocationSettings`. Alle übrigen Helfer bleiben `private` (sie werden nur hier verwendet). Imports in beiden Dateien mit der IDE-Funktion „Optimize Imports" bzw. per Compiler-Fehlerliste sortieren.

- [ ] **Step 3: Verify compilation and tests**

Run: `.\gradlew.bat :app:compileOnlineDebugKotlin :app:compileOfflineDebugKotlin :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 4: Verify no behaviour changed**

Run: `git diff --stat HEAD`
Expected: `MainActivity.kt` verliert ~490 Zeilen, `SettingsSheet.kt` gewinnt etwa dieselbe Zahl. Nettoänderung nahe null. Steht dort mehr, wurde versehentlich Logik verändert — dann zurückrollen und nur verschieben.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt
git commit -m "refactor(ui): Einstellungs-Sheet aus MainActivity in eigene Datei"
```

---

### Task 9: Badges in der Trefferliste

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt` (Trefferliste in `LocationSettings`)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `timesSourceBadge` (Task 7), `DiyanetPlaceIndex.nearest`/`distanceKm` (Task 3), `BundledOfficialSource.nearestLocation`.

- [ ] **Step 1: Add the strings**

In `strings.xml` ergänzen:

```xml
    <string name="badge_bundled">Amtlich · %1$s</string>
    <string name="badge_official">Amtlich · %1$s, %2$d km</string>
    <string name="badge_calculated">Berechnet</string>
```

- [ ] **Step 2: Compute badges for the shown matches**

In `LocationSettings`, nach der Deklaration von `shownMatches`:

```kotlin
            // Quelle pro Treffer: beide Indizes sind vorgewärmt, das läuft
            // ohne Netz und ohne merkbare Verzögerung.
            val badges by produceState(emptyMap<String, TimesSourceBadge>(), shownMatches, settings.useCalculated) {
                value = shownMatches.associate { c ->
                    val key = "${c.name}|${c.latitude}|${c.longitude}"
                    val bundled = de.gebetszeiten.official.BundledOfficialSource
                        .nearestLocation(context, c.latitude, c.longitude)?.name
                    val place = de.gebetszeiten.official.DiyanetPlaceIndex
                        .nearest(context, c.latitude, c.longitude)
                    key to timesSourceBadge(
                        bundledName = bundled,
                        officialPlace = place,
                        distanceKm = place?.let {
                            de.gebetszeiten.official.DiyanetPlaceIndex.distanceKm(it, c.latitude, c.longitude)
                        },
                        useCalculated = settings.useCalculated,
                    )
                }
            }
```

- [ ] **Step 3: Render the badge in each row**

Im `DropdownMenuItem`-`text`-Block, unter der Region/Land-Zeile:

```kotlin
                                        val badge = badges["${c.name}|${c.latitude}|${c.longitude}"]
                                        if (badge != null) {
                                            Text(
                                                when (badge) {
                                                    is TimesSourceBadge.Bundled ->
                                                        stringResource(R.string.badge_bundled, badge.locationName)
                                                    is TimesSourceBadge.Official ->
                                                        stringResource(R.string.badge_official, badge.locationName, badge.distanceKm)
                                                    TimesSourceBadge.Calculated ->
                                                        stringResource(R.string.badge_calculated)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (badge is TimesSourceBadge.Calculated) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                        }
```

Import ergänzen: `de.gebetszeiten.prayer.TimesSourceBadge`, `de.gebetszeiten.prayer.timesSourceBadge`.

- [ ] **Step 4: Preload the index when the sheet opens**

Die bestehende Vorwärmung erweitern:

```kotlin
    LaunchedEffect(Unit) {
        Cities.preload(context)
        de.gebetszeiten.official.DiyanetPlaceIndex.preload(context)
    }
```

- [ ] **Step 5: Verify on device**

Einstellungen öffnen, „Serdivan" tippen.
Expected: Treffer „Serdivan · Sakarya · Türkei" mit Badge **„Amtlich · Sakarya, 2 km"** in Akzentfarbe. „Wien" → „Berechnet" in gedeckter Farbe. „Nürnberg" → „Amtlich · Nürnberg". Kein merkliches Ruckeln beim Tippen. Screenshot.

Im offline-Flavor gegenprüfen: „Serdivan" → „Berechnet" (kein Index), „Nürnberg" → „Amtlich · Nürnberg".

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat(ui): Ortssuche zeigt pro Treffer die zu erwartende Quelle"
```

---

### Task 10: Letzte Orte, Autofokus, Leeren-Knopf, Manuell-Aufklapper

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/data/RecentPlaces.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/data/RecentPlacesTest.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces:
  - `fun parseRecentPlaces(text: String?): List<City>`
  - `fun serializeRecentPlaces(places: List<City>): String`
  - `fun withRecentPlace(existing: List<City>, added: City, max: Int = 5): List<City>`
  - `AppSettings.recentPlaces: List<City>` (neues Feld, Default `emptyList()`)

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/de/gebetszeiten/data/RecentPlacesTest.kt`:

```kotlin
package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPlacesTest {

    private fun city(name: String) = City(name, "TR", 40.0, 30.0, "SAKARYA")

    @Test fun `Rundreise durch Serialisierung erhaelt die Reihenfolge`() {
        val list = listOf(city("Serdivan"), city("Adapazarı"))
        assertEquals(list, parseRecentPlaces(serializeRecentPlaces(list)))
    }

    @Test fun `null und Muell ergeben eine leere Liste`() {
        assertEquals(emptyList<City>(), parseRecentPlaces(null))
        assertEquals(emptyList<City>(), parseRecentPlaces(""))
        assertEquals(emptyList<City>(), parseRecentPlaces("kaputt\tzeile"))
    }

    @Test fun `neuester Ort steht vorn`() {
        val result = withRecentPlace(listOf(city("A"), city("B")), city("C"))
        assertEquals(listOf("C", "A", "B"), result.map { it.name })
    }

    @Test fun `erneute Wahl dedupliziert statt zu verdoppeln`() {
        val result = withRecentPlace(listOf(city("A"), city("B")), city("B"))
        assertEquals(listOf("B", "A"), result.map { it.name })
    }

    @Test fun `laenger als max wird abgeschnitten`() {
        val start = listOf(city("A"), city("B"), city("C"), city("D"), city("E"))
        val result = withRecentPlace(start, city("F"))
        assertEquals(5, result.size)
        assertEquals("F", result.first().name)
        assertEquals(listOf("F", "A", "B", "C", "D"), result.map { it.name })
    }

    @Test fun `Tabs im Ortsnamen zerstoeren die Serialisierung nicht`() {
        val odd = City("Bad\tName", "DE", 49.0, 11.0, null)
        assertEquals(listOf("BadName"), parseRecentPlaces(serializeRecentPlaces(listOf(odd))).map { it.name })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "*RecentPlacesTest*"`
Expected: FAIL — Funktionen unbekannt.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/kotlin/de/gebetszeiten/data/RecentPlaces.kt`:

```kotlin
package de.gebetszeiten.data

/**
 * Zuletzt gewählte Orte, damit ein Ortswechsel keinen Tastendruck kostet.
 * Serialisiert als Zeilen `name \t iso2 \t lat \t lng \t region` — geordnet,
 * anders als ein `stringSetPreferencesKey`.
 */
private const val SEP = '\t'

fun serializeRecentPlaces(places: List<City>): String =
    places.joinToString("\n") {
        // Tabs im Namen würden das Format sprengen.
        listOf(
            it.name.replace("\t", ""),
            it.country,
            it.latitude.toString(),
            it.longitude.toString(),
            it.region.orEmpty().replace("\t", ""),
        ).joinToString(SEP.toString())
    }

fun parseRecentPlaces(text: String?): List<City> =
    text?.lineSequence()?.mapNotNull { line ->
        val c = line.split(SEP)
        if (c.size != 5) return@mapNotNull null
        val lat = c[2].toDoubleOrNull() ?: return@mapNotNull null
        val lng = c[3].toDoubleOrNull() ?: return@mapNotNull null
        if (c[0].isBlank()) return@mapNotNull null
        City(c[0], c[1], lat, lng, c[4].ifBlank { null })
    }?.toList() ?: emptyList()

/** [added] nach vorn, Duplikate entfernt, auf [max] gekürzt. Identität über die
 *  KOORDINATEN, nicht über den Namen: gleichnamige Orte gibt es wirklich
 *  (Esenköy in Yalova und in Aydın — genau dafür hat `City` ein `region`-Feld). */
fun withRecentPlace(existing: List<City>, added: City, max: Int = 5): List<City> =
    (listOf(added) + existing.filterNot {
        it.latitude == added.latitude && it.longitude == added.longitude
    }).take(max)

/** Chip-Beschriftung: der bloße Name, außer ein anderer Eintrag heißt genauso —
 *  dann mit Region, damit die Chips unterscheidbar bleiben. */
fun recentPlaceLabel(place: City, all: List<City>): String =
    if (all.count { it.name == place.name } > 1 && !place.region.isNullOrBlank()) {
        "${place.name} · ${place.region}"
    } else {
        place.name
    }
```

- [ ] **Step 4: Wire the field into settings**

In `SettingsRepository.kt`: Feld `val recentPlaces: List<City> = emptyList()` in `AppSettings` ergänzen, `val RECENT_PLACES = stringPreferencesKey("recent_places")` in `Keys`, im `settings`-Flow `recentPlaces = parseRecentPlaces(prefs[Keys.RECENT_PLACES])` und in `save` `it[Keys.RECENT_PLACES] = serializeRecentPlaces(value.recentPlaces)`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest`
Expected: PASS

- [ ] **Step 6: Add the UI affordances**

In `LocationSettings`:

a) Beim Auswählen eines Treffers die Liste pflegen — im `onClick` des `DropdownMenuItem`:

```kotlin
                                    commit {
                                        copy(
                                            city = c.name,
                                            latitude = c.latitude,
                                            longitude = c.longitude,
                                            recentPlaces = withRecentPlace(recentPlaces, c),
                                        )
                                    }
```

b) Autofokus und Leeren-Knopf am Suchfeld:

```kotlin
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
```
Der `trailingIcon`-Block bekommt vor dem Aufklapp-Pfeil:
```kotlin
                        if (city.text.isNotEmpty()) {
                            IconButton(onClick = { city = TextFieldValue(""); expanded = true }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.city_clear))
                            }
                        }
```
und das Feld `.focusRequester(focusRequester)` im Modifier.

Falls `ic_close` in `app/src/main/res/drawable/` fehlt: eine 24dp-Vektorgrafik mit dem Material-„close"-Pfad anlegen (`M19,6.41 17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z`), Namensschema wie `ic_chevron_left`.

c) Letzte Orte bei leerem Feld, direkt unter dem Suchfeld:

```kotlin
            if (city.text.isBlank() && settings.recentPlaces.isNotEmpty()) {
                Text(
                    stringResource(R.string.city_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.recentPlaces.forEach { c ->
                        FilterChip(
                            selected = c.name == settings.city,
                            onClick = {
                                commit {
                                    copy(
                                        city = c.name, latitude = c.latitude, longitude = c.longitude,
                                        recentPlaces = withRecentPlace(recentPlaces, c),
                                    )
                                }
                            },
                            label = { Text(c.name) },
                        )
                    }
                }
            }
```

d) Koordinaten in einen Aufklapper. Die beiden `OutlinedTextField`s für `lat`/`lng`, die Fehlermeldung und den „Ort übernehmen"-Knopf in einen Block hüllen:

```kotlin
            var manual by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { manual = !manual }) {
                Text(stringResource(if (manual) R.string.coords_hide else R.string.coords_show))
            }
            if (manual) {
                // … bestehende lat/lng-Felder, Fehlertext und Übernehmen-Knopf …
            }
```

Neue Strings:

```xml
    <string name="city_clear">Eingabe löschen</string>
    <string name="city_recent">Zuletzt gewählt</string>
    <string name="coords_show">Koordinaten manuell eingeben</string>
    <string name="coords_hide">Manuelle Eingabe schließen</string>
```

- [ ] **Step 7: Verify on device**

Expected: Sheet öffnet mit Fokus im Suchfeld und offener Tastatur; bei leerem Feld erscheinen die letzten Orte als Chips; ein Tipp darauf wechselt den Ort sofort; das `X` leert das Feld; Koordinatenfelder sind eingeklappt. Nach zwei Ortswechseln steht der zuletzt gewählte Ort vorn. Screenshot.

Achtung Regression: die Kommentare in `LocationSettings:1169-1172` warnen vor einem IME-Echo-Race beim Fokussieren. Der Entwurf startet weiterhin **leer** — das nicht ändern. Wenn der Autofokus flackert, `LaunchedEffect` durch `delay(150)` vor `requestFocus()` entschärfen.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/data/RecentPlaces.kt app/src/test/kotlin/de/gebetszeiten/data/RecentPlacesTest.kt app/src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt app/src/main/res/
git commit -m "feat(ui): letzte Orte, Autofokus, Leeren-Knopf, Koordinaten eingeklappt"
```

---

### Task 11: Statuszeile mit „Jetzt aktualisieren"

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/PrayerViewModel.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (Aufrufstelle von `LocationSettings` — die Signatur bekommt den neuen `onRefresh`-Parameter)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `OfficialTimesCache.status`, `officialStatusText` (Task 5), `DiyanetPlaceIndex.nearest` (Task 3).
- Produces:
  - `PrayerViewModel.refreshOfficialNow()`
  - `internal fun LocationSettings(settings: AppSettings, onApply: (AppSettings) -> Unit, onRefresh: () -> Unit)` — erweitert die Signatur aus Task 8 um den dritten Parameter

- [ ] **Step 1: Add the refresh entry point**

In `PrayerViewModel`:

```kotlin
    /** Manueller Abruf aus den Einstellungen — schreibt Zeitstempel und
     *  Fehlergrund in den Cache, den die Statuszeile liest. */
    fun refreshOfficialNow() {
        viewModelScope.launch {
            val value = repository.current()
            de.gebetszeiten.prayer.PrayerProvider.refreshOfficial(getApplication(), value)
            reschedule(value)
        }
    }
```

- [ ] **Step 2: Render the status section**

In `LocationSettings` unterhalb des Ort-Abschnitts, als eigener `SettingsSection`. `LocationSettings` bekommt dafür einen zweiten Callback-Parameter `onRefresh: () -> Unit` (Aufrufstelle in `MainActivity`: `onRefresh = { viewModel.refreshOfficialNow() }`).

```kotlin
        SettingsSection(stringResource(R.string.settings_section_source)) {
            var reloads by remember { mutableIntStateOf(0) }
            val statusText by produceState<String?>(null, settings, reloads) {
                val status = de.gebetszeiten.official.OfficialTimesCache(context)
                    .status(settings.latitude, settings.longitude)
                val name = de.gebetszeiten.official.DiyanetPlaceIndex
                    .nearest(context, settings.latitude, settings.longitude)
                    ?.displayName()
                value = de.gebetszeiten.prayer.officialStatusText(status, name)
            }
            Text(
                statusText ?: stringResource(R.string.status_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.useOnline && !settings.useCalculated) {
                OutlinedButton(
                    onClick = { onRefresh(); reloads++ },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.status_refresh))
                }
            }
        }
```

Neue Strings:

```xml
    <string name="settings_section_source">Zeitenquelle</string>
    <string name="status_loading">Status wird geladen…</string>
    <string name="status_refresh">Jetzt aktualisieren</string>
```

- [ ] **Step 3: Verify on device**

Expected (Serdivan, online): Statuszeile nennt „Quelle: amtliche Diyanet-Zeiten · Sakarya (ID 9807)", „Abgedeckt bis: 31.12.2026", „Letzter Abruf: <Zeitstempel>". Flugmodus an, Stadt „Kairo" wählen, „Jetzt aktualisieren" → Zeile zeigt „Fehler: …" und die Zeiten bleiben die Berechnung (kein Fremdort — Stempelschutz). Screenshots beider Zustände.

- [ ] **Step 4: Run the suites**

Run: `.\gradlew.bat :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/ app/src/main/res/values/strings.xml
git commit -m "feat(ui): Statuszeile Zeitenquelle mit Zeitstempel, Fehlergrund, Neuabruf"
```

---

### Task 12: Snackbar bei Zustandswechsel, Strings, Changelog, Abschlusscheck

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `fastlane/metadata/android/de-DE/changelogs/<neu>.txt`, `fastlane/metadata/android/en-US/changelogs/<neu>.txt`
- Modify: `app/build.gradle.kts` (versionCode/versionName)

- [ ] **Step 1: Show the snackbar only on transition**

Geprüfter Ausgangszustand: `MainActivity.kt:190` hat ein `Scaffold`, aber **keinen**
`SnackbarHost`; `HeuteContent` wird bei `:225` als `HeuteContent(inner, settings)`
aufgerufen. Der State wird deshalb beim `Scaffold` gehalten und **hineingegeben** —
nicht in `HeuteContent` deklariert, sonst hat der Host keinen Zugriff darauf.

**(a) Beim `Scaffold` (`:190`)** den State anlegen und den Host anmelden:

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // … bestehende Parameter unverändert …
    ) { inner ->
```

**(b) Aufruf bei `:225`** um den State erweitern:

```kotlin
            Tab.HEUTE -> HeuteContent(inner, settings, snackbarHostState)
```

**(c) Signatur von `HeuteContent`** (`:244`) erweitern:

```kotlin
private fun HeuteContent(
    inner: PaddingValues,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
) {
```

**(d) In `HeuteContent`**, nach der `officialName`-Deklaration:

```kotlin
    // Nur beim WECHSEL melden, nicht bei jedem Start — sonst ist es Lärm.
    // lastReported startet null, deshalb loest der erste beobachtete Wert
    // (App-Start) keine Meldung aus, ein spaeterer Ortswechsel schon.
    var lastReported by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(officialName) {
        val name = officialName
        if (name != null && lastReported != null && name != lastReported) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.snackbar_official_loaded, name),
            )
        }
        lastReported = name
    }
```

Imports ergänzen: `androidx.compose.material3.SnackbarHost`,
`androidx.compose.material3.SnackbarHostState`.

String:

```xml
    <string name="snackbar_official_loaded">Amtliche Zeiten für %1$s geladen</string>
```

- [ ] **Step 2: Bump version and write changelogs**

Höchsten vorhandenen Changelog-Namen ermitteln (`ls fastlane/metadata/android/de-DE/changelogs/`), `versionCode` in `app/build.gradle.kts` um 1 erhöhen, `versionName` auf die nächste Patch-Version. Changelog-Datei nach dem neuen `versionCode` benennen.

`de-DE`:
```
• Amtliche Diyanet-Zeiten jetzt auch für Orte ohne eigenen Diyanet-Eintrag
• Ortssuche zeigt vorab, welche Quelle ein Ort liefert
• Neue Statuszeile: Quelle, Abdeckung, letzter Abruf, Fehlergrund
• Zuletzt gewählte Orte, Suchfeld mit Fokus und Löschknopf
```

`en-US`:
```
• Official Diyanet times now also for places without their own Diyanet entry
• Place search shows which source a place will use
• New status line: source, coverage, last fetch, error reason
• Recent places, search field with focus and clear button
```

- [ ] **Step 3: Full verification**

Run:
```bash
.\gradlew.bat :core-prayertimes:test :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest
```
Expected: alle grün.

Run: `.\gradlew.bat :app:assembleOnlineRelease :app:assembleOfflineRelease`
Expected: BUILD SUCCESSFUL. APK-Größe des offline-Flavors prüfen: sie darf **nicht** gewachsen sein (der Index liegt nur im online-Flavor).

- [ ] **Step 4: Device acceptance run**

Alle in einem Durchgang, mit Screenshots:
1. Serdivan → Footer „Amtliche Diyanet-Zeiten · Sakarya", Isha **21:36**
2. Suche „Serdivan" → Badge „Amtlich · Sakarya, 2 km"
3. Suche „Wien" → Badge „Berechnet"
4. Statuszeile zeigt ID 9807 und Zeitstempel
5. Ortswechsel Nürnberg → Serdivan: Snackbar erscheint einmal
6. Neustart der App: **keine** Snackbar (kein Wechsel)
7. offline-Flavor: „Serdivan" → „Berechnet", „Nürnberg" → „Amtlich · Nürnberg"

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/main/res/values/strings.xml app/build.gradle.kts fastlane/
git commit -m "feat(ui): Snackbar bei Quellenwechsel plus Release-Vorbereitung"
```

---

## Selbstreview des Plans

**Spec-Abdeckung** — jede Spec-Komponente hat einen Task:

| Spec-Abschnitt | Task |
|---|---|
| 1 Standortindex-Pipeline | 2 |
| 2 Asset `locations-world.tsv` | 2 |
| 3 Lookup-Modul (`DiyanetPlace`) | 1 |
| 4 Auflösungskette | 4 (Index-Provider: 3) |
| 5 Ortssuche (Datei-Split, Badges, Recents, Aufklapper) | 8, 9, 10 |
| 6.1 Footer | 6 |
| 6.2 Statuszeile | 5 (Daten), 11 (UI) |
| 6.3 Snackbar | 12 |
| 6 Logging der stillen `null` | 4 |
| Edge Cases | 1 (Schwelle), 2 (Provinzregel, leere Länder), 3 (offline No-op), 5 (Netzfehler) |
| Tests | in jedem Task, Regressionswächter in 2 |

**Bewusste Abweichung:** `DiyanetPlaces.search(query, limit)` aus der Spec wird nicht gebaut (kein Aufrufer — YAGNI). In Task 1 dokumentiert.

**Reihenfolge-Abhängigkeiten:** 1 → 2 → 3 → 4 (danach ist der Bug behoben und auf dem Gerät prüfbar). 5 → 11. 7 → 9. 8 vor 9/10/11 (der Datei-Split muss vor den UI-Erweiterungen liegen, sonst wandern Änderungen mitten im Move). 12 zuletzt.

**Typkonsistenz geprüft:** `DiyanetPlace` (Task 1) wird in 3, 4, 6, 7, 9, 11 identisch verwendet; `displayName()` überall als Extension; `OfficialStatus`-Felder in 5 definiert und in 5/11 gelesen; `City` in 10 mit dem bestehenden fünffeldrigen Konstruktor aus `Cities.kt`.
