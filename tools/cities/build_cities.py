#!/usr/bin/env python3
"""Erzeugt app/src/main/assets/cities.tsv aus einem GeoNames-Extrakt.

Ablauf: cities500.zip (oder --source cities1000) + admin1CodesASCII.txt laden
-> optionale Muss-Orte aus extra-places.tsv ueber die Laender-Dumps aufloesen
-> dedupen -> nach Einwohnerzahl absteigend sortieren (Asset-Reihenfolge =
Suchrang in Cities.kt) -> 6-spaltiges TSV schreiben:
    name \t asciiname \t ISO2 \t lat \t lng \t admin1Name
Eingebaute Pruefungen (Skript bricht hart ab): Sentinel-Orte vorhanden,
alle Alias-Ziele aus city-aliases.tsv weiterhin aufloesbar.
Einmal pro Jahr manuell ausfuehren (siehe README.md in diesem Ordner).
Datenquelle: GeoNames (CC BY 4.0, https://www.geonames.org).
"""
import argparse
import io
import sys
import time
import unicodedata
import urllib.request
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / "app" / "src" / "main" / "assets"
CACHE = Path(__file__).resolve().parent / "cache"
EXTRAS = Path(__file__).resolve().parent / "extra-places.tsv"

DUMP_BASE = "https://download.geonames.org/export/dump/"
HEADERS = {"User-Agent": "GebetszeitenApp-Datenpipeline (jaehrlich)"}

