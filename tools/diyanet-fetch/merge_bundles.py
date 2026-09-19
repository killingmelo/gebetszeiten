#!/usr/bin/env python3
"""Fuehrt zwei Bundle-Jahrgaenge zu einem zusammen, statt einen zu ersetzen.

WARUM ES DIESES SKRIPT GIBT
---------------------------
Die Diyanet-Jahresseite ist ein rollierendes Fenster, aber KEIN
zusammenhaengendes. Sie traegt zwei Tabellen:

    tab-1   31 Tage ab heute          (z.B. 2026-09-19 .. 2026-10-19)
    tab-2   das naechste Kalenderjahr (2027-01-01 .. 2027-12-31)

Dazwischen liegt ein Loch, das die Seite nie herausgibt: der Rest des
LAUFENDEN Jahres ab Tag 32. Wer im September das naechste Jahr holt und den
alten Jahrgang dabei ersetzt, wirft Tage weg, die nicht wieder zu beschaffen
sind. Genau das ist in Commit 647fd0d passiert ("Jahrgang 2027 loest 2026
ab"): danach begann die Reserve erst am 01.01.2027, und bis dahin hatte die
offline-Variante fuer keinen Ort eine einzige amtliche Zeit.

Deshalb gilt ab jetzt: ein neuer Jahrgang wird ERGAENZT, nicht ersetzt.

WIE ZUSAMMENGEFUEHRT WIRD
-------------------------
Ueber die Diyanet-Standort-Kennung, die ueber Laeufe hinweg stabil ist - NICHT
ueber die Tabellenkennung `t###`. Die vergibt `fetch_diyanet.py` bei jedem Lauf
neu nach Inhaltsreihenfolge; `t005` bedeutet nach einem neuen Lauf im Zweifel
einen anderen Ort. Aus demselben Grund schreibt dieses Skript fuer das Ergebnis
frische Kennungen mit EINER Lauf-Id: hinterher liegt wieder genau ein Jahrgang
im Verzeichnis, und `locations-de.tsv` und `tables/` stammen zwangslaeufig aus
demselben Vorgang.

Bei Ueberschneidung gewinnt `--overlay`. Amtliche Zeiten werden korrigiert; der
neuere Abruf ist der naehere an der Wahrheit.

Ein Ort, den nur `--base` kennt, faellt WEG: er stand im neuen Abruf nicht
mehr, und einen Ort, den Diyanet nicht mehr fuehrt, buendeln wir nicht. Ein
Ort, den nur `--overlay` kennt, kommt mit dem kuerzeren Fenster mit, das der
Abruf fuer ihn hergab - eine kurze Reserve ist besser als keine.

AUFRUF
------
    mkdir -p /tmp/alt
    git archive 647fd0d^ shared-assets/official | tar -x -C /tmp/alt --strip-components=2
    python tools/diyanet-fetch/merge_bundles.py \
        --base /tmp/alt --overlay shared-assets/official --out shared-assets/official
"""
import argparse
import datetime
import re
import sys
import zlib
from pathlib import Path


def read_bundle(d: Path) -> dict[int, dict]:
    """{diyanet_id: {name, lat, lng, days}}; days ist {ISO-Datum: Zeiten-Rest-der-Zeile}."""
    index = d / "locations-de.tsv"
    if not index.is_file():
        sys.exit(f"FEHLER: {index} fehlt - ist {d} wirklich ein Bundle-Verzeichnis?")
    tables = d / "tables"
    cache: dict[str, dict[str, str]] = {}
    out: dict[int, dict] = {}
    for line in index.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        loc_id, name, lat, lng, ref = line.split("\t")
        if ref not in cache:
            # Zwei Schreibweisen: alt `t000` + Datei `t000-2026.tsv`,
            # neu `t000-20260919` + Datei gleichen Namens.
            p = tables / f"{ref}.tsv"
            if not p.is_file():
                hits = sorted(tables.glob(f"{ref}-*.tsv"))
                if len(hits) != 1:
                    sys.exit(f"FEHLER: keine eindeutige Tabelle fuer {ref!r} in {tables} "
                             f"(gefunden: {[h.name for h in hits]})")
                p = hits[0]
            cache[ref] = {
                ln.split("\t", 1)[0]: ln.split("\t", 1)[1]
                for ln in p.read_text(encoding="utf-8").splitlines() if ln.strip()
            }
        out[int(loc_id)] = {"name": name, "lat": lat, "lng": lng, "days": cache[ref]}
    return out


