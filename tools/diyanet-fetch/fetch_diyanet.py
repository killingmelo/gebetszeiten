#!/usr/bin/env python3
"""Holt amtliche Diyanet-Jahrestabellen fuer alle deutschen Standorte.

Ablauf: Standortliste vom Community-Proxy -> pro Standort Jahresseite von
namazvakitleri.diyanet.gov.tr (Rate-Limit 1s, Roh-HTML gecacht/resumierbar)
-> Koordinaten via cities.tsv + city-aliases.tsv -> nur die Zeilen von
--year behalten -> Dedupe identischer Tabellen -> TSV-Assets + coverage.tsv.
Einmal pro Jahr manuell ausfuehren (siehe README).

Warum --year Pflicht ist: die Jahresseite ist ein rollierendes ~16-Monats-
Fenster. Im September liefert sie den Rest des laufenden Jahres UND das
ganze Folgejahr. Frueher nahm das Skript einfach max(Jahr) aus den Daten
und schrieb Dateien namens `-2027.tsv`, in denen 2026er Tage standen, ohne
zu pruefen, ob 2027 ueberhaupt vollstaendig war.

Eingebaute Pruefung (Skript bricht hart ab): jeder Standort muss das
gewaehlte Jahr LUECKENLOS abdecken (365 Tage, im Schaltjahr 366). Ein halbes
Jahr im Bundle waere schlimmer als ein altes, weil es niemandem auffaellt.
"""
import argparse
import calendar
import datetime
import html as html_module
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
OUT_DIR = REPO / "shared-assets" / "official"
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
    cells = [html_module.unescape(c) for c in re.findall(r"<td>\s*([^<]*?)\s*</td>", html[start:end])]
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


def days_of_year(year: int) -> set[str]:
    """{'2026-01-01', ...} — alle Kalendertage des Jahres als ISO-Datum."""
    start = datetime.date(year, 1, 1)
    return {(start + datetime.timedelta(days=i)).isoformat()
            for i in range(366 if calendar.isleap(year) else 365)}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    # Pflicht, kein Default: ein Default waere ab Herbst die Haelfte des Jahres
    # falsch (rollierendes Fenster), und genau diese stille Annahme war der
    # Fehler, den dieses Argument beseitigt. Wer das Bundle baut, sagt wofuer.
    ap.add_argument("--year", type=int, required=True,
                    help="Kalenderjahr, das gebuendelt wird (Pflicht, z.B. 2027)")
    ap.add_argument("--limit", type=int, default=0, help="nur N Standorte (Smoke-Test)")
    ap.add_argument("--out-dir", type=Path, default=OUT_DIR,
                    help="Ausgabeverzeichnis (Default: shared-assets/official); "
                         "fuer Rauchtests auf ein temporaeres Verzeichnis zeigen")
    args = ap.parse_args()

    year = args.year
    out_dir: Path = args.out_dir
    tables_dir = out_dir / "tables"
    wanted_days = days_of_year(year)
    print(f"Zieljahr {year}: {len(wanted_days)} Tage erwartet -> {out_dir}")

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
    skipped: list[str] = []
    for n, (loc, (name, lat, lng)) in enumerate(sorted(matched, key=lambda m: m[0]["id"]), 1):
        print(f"[{n}/{len(matched)}] {name} (id={loc['id']})")
        try:
            page_rows = parse_year_table(year_page(loc["id"]))
        except Exception as e:
            print(f"  SKIP {name} (id={loc['id']}): {e}", file=sys.stderr)
            skipped.append(name)
            continue

        # Nur Zeilen des Zieljahres; alles aus dem Nachbarjahr faellt weg.
        rows = [(d, t) for d, t in page_rows if d.startswith(f"{year}-")]
        missing = sorted(wanted_days - {d for d, _ in rows})
        if missing or len(rows) != len(wanted_days):
            # Harter Abbruch (Muster: build_cities.py). Ein lueckenhaftes Jahr
            # im Bundle merkt niemand — ein abgebrochener Lauf schon.
            sys.exit(
                f"FEHLER: {name} (Diyanet-id {loc['id']}) deckt {year} nicht vollstaendig ab: "
                f"{len(rows)} Zeilen fuer {len(wanted_days)} erwartete Tage, "
                f"{len(missing)} Tage fehlen"
                + (f" (erster {missing[0]}, letzter {missing[-1]})" if missing else "")
                + f".\n  Die Jahresseite ist ein rollierendes Fenster - ist {year} bei Diyanet "
                f"schon vollstaendig publiziert? Falls der Cache noch das alte Fenster haelt: "
                f"{CACHE} loeschen und neu holen."
            )

        content = "".join(f"{d}\t" + "\t".join(t) + "\n" for d, t in rows)
        ref = content_to_ref.setdefault(content, f"t{len(content_to_ref):03d}")
        index_rows.append((loc["id"], name, lat, lng, ref))

    if not index_rows:
        sys.exit("FEHLER: kein einziger Standort lieferte eine Tabelle - nichts geschrieben.")

    tables_dir.mkdir(parents=True, exist_ok=True)
    total = 0
    for content, ref in content_to_ref.items():
        p = tables_dir / f"{ref}-{year}.tsv"
        p.write_text(content, encoding="utf-8", newline="\n")
        total += len(zlib.compress(content.encode("utf-8"), 9))
    index_text = "".join(f"{i}\t{n}\t{lat}\t{lng}\t{r}\n" for i, n, lat, lng, r in index_rows)
    (out_dir / "locations-de.tsv").write_text(index_text, encoding="utf-8", newline="\n")

    # coverage.tsv: erster und letzter abgedeckter Tag, eine Zeile, Tab-getrennt,
    # kein Header — im Ton von locations-de.tsv. Gelesen vom Integritaetstest
    # (Stolperdraht) und von der App. Die Vollstaendigkeitspruefung oben hat
    # fuer JEDEN Standort bewiesen, dass genau diese Tage geschrieben wurden —
    # deshalb darf die Abdeckung hier aus wanted_days kommen.
    covered = sorted(wanted_days)
    (out_dir / "coverage.tsv").write_text(
        f"{covered[0]}\t{covered[-1]}\n", encoding="utf-8", newline="\n")

    print(f"\nJahr {year} ({covered[0]} bis {covered[-1]}) | eindeutige Tabellen: "
          f"{len(content_to_ref)} von {len(index_rows)} Standorten")
    print(f"Groesse komprimiert (zlib-9-Schaetzung): {total / 1024 / 1024:.2f} MB "
          f"(Ziel < 4 MB) + Index {len(index_text) / 1024:.0f} KB")
    if skipped:
        print(f"Uebersprungen wegen Fehlern: {len(skipped)}")
        for name in skipped[:20]:
            print(f"  {name}")

    # Tabellen frueherer Jahrgaenge werden NICHT geloescht (Loeschen gehoert in
    # den Commit, nicht in ein Skript), aber sie sollen auffallen: sonst
    # buendelt die App zwei Jahrgaenge und waechst jedes Jahr weiter.
    stale = sorted(p.name for p in tables_dir.glob("t*.tsv")
                   if not p.name.endswith(f"-{year}.tsv"))
    if stale:
        print(f"\nACHTUNG: {len(stale)} Tabellen frueherer Jahrgaenge liegen noch in "
              f"{tables_dir} (z.B. {stale[0]}). Vor dem Commit entfernen:")
        print(f"  git rm shared-assets/official/tables/t*-{stale[0][5:9]}.tsv")


if __name__ == "__main__":
    main()
