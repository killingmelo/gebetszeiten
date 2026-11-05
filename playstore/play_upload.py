"""Lädt ein Release über die Google Play Developer API hoch (Telefon und Uhr).

Einmalige Einrichtung: siehe playstore/CHECKLISTE-upload.md. Kurz:
  pip install -r playstore/requirements-upload.txt
  Dienstkonto-Schlüssel nach keystore/play-dienstkonto.json (keystore/ ist in .gitignore)

Aufrufe (aus dem Projektordner):
  python playstore/play_upload.py --trocken        nur lokale Prüfung, kein Netz, kein Schlüssel
  python playstore/play_upload.py --pruefen        Zugang testen: Tracks lesen, nichts ändern
  python playstore/play_upload.py                  hochladen, Google prüfen lassen, Bearbeitung verwerfen
  python playstore/play_upload.py --ja             hochladen und übernehmen (Entwurf im internen Test)

Zusätze: --eintrag (Titel, Kurz- und Vollbeschreibung), --bilder (Screenshots, Symbol,
Vorstellungsgrafik), --nur-telefon, --nur-uhr, --track, --uhr-track.

Das Skript übernimmt Releases nur als Entwurf (status "draft"). Freigeben, in die
Produktion bringen und staffeln bleibt Handarbeit in der Play Console.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

PAKET = "de.gebetszeiten"
WURZEL = Path(__file__).resolve().parent.parent
META = WURZEL / "fastlane" / "metadata" / "android"
SCHLUESSEL = WURZEL / "keystore" / "play-dienstkonto.json"
STRICHE = ("\N{EM DASH}", "\N{EN DASH}")

GERAETE = {
    "telefon": {
        "gradle": WURZEL / "app" / "build.gradle.kts",
        "aab": WURZEL / "app" / "build" / "outputs" / "bundle" / "onlineRelease" / "app-online-release.aab",
    },
    "uhr": {
        "gradle": WURZEL / "wear" / "build.gradle.kts",
        "aab": WURZEL / "wear" / "build" / "outputs" / "bundle" / "onlineRelease" / "wear-online-release.aab",
    },
}

# Play-Grenzen für Store-Texte und Versionshinweise.
GRENZEN = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
HINWEIS_GRENZE = 500

# fastlane-Bildordner -> imageType der API.
BILDER = {
    "phoneScreenshots": "phoneScreenshots",
    "sevenInchScreenshots": "sevenInchScreenshots",
    "tenInchScreenshots": "tenInchScreenshots",
    "wearScreenshots": "wearScreenshots",
}
EINZELBILDER = {"icon.png": "icon", "featureGraphic.png": "featureGraphic"}


class Fehler(Exception):
    pass


def lies(p: Path) -> str:
    return p.read_text(encoding="utf-8").strip()


def version(gradle: Path) -> tuple[int, str]:
    s = gradle.read_text(encoding="utf-8")
    code = re.search(r"^\s*versionCode\s*=\s*(\d+)", s, re.M)
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', s, re.M)
    if not code or not name:
        raise Fehler(f"versionCode/versionName nicht gefunden in {gradle}")
    return int(code.group(1)), name.group(1)


def sprachen() -> list[str]:
    return sorted(p.name for p in META.iterdir() if p.is_dir())


def text_pruefen(name: str, text: str, grenze: int) -> list[str]:
    probleme = []
    if len(text) > grenze:
        probleme.append(f"{name}: {len(text)} Zeichen, erlaubt {grenze}")
    if any(s in text for s in STRICHE):
        probleme.append(f"{name}: enthält Gedanken- oder Halbgeviertstrich")
    return probleme


def plan_erstellen(args) -> dict:
    """Alles, was lokal feststeht, mit Prüfung. Wirft Fehler mit allen Problemen."""
    probleme: list[str] = []
    geraete = [g for g in ("telefon", "uhr")
               if not (args.nur_telefon and g == "uhr") and not (args.nur_uhr and g == "telefon")]
    plan = {"geraete": {}, "sprachen": sprachen(), "eintrag": {}, "bilder": {}}

    for g in geraete:
        info = GERAETE[g]
        code, name = version(info["gradle"])
        aab = info["aab"]
        if not aab.is_file():
            probleme.append(f"{g}: Bundle fehlt ({aab.relative_to(WURZEL)})")
        elif aab.stat().st_mtime < info["gradle"].stat().st_mtime:
            probleme.append(f"{g}: Bundle ist älter als {info['gradle'].name}, bitte neu bauen")
        hinweise = {}
        for sp in plan["sprachen"]:
            datei = META / sp / "changelogs" / f"{code}.txt"
            if not datei.is_file():
                probleme.append(f"{g}: Versionshinweis fehlt ({datei.relative_to(WURZEL)})")
                continue
            t = lies(datei)
            probleme += text_pruefen(str(datei.relative_to(WURZEL)), t, HINWEIS_GRENZE)
            hinweise[sp] = t
        plan["geraete"][g] = {
            "code": code, "name": name, "aab": aab, "hinweise": hinweise,
            "track": args.track if g == "telefon" else args.uhr_track,
        }

    if args.eintrag:
        for sp in plan["sprachen"]:
            felder = {}
            for datei, grenze in GRENZEN.items():
                p = META / sp / datei
                if p.is_file():
                    t = lies(p)
                    probleme += text_pruefen(str(p.relative_to(WURZEL)), t, grenze)
                    felder[datei] = t
            plan["eintrag"][sp] = felder

    if args.bilder:
        for sp in plan["sprachen"]:
            ordner = META / sp / "images"
            if not ordner.is_dir():
                continue
            je = {}
            for sub, typ in BILDER.items():
                dateien = sorted((ordner / sub).glob("*.png")) + sorted((ordner / sub).glob("*.jpg"))
                if dateien:
                    if len(dateien) > 8:
                        probleme.append(f"{sp}/{sub}: {len(dateien)} Bilder, erlaubt 8")
                    je[typ] = dateien
            for datei, typ in EINZELBILDER.items():
                if (ordner / datei).is_file():
                    je[typ] = [ordner / datei]
            plan["bilder"][sp] = je

    if probleme:
        raise Fehler("Lokale Prüfung fehlgeschlagen:\n  " + "\n  ".join(probleme))
    return plan


def plan_zeigen(plan: dict) -> None:
    for g, d in plan["geraete"].items():
        mb = d["aab"].stat().st_size / 1e6 if d["aab"].is_file() else 0
        print(f"{g:8} {d['name']} ({d['code']})  {mb:.1f} MB  nach Track {d['track']}")
        for sp, t in d["hinweise"].items():
            print(f"         Was ist neu [{sp}]: {len(t)} Zeichen")
    for sp, felder in plan["eintrag"].items():
        print(f"Eintrag  [{sp}]: " + ", ".join(f"{k} {len(v)}" for k, v in felder.items()))
    for sp, je in plan["bilder"].items():
        print(f"Bilder   [{sp}]: " + ", ".join(f"{k} {len(v)}" for k, v in je.items()))


def dienst(schluessel: Path):
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError:
        raise Fehler("Bibliotheken fehlen: pip install -r playstore/requirements-upload.txt")
    if not schluessel.is_file():
        raise Fehler(f"Schlüssel fehlt: {schluessel} (siehe playstore/CHECKLISTE-upload.md)")
    cred = service_account.Credentials.from_service_account_file(
        str(schluessel), scopes=["https://www.googleapis.com/auth/androidpublisher"])
    return build("androidpublisher", "v3", credentials=cred, cache_discovery=False)


def api_fehler(e) -> str:
    inhalt = getattr(e, "content", b"")
    try:
        import json
        return json.loads(inhalt)["error"]["message"]
    except Exception:
        return str(e)


def tracks_zeigen(edits, edit_id) -> None:
    for t in edits.tracks().list(packageName=PAKET, editId=edit_id).execute().get("tracks", []):
        rel = t.get("releases", [])
        teile = [f"{r.get('name', '?')} {r.get('versionCodes', [])} {r.get('status')}" for r in rel]
        print(f"  {t['track']:22} " + ("; ".join(teile) if teile else "leer"))


def hochladen(svc, plan: dict, args) -> None:
    from googleapiclient.http import MediaFileUpload

    edits = svc.edits()
    edit_id = edits.insert(packageName=PAKET, body={}).execute()["id"]
    uebernommen = False
    try:
        if args.pruefen:
            print("Zugang in Ordnung. Tracks jetzt:")
            tracks_zeigen(edits, edit_id)
            return

        for g, d in plan["geraete"].items():
            print(f"{g}: lade {d['aab'].name} hoch …", flush=True)
            media = MediaFileUpload(str(d["aab"]), mimetype="application/octet-stream", resumable=True)
            code = edits.bundles().upload(packageName=PAKET, editId=edit_id, media_body=media).execute()["versionCode"]
            if code != d["code"]:
                raise Fehler(f"{g}: Bundle hat versionCode {code}, Gradle sagt {d['code']}")
            edits.tracks().update(packageName=PAKET, editId=edit_id, track=d["track"], body={
                "track": d["track"],
                "releases": [{
                    "name": f"{d['name']} ({d['code']})",
                    "versionCodes": [str(d["code"])],
                    "status": "draft",
                    "releaseNotes": [{"language": sp, "text": t} for sp, t in d["hinweise"].items()],
                }],
            }).execute()
            print(f"{g}: {d['name']} ({d['code']}) als Entwurf in {d['track']}")

        for sp, felder in plan["eintrag"].items():
            body = {"language": sp}
            if "title.txt" in felder: body["title"] = felder["title.txt"]
            if "short_description.txt" in felder: body["shortDescription"] = felder["short_description.txt"]
            if "full_description.txt" in felder: body["fullDescription"] = felder["full_description.txt"]
            edits.listings().patch(packageName=PAKET, editId=edit_id, language=sp, body=body).execute()
            print(f"Eintrag [{sp}] gesetzt")

        for sp, je in plan["bilder"].items():
            for typ, dateien in je.items():
                edits.images().deleteall(packageName=PAKET, editId=edit_id, language=sp, imageType=typ).execute()
                for f in dateien:
                    mime = "image/png" if f.suffix == ".png" else "image/jpeg"
                    edits.images().upload(packageName=PAKET, editId=edit_id, language=sp, imageType=typ,
                                          media_body=MediaFileUpload(str(f), mimetype=mime)).execute()
                print(f"Bilder [{sp}] {typ}: {len(dateien)}")

        edits.validate(packageName=PAKET, editId=edit_id).execute()
        print("Google hat die Bearbeitung geprüft: in Ordnung.")
        if not args.ja:
            print("Ohne --ja wird nichts übernommen; die Bearbeitung wird verworfen.")
            return
        edits.commit(packageName=PAKET, editId=edit_id,
                     changesNotSentForReview=args.nicht_zur_pruefung or None).execute()
        uebernommen = True
        print("Übernommen. Freigeben in der Play Console: Test und Release > Interner Test.")
    finally:
        if not uebernommen:
            try:
                edits.delete(packageName=PAKET, editId=edit_id).execute()
            except Exception:
                pass


def main() -> int:
    for strom in (sys.stdout, sys.stderr):
        try:
            strom.reconfigure(encoding="utf-8")
        except Exception:
            pass
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("--trocken", action="store_true", help="nur lokal prüfen")
    a.add_argument("--pruefen", action="store_true", help="Zugang testen, Tracks zeigen, nichts ändern")
    a.add_argument("--ja", action="store_true", help="Bearbeitung übernehmen (sonst nur prüfen und verwerfen)")
    a.add_argument("--eintrag", action="store_true", help="Titel und Beschreibungen mitschicken")
    a.add_argument("--bilder", action="store_true", help="Screenshots und Grafiken ersetzen")
    a.add_argument("--nur-telefon", action="store_true")
    a.add_argument("--nur-uhr", action="store_true")
    a.add_argument("--track", default="internal", help="Track fürs Telefon (Standard internal)")
    a.add_argument("--uhr-track", default="wear:internal", help="Track für die Uhr (Standard wear:internal)")
    a.add_argument("--nicht-zur-pruefung", action="store_true",
                   help="changesNotSentForReview setzen, falls Google das beim Übernehmen verlangt")
    a.add_argument("--schluessel", type=Path, default=SCHLUESSEL)
    args = a.parse_args()
    if args.nur_telefon and args.nur_uhr:
        a.error("--nur-telefon und --nur-uhr schließen sich aus")

    try:
        plan = plan_erstellen(args) if not args.pruefen else {"geraete": {}, "sprachen": [], "eintrag": {}, "bilder": {}}
        if not args.pruefen:
            plan_zeigen(plan)
        if args.trocken:
            print("Lokale Prüfung in Ordnung. Kein Netz benutzt.")
            return 0
        hochladen(dienst(args.schluessel), plan, args)
        return 0
    except Fehler as e:
        print(e, file=sys.stderr)
        return 1
    except Exception as e:  # HttpError der API
        print("Play-API: " + api_fehler(e), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
