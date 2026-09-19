#!/usr/bin/env python3
"""Holt amtliche Diyanet-Tabellen fuer alle deutschen Standorte.

Ablauf: Standortliste vom Community-Proxy -> pro Standort Jahresseite von
namazvakitleri.diyanet.gov.tr (Rate-Limit 1s, Roh-HTML gecacht/resumierbar)
-> Koordinaten via cities.tsv + city-aliases.tsv -> Dedupe identischer
Tabellen -> TSV-Assets + coverage.tsv. Manuell ausfuehren (siehe README).

WAS DIE JAHRESSEITE WIRKLICH LIEFERT (am 19.09.2026 nachgemessen)
-----------------------------------------------------------------
Sie traegt ZWEI Tabellen, und dazwischen liegt ein Loch:

    tab-1   31 Tage ab heute          2026-09-19 .. 2026-10-19
    tab-2   das naechste Kalenderjahr 2027-01-01 .. 2027-12-31
    ---     nie ausgeliefert          2026-10-20 .. 2026-12-31

Dieses Skript liest `tab-2`, also das naechste Kalenderjahr. Die 403 Zeilen,
die die Seite insgesamt zeigt, wurden frueher fuer ein zusammenhaengendes
"rollierendes 16-Monats-Fenster" gehalten — sie sind es nicht.

Daraus folgt die wichtigste Regel im Umgang mit diesem Skript: seine Ausgabe
ERSETZT den vorhandenen Jahrgang nicht, sie ERGAENZT ihn. Den Rest des
laufenden Jahres gibt die Seite nie wieder her; wer ihn wegwirft, hat ihn
verloren. Genau das ist in Commit 647fd0d passiert ("Jahrgang 2027 loest 2026
ab") — danach begann die Reserve erst am 01.01.2027, und bis dahin hatte die
offline-Variante fuer keinen Ort eine amtliche Zeit. Zusammengefuehrt wird mit
`merge_bundles.py`; `OfficialAssetsIntegrityTest.coverageHasAlreadyBegun`
faellt, wenn es jemand wieder vergisst.

Eingebaute Pruefung (Skript bricht hart ab): jeder Standort muss JEDEN Tag
vom Stichtag bis zum letzten GEMEINSAMEN Tag aller Standorte LUECKENLOS
abdecken. Der Stichtag ist per Default der spaeteste Anfang, den die Seiten
hergeben (also praktisch der 1. Januar des Folgejahres); `--from` setzt ihn
von Hand. Nicht die Jahreszahl ist der Wert dieser Pruefung, sondern die
Lueckenlosigkeit: ein halbes Fenster im Bundle waere schlimmer als ein altes,
weil es niemandem auffaellt.

Dateibenennung: `tables/t###-<lauf-id>.tsv`, wobei <lauf-id> das Abrufdatum
ist (YYYYMMDD) — ein Lauf, kein Kalenderjahr. Dieselbe Kennung steht als
`tableRef` in `locations-de.tsv`, Index und Tabellen stammen also immer aus
demselben Lauf. Das ist wichtig, weil die Kennungen t000, t001, … bei JEDEM
Lauf neu nach Inhaltsreihenfolge vergeben werden: `t005` bedeutet nach einem
neuen Lauf einen anderen Ort.
"""
import argparse
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


# Eine Jahresseite traegt ein rollierendes Fenster von reichlich 365 Tagen.
# Deutlich weniger heisst: die Antwort kam unvollstaendig an.
MIN_PLAUSIBLE_ROWS = 300


def _plausible(html: str) -> bool:
    """Ist das eine ganze Jahresseite — oder ein abgebrochener Download?

    Am 11.09.2026 kam eine Seite mit 166 KB statt der ueblichen 424 KB an,
    mit HTTP 200 und ohne Ausnahme. Sie landete im Cache, und JEDER weitere
    Lauf scheiterte danach an ihr ("0 Zeilen fuer 365 erwartete Tage"), bis
    jemand die Datei von Hand loeschte. Deshalb wird hier geprueft, bevor
    gecacht wird — und ein schon vorhandener Schrott-Cache heilt sich selbst.
    """
    try:
        return len(parse_year_table(html)) >= MIN_PLAUSIBLE_ROWS
    except Exception:  # noqa: BLE001 - jeder Parse-Fehler heisst "unbrauchbar"
        return False