def contiguous(days: list[str]) -> bool:
    first = datetime.date.fromisoformat(days[0])
    last = datetime.date.fromisoformat(days[-1])
    return (last - first).days + 1 == len(days)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", type=Path, required=True, help="aelteres Bundle-Verzeichnis")
    ap.add_argument("--overlay", type=Path, required=True,
                    help="neueres Bundle-Verzeichnis; gewinnt bei Ueberschneidung")
    ap.add_argument("--out", type=Path, required=True, help="Zielverzeichnis")
    ap.add_argument("--run-id", default=None,
                    help="Suffix der Tabellen (Default: heute, YYYYMMDD)")
    args = ap.parse_args()

    run_id = args.run_id or datetime.date.today().strftime("%Y%m%d")
    if not re.fullmatch(r"\d{8}", run_id):
        sys.exit(f"FEHLER: --run-id muss acht Ziffern haben (YYYYMMDD), bekommen: {run_id!r}")

    base = read_bundle(args.base)
    overlay = read_bundle(args.overlay)
    print(f"base    {args.base}: {len(base)} Orte")
    print(f"overlay {args.overlay}: {len(overlay)} Orte")
    nur_base = sorted(set(base) - set(overlay))
    if nur_base:
        namen = ", ".join(base[i]["name"] for i in nur_base[:5])
        print(f"{len(nur_base)} Orte nur im base-Jahrgang - sie fallen weg (z.B. {namen})")

    content_to_ref: dict[str, str] = {}
    index_rows: list[tuple[int, str, str, str, str]] = []
    luecken: list[str] = []
    for loc_id in sorted(overlay):
        neu = overlay[loc_id]
        days = dict(base.get(loc_id, {}).get("days", {}))
        days.update(neu["days"])          # overlay gewinnt
        ordered = sorted(days)
        if not contiguous(ordered):
            # Kein Abbruch: genau fuer Loecher gibt es dieses Skript. Aber sichtbar.
            spanne = (datetime.date.fromisoformat(ordered[-1])
                      - datetime.date.fromisoformat(ordered[0])).days + 1
            luecken.append(f"{neu['name']}: {ordered[0]}..{ordered[-1]}, "
                           f"{spanne - len(ordered)} Tage fehlen")
        content = "".join(f"{d}\t{days[d]}\n" for d in ordered)
        ref = content_to_ref.setdefault(content, f"t{len(content_to_ref):03d}-{run_id}")
        index_rows.append((loc_id, neu["name"], neu["lat"], neu["lng"], ref))

    if luecken:
        print(f"\nACHTUNG: {len(luecken)} Orte haben Loecher in ihrer Tabelle:")
        for z in luecken[:10]:
            print(f"  {z}")
        if len(luecken) > 10:
            print(f"  ... und {len(luecken) - 10} weitere")

    tables_dir = args.out / "tables"
    tables_dir.mkdir(parents=True, exist_ok=True)
    total = 0
    for content, ref in content_to_ref.items():
        (tables_dir / f"{ref}.tsv").write_text(content, encoding="utf-8", newline="\n")
        total += len(zlib.compress(content.encode("utf-8"), 9))
    (args.out / "locations-de.tsv").write_text(
        "".join(f"{i}\t{n}\t{lat}\t{lng}\t{r}\n" for i, n, lat, lng, r in index_rows),
        encoding="utf-8", newline="\n")

    # coverage.tsv ist eine Zusage ueber JEDEN gebuendelten Ort, nicht ueber den
    # besten: der spaeteste Anfang und das frueheste Ende. Ein Ort mit laengerem
    # Fenster darf darueber hinaus mehr haben - die App schlaegt ohnehin je Tag
    # nach und bekommt fuer einen nicht abgedeckten Tag `null`.
    tage_je_ref = {ref: [ln.split("\t", 1)[0] for ln in c.splitlines()]
                   for c, ref in content_to_ref.items()}
    firsts = [ds[0] for ds in tage_je_ref.values()]
    lasts = [ds[-1] for ds in tage_je_ref.values()]
    cov_first, cov_last = max(firsts), min(lasts)
    (args.out / "coverage.tsv").write_text(
        f"{cov_first}\t{cov_last}\n", encoding="utf-8", newline="\n")

    heute = datetime.date.today().isoformat()
    mit_heute = sum(1 for ds in tage_je_ref.values() if ds[0] <= heute <= ds[-1])
    orte_mit_heute = sum(1 for _, _, _, _, r in index_rows
                         if tage_je_ref[r][0] <= heute <= tage_je_ref[r][-1])
    print(f"\n{len(index_rows)} Orte, {len(content_to_ref)} eindeutige Tabellen, Lauf {run_id}")
    print(f"Zugesagt fuer JEDEN Ort (coverage.tsv): {cov_first} bis {cov_last}")
    print(f"Frueheste Anfaenge: {min(firsts)} | spaetestes Ende: {max(lasts)}")
    print(f"Tabellen mit Reserve fuer heute ({heute}): {mit_heute} von {len(content_to_ref)}")
    print(f"Orte mit Reserve fuer heute: {orte_mit_heute} von {len(index_rows)}")
    print(f"Groesse komprimiert (zlib-9-Schaetzung): {total / 1024 / 1024:.2f} MB")

    stale = sorted(p.name for p in tables_dir.glob("*.tsv")
                   if not p.name.endswith(f"-{run_id}.tsv"))
    if stale:
        old = sorted({p.rsplit("-", 1)[1][:-4] for p in stale})
        print(f"\nACHTUNG: {len(stale)} Tabellen frueherer Laeufe liegen noch in "
              f"{tables_dir}. Vor dem Commit entfernen:")
        for o in old:
            print(f"  git rm shared-assets/official/tables/t*-{o}.tsv")


if __name__ == "__main__":
    main()