# Sentinel-Orte: (normalisierter Name, ISO2, lat, lng, Toleranz Grad).
# Schlaegt einer fehl, ist das Asset unbrauchbar -> harter Abbruch.
SENTINELS = [
    ("esenkoy", "TR", 40.617, 28.957, 0.15),
    ("cinarcik", "TR", 40.643, 29.121, 0.15),
    # GeoNames-Hauptname ist "Nuremberg"; "Nürnberg" kommt via city-aliases.tsv.
    ("nuremberg", "DE", 49.452, 11.077, 0.15),
    ("istanbul", "TR", 41.014, 28.950, 0.30),
]


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
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=120) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - retry-all ist hier gewollt
            if attempt == 2:
                raise
            print(f"  Retry {attempt + 1} nach Fehler: {e}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    raise AssertionError("unreachable")


def cached(filename: str) -> bytes:
    """Laedt eine Dump-Datei, mit Datei-Cache in tools/cities/cache."""
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / filename
    if path.exists() and path.stat().st_size > 0:
        return path.read_bytes()
    print(f"Lade {DUMP_BASE}{filename} …")
    data = fetch(DUMP_BASE + filename)
    path.write_bytes(data)
    return data


def read_dump_lines(zip_bytes: bytes, member: str):
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as z:
        with z.open(member) as f:
            for raw in io.TextIOWrapper(f, encoding="utf-8"):
                yield raw.rstrip("\n")


def parse_row(line: str) -> dict | None:
    """Eine Zeile des 19-spaltigen GeoNames-Dumps -> dict (oder None)."""
    c = line.split("\t")
    if len(c) < 16:
        return None
    try:
        return {
            "geonameid": int(c[0]),
            "name": c[1].strip(),
            "asciiname": c[2].strip(),
            "lat": float(c[4]),
            "lng": float(c[5]),
            "fclass": c[6].strip(),
            "fcode": c[7].strip(),
            "country": c[8].strip(),
            "admin1": c[10].strip(),
            "population": int(c[14] or 0),
        }
    except ValueError:
        return None


def load_admin1_names() -> dict[str, str]:
    """'TR.77' -> 'Yalova' (aus admin1CodesASCII.txt, Spalte 2 = Name)."""
    out: dict[str, str] = {}
    for line in cached("admin1CodesASCII.txt").decode("utf-8").splitlines():
        c = line.split("\t")
        if len(c) >= 2:
            out[c[0]] = c[1].strip()
    return out


def load_extras() -> list[tuple[int, str]]:
    """extra-places.tsv: geonameid \t ISO2 (Kommentarzeilen mit # erlaubt)."""
    if not EXTRAS.exists():
        return []
    out = []
    for line in EXTRAS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        c = line.split("\t")
        if len(c) >= 2:
            out.append((int(c[0]), c[1].strip()))
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", default="cities500", choices=["cities500", "cities1000", "cities5000", "cities15000"],
                    help="GeoNames-Extrakt (Default: cities500)")
    args = ap.parse_args()

    admin1 = load_admin1_names()

    rows: dict[int, dict] = {}
    for line in read_dump_lines(cached(f"{args.source}.zip"), f"{args.source}.txt"):
        r = parse_row(line)
        if r and r["name"]:
            rows[r["geonameid"]] = r
    print(f"{args.source}: {len(rows)} Orte")

    # Muss-Orte aus den Laender-Dumps nachladen (nur falls noch nicht enthalten).
    for geonameid, country in load_extras():
        if geonameid in rows:
            continue
        found = None
        for line in read_dump_lines(cached(f"{country}.zip"), f"{country}.txt"):
            if line.startswith(f"{geonameid}\t"):
                found = parse_row(line)
                break
        if not found:
            sys.exit(f"FEHLER: Muss-Ort geonameid={geonameid} nicht im {country}-Dump gefunden.")
        rows[geonameid] = found
        print(f"Extra: {found['name']} ({country}, geonameid {geonameid}) ergaenzt")

    # Dedupe: gleicher normalisierter Name + Land + ~1-km-Zelle -> Zeile mit
    # hoeherer Einwohnerzahl gewinnt (stabil via geonameid als Tie-Break).
    best: dict[tuple[str, str, float, float], dict] = {}
    for r in rows.values():
        key = (normalize(r["name"]), r["country"], round(r["lat"], 2), round(r["lng"], 2))
        cur = best.get(key)
        if cur is None or (r["population"], -r["geonameid"]) > (cur["population"], -cur["geonameid"]):
            best[key] = r
    result = sorted(best.values(), key=lambda r: (-r["population"], r["geonameid"]))
    print(f"Nach Dedupe: {len(result)} Orte")

    lines = []
    for r in result:
        region = admin1.get(f"{r['country']}.{r['admin1']}", "") if r["admin1"] else ""
        lines.append(f"{r['name']}\t{r['asciiname']}\t{r['country']}\t{r['lat']:.5f}\t{r['lng']:.5f}\t{region}")
    out_text = "\n".join(lines) + "\n"

    # --- Pruefung 1: Sentinels ---
    # Index ueber Name UND ASCII-Name — GeoNames fuehrt z.B. "Nuremberg" als
    # Hauptnamen, gesucht wird aber (wie in Cities.kt) ueber beide Felder.
    by_norm: dict[tuple[str, str], list[dict]] = {}
    for r in result:
        by_norm.setdefault((normalize(r["name"]), r["country"]), []).append(r)
        ascii_key = (normalize(r["asciiname"]), r["country"])
        if r["asciiname"] and ascii_key != (normalize(r["name"]), r["country"]):
            by_norm.setdefault(ascii_key, []).append(r)
    for norm_name, country, lat, lng, tol in SENTINELS:
        hits = [r for r in by_norm.get((norm_name, country), [])
                if abs(r["lat"] - lat) <= tol and abs(r["lng"] - lng) <= tol]
        if not hits:
            sys.exit(f"FEHLER: Sentinel '{norm_name}' ({country}) fehlt oder liegt falsch.")
    print("Sentinels ok:", ", ".join(s[0] for s in SENTINELS))

    # --- Pruefung 2: Alias-Ziele aus city-aliases.tsv weiterhin aufloesbar ---
    alias_keys = set()
    for line in (ASSETS / "city-aliases.tsv").read_text(encoding="utf-8").splitlines():
        c = line.split("\t")
        if len(c) >= 3 and c[0].strip():
            alias_keys.add((normalize(c[1]), c[2].strip()))
    missing = [k for k in alias_keys if k not in by_norm]
    if missing:
        sys.exit(f"FEHLER: Alias-Ziele ohne Treffer im neuen Asset: {missing}")
    print(f"Alias-Ziele ok ({len(alias_keys)} geprueft)")

    out_path = ASSETS / "cities.tsv"
    out_path.write_text(out_text, encoding="utf-8", newline="\n")
    print(f"Geschrieben: {out_path} ({len(result)} Zeilen, {len(out_text.encode('utf-8')) / 1e6:.2f} MB)")


if __name__ == "__main__":
    main()