def year_page(location_id: int) -> str:
    CACHE.mkdir(exist_ok=True)
    cached = CACHE / f"{location_id}.html"
    if cached.exists():
        html = cached.read_text(encoding="utf-8")
        if _plausible(html):
            return html
        print(f"  Cache unbrauchbar ({len(html)} Zeichen), hole neu: {cached.name}", file=sys.stderr)
        cached.unlink()
    # Eigene Wiederholung: `fetch` faengt nur Ausnahmen, ein abgeschnittener
    # Koerper kommt als sauberes HTTP 200 zurueck.
    for attempt in range(3):
        html = fetch(PAGE_URL.format(id=location_id)).decode("utf-8")
        time.sleep(1.0)  # Rate-Limit gegenueber diyanet.gov.tr
        if _plausible(html):
            cached.write_text(html, encoding="utf-8")
            return html
        print(f"  Unvollstaendige Seite ({len(html)} Zeichen) fuer id={location_id}, "
              f"Versuch {attempt + 1}/3", file=sys.stderr)
        time.sleep(5 * (attempt + 1))
    raise ValueError(
        f"Seite fuer id={location_id} kam dreimal unvollstaendig an "
        f"(< {MIN_PLAUSIBLE_ROWS} Zeilen). Nicht gecacht."
    )


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


