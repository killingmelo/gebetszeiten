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


def load_city_aliases() -> dict:
    """{Name}|{ISO2} -> [normalisierte Aliasnamen], gespiegelt aus
    fetch_diyanet.py.load_city_index(). Faengt Faelle auf, in denen cities.tsv
    einen englischen Exonym fuehrt (z.B. 'Nuremberg'), Diyanet aber den
    deutschen Namen ('NURNBERG')."""
    aliases: dict = collections.defaultdict(list)
    for line in (ASSETS / "city-aliases.tsv").read_text(encoding="utf-8").splitlines():
        c = line.split("\t")
        if len(c) < 3 or not c[0].strip():
            continue
        aliases[f"{c[1]}|{c[2]}"].append(normalize(c[0]))
    return aliases


def load_city_index() -> tuple[dict, dict]:
    """(name_index, region_index) ueber ALLE Laender.

    name_index:   (iso2, normName) -> (lat, lng)
    region_index: (iso2, normAdmin1) -> [(lat, lng), ...] in Asset-Reihenfolge
                  (cities.tsv ist populationssortiert -> [0] = groesste Stadt)
    Spalten: name, ascii, iso2, lat, lng, admin1
    """
    aliases = load_city_aliases()
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
        for n in {normalize(c[0]), normalize(c[1]), *aliases.get(f"{c[0]}|{c[2]}", [])}:
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