def days_between(start: datetime.date, end: datetime.date) -> set[str]:
    """{'2026-09-19', ...} — jeder Tag von start bis end (beide inklusive)."""
    return {(start + datetime.timedelta(days=i)).isoformat()
            for i in range((end - start).days + 1)}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    # Kein --year mehr: was die Seite hergibt, ist das Produkt, nicht ein
    # Kalenderjahr, das man ihr abringt. Der Stichtag wird per Default aus den
    # Daten abgeleitet (spaetester Anfang ueber alle Standorte), statt ihn zu
    # raten — ein geratenes Jahr war der urspruengliche Fehler.
    ap.add_argument("--from", dest="start", type=datetime.date.fromisoformat, default=None,
                    help="Stichtag (ISO, Default: spaetester Anfang in den Daten). "
                         "Ab hier muss jeder Standort lueckenlos abdecken.")
    ap.add_argument("--run-id", default=None,
                    help="Suffix der Tabellendateien, acht Ziffern "
                         "(Default: Abrufdatum YYYYMMDD)")
    ap.add_argument("--limit", type=int, default=0, help="nur N Standorte (Smoke-Test)")
    ap.add_argument("--out-dir", type=Path, default=OUT_DIR,
                    help="Ausgabeverzeichnis (Default: shared-assets/official); "
                         "fuer Rauchtests auf ein temporaeres Verzeichnis zeigen")
    args = ap.parse_args()

    today = datetime.date.today()
    run_id: str = args.run_id or today.strftime("%Y%m%d")
    if not re.fullmatch(r"\d{8}", run_id):
        sys.exit(f"FEHLER: --run-id muss acht Ziffern haben (YYYYMMDD), bekommen: {run_id!r}. "
                 f"Der Waechtertest `onlyOneVintageIsBundled` erkennt Jahrgaenge an "
                 f"genau dieser Form.")
    out_dir: Path = args.out_dir
    tables_dir = out_dir / "tables"
    print(f"Lauf-Kennung {run_id} -> {out_dir}")

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

    # Erst holen und parsen, dann erst beschneiden: der letzte abgedeckte Tag
    # ist der letzte GEMEINSAME Tag aller Standorte, und den kennt man erst,
    # wenn alle geholt sind. (Speicher: ~950 Standorte x ~470 Zeilen, das sind
    # ein paar Dutzend MB — vertretbar gegen einen zweiten Durchlauf.)
    harvest: list[tuple[dict, str, float, float, list[tuple[str, list[str]]]]] = []
    skipped: list[str] = []
    for n, (loc, (name, lat, lng)) in enumerate(sorted(matched, key=lambda m: m[0]["id"]), 1):
        print(f"[{n}/{len(matched)}] {name} (id={loc['id']})")
        try:
            page_rows = parse_year_table(year_page(loc["id"]))
        except Exception as e:
            print(f"  SKIP {name} (id={loc['id']}): {e}", file=sys.stderr)
            skipped.append(name)
            continue

        if not page_rows:
            sys.exit(
                f"FEHLER: {name} (Diyanet-id {loc['id']}) liefert keine einzige Zeile.\n"
                f"  Haelt der Cache noch eine kaputte Seite? {CACHE} loeschen und neu holen."
            )
        harvest.append((loc, name, lat, lng, page_rows))

    if not harvest:
        sys.exit("FEHLER: kein einziger Standort lieferte eine Tabelle - nichts geschrieben.")

    # Der gemeinsame Zeitraum: der spaeteste Anfang und das frueheste Ende.
    # Beides aus den Daten, nicht geraten. Wer frueher endet oder spaeter
    # anfaengt, zieht alle mit — das darf passieren, aber nicht unbemerkt.
    first_per_loc = [(min(d for d, _ in rows), name) for _, name, _, _, rows in harvest]
    latest_start_iso = max(d for d, _ in first_per_loc)
    start = args.start or datetime.date.fromisoformat(latest_start_iso)
    start_iso = start.isoformat()
    if start_iso != min(d for d, _ in first_per_loc):
        spaet = sorted({(d, n) for d, n in first_per_loc if d > min(d for d, _ in first_per_loc)})
        print(f"\nACHTUNG: {len(spaet)} Standorte fangen spaeter an als die uebrigen "
              f"und schieben den Stichtag auf {latest_start_iso}:")
        for d, n in spaet[:10]:
            print(f"  {n}: beginnt {d}")

    last_per_loc = [(max(d for d, _ in rows), name) for _, name, _, _, rows in harvest]
    end_iso = min(d for d, _ in last_per_loc)
    end = datetime.date.fromisoformat(end_iso)
    latest_iso = max(d for d, _ in last_per_loc)
    if end_iso != latest_iso:
        short = sorted({(d, n) for d, n in last_per_loc if d < latest_iso})
        print(f"\nACHTUNG: {len(short)} Standorte enden vor dem spaetesten Tag {latest_iso} "
              f"und beschneiden das Fenster auf {end_iso}:")
        for d, n in short[:10]:
            print(f"  {n}: endet {d}")
    if end < start:
        sys.exit(f"FEHLER: letzter gemeinsamer Tag {end_iso} liegt vor dem Stichtag {start_iso}.")

    wanted_days = days_between(start, end)
    print(f"\nFenster {start_iso} bis {end_iso}: {len(wanted_days)} Tage erwartet")

    # Dedupe: identischer Tabelleninhalt -> eine Datei.
    content_to_ref: dict[str, str] = {}
    index_rows: list[tuple[int, str, float, float, str]] = []
    for loc, name, lat, lng, rows in harvest:
        rows = [(d, t) for d, t in rows if start_iso <= d <= end_iso]
        missing = sorted(wanted_days - {d for d, _ in rows})
        if missing or len(rows) != len(wanted_days):
            # Harter Abbruch (Muster: build_cities.py). Ein lueckenhaftes
            # Fenster im Bundle merkt niemand — ein abgebrochener Lauf schon.
            sys.exit(
                f"FEHLER: {name} (Diyanet-id {loc['id']}) deckt {start_iso} bis {end_iso} "
                f"nicht lueckenlos ab: {len(rows)} Zeilen fuer {len(wanted_days)} erwartete "
                f"Tage, {len(missing)} Tage fehlen"
                + (f" (erster {missing[0]}, letzter {missing[-1]})" if missing else "")
                + f".\n  Falls der Cache noch ein aelteres Fenster haelt: "
                f"{CACHE} loeschen und neu holen."
            )

        content = "".join(f"{d}\t" + "\t".join(t) + "\n" for d, t in rows)
        ref = content_to_ref.setdefault(content, f"t{len(content_to_ref):03d}-{run_id}")
        index_rows.append((loc["id"], name, lat, lng, ref))

    tables_dir.mkdir(parents=True, exist_ok=True)
    total = 0
    for content, ref in content_to_ref.items():
        p = tables_dir / f"{ref}.tsv"
        p.write_text(content, encoding="utf-8", newline="\n")
        total += len(zlib.compress(content.encode("utf-8"), 9))
    index_text = "".join(f"{i}\t{n}\t{lat}\t{lng}\t{r}\n" for i, n, lat, lng, r in index_rows)
    (out_dir / "locations-de.tsv").write_text(index_text, encoding="utf-8", newline="\n")

    # coverage.tsv: erster und letzter abgedeckter Tag, eine Zeile, Tab-getrennt,
    # kein Header — im Ton von locations-de.tsv. Gelesen vom Integritaetstest
    # (Stolperdraht) und von der App. Die Vollstaendigkeitspruefung oben hat
    # fuer JEDEN Standort bewiesen, dass genau diese Tage geschrieben wurden —
    # deshalb darf die Abdeckung hier aus wanted_days kommen.
    (out_dir / "coverage.tsv").write_text(
        f"{start_iso}\t{end_iso}\n", encoding="utf-8", newline="\n")

    print(f"\nFenster {start_iso} bis {end_iso} ({len(wanted_days)} Tage) | "
          f"eindeutige Tabellen: {len(content_to_ref)} von {len(index_rows)} Standorten")
    # Die Gesamtgroesse allein sagt nichts: sie waechst mit der Zahl der
    # Standorte, und mehr Standorte sind erwuenscht. Aussagekraeftig ist die
    # Groesse JE STANDORT — bleibt sie stabil, ist ein Zuwachs Abdeckung und
    # kein Ballast. Seit dem rollierenden Fenster waechst sie auch mit der
    # Fensterlaenge — der vergleichbare Wert ist deshalb KB je Standort und
    # 365 Tagen. Erfahrungswerte: 2026 = 621 Orte / 2.71 MB / 4.5 KB je Ort,
    # 2027 = 947 Orte / 4.28 MB / 4.6 KB je Ort (beide ueber 365 Tage).
    per_loc = total / len(index_rows) / 1024 if index_rows else 0.0
    per_loc_year = per_loc * 365 / len(wanted_days)
    print(f"Groesse komprimiert (zlib-9-Schaetzung): {total / 1024 / 1024:.2f} MB "
          f"= {per_loc:.1f} KB je Standort ({per_loc_year:.1f} KB je Standort und 365 Tage) "
          f"+ Index {len(index_text) / 1024:.0f} KB")
    if per_loc_year > 6.0:
        print(f"  ACHTUNG: {per_loc_year:.1f} KB je Standort und Jahr statt der ueblichen "
              f"~4.6 — das waere Ballast, nicht Abdeckung. Tabellenformat pruefen.")
    if skipped:
        print(f"Uebersprungen wegen Fehlern: {len(skipped)}")
        for name in skipped[:20]:
            print(f"  {name}")

    # Tabellen frueherer Laeufe werden NICHT geloescht (Loeschen gehoert in
    # den Commit, nicht in ein Skript), aber sie sollen auffallen: sonst
    # buendelt die App zwei Jahrgaenge und waechst mit jedem Lauf weiter.
    stale = sorted(p.name for p in tables_dir.glob("*.tsv")
                   if not p.name.endswith(f"-{run_id}.tsv"))
    if stale:
        old_ids = sorted({p.rsplit("-", 1)[1][:-4] for p in stale})
        print(f"\nACHTUNG: {len(stale)} Tabellen frueherer Laeufe liegen noch in "
              f"{tables_dir} (z.B. {stale[0]}). Vor dem Commit entfernen:")
        for old in old_ids:
            print(f"  git rm shared-assets/official/tables/t*-{old}.tsv")


if __name__ == "__main__":
    main()
