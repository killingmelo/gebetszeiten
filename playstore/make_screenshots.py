#!/usr/bin/env python3
"""Macht Roh-Aufnahmen vom Emulator Play-tauglich und legt sie dorthin, wo
hochgeladen wird: fastlane/metadata/android/de-DE/images/.

Warum es das Skript ueberhaupt braucht: Play weist Telefon-Bilder ab, die
hoeher als 2:1 sind. Eine Aufnahme vom Testgeraet ist 1080x2400 und damit
2,22:1 — sie faellt durch, und zwar erst beim Hochladen. Hier wird auf 16:9
beschnitten (oben behalten: Kopfzeile und Inhalt).

Bis zum 20.09.2026 schrieb dieses Skript nach `playstore/screenshots/` und
las Dateinamen, die es seit Juni nicht mehr gab. Hochgeladen wurde aber aus
`fastlane/…/images/`. Zwei Orte, von denen einer gepflegt wurde — jetzt einer.

AUFNEHMEN (Emulator laeuft, App installiert):

    adb shell screencap -p /sdcard/s.png
    adb pull /sdcard/s.png build/roh/01_heute.png

In Git Bash `MSYS_NO_PATHCONV=1` davorsetzen, sonst macht die Shell aus
`/sdcard/...` einen Windows-Pfad. Fuer die Uhr `adb -s emulator-<port>`.

Dann:

    python playstore/make_screenshots.py
"""
import shutil
import sys
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parents[1]
ROH = REPO / "build" / "roh"
ZIEL = REPO / "fastlane" / "metadata" / "android" / "de-DE" / "images"

# Play-Grenzen: Telefon hoechstens 2:1, Uhr genau 1:1 und mindestens 384 px.
MAX_VERHAELTNIS = 2.0
ZIEL_VERHAELTNIS = 16 / 9


def telefon(quelle: Path, ziel: Path) -> None:
    im = Image.open(quelle)
    w, h = im.size
    hoehe = int(w * ZIEL_VERHAELTNIS)
    if h > hoehe:
        im = im.crop((0, 0, w, hoehe))
    assert im.size[1] / im.size[0] <= MAX_VERHAELTNIS, f"{ziel.name}: zu hoch fuer Play"
    im.convert("RGB").save(ziel)
    print(f"  {ziel.name:26} {im.size}")


def uhr(quelle: Path, ziel: Path) -> None:
    im = Image.open(quelle)
    assert im.size[0] == im.size[1], f"{ziel.name}: Uhr-Bilder muessen quadratisch sein"
    assert im.size[0] >= 384, f"{ziel.name}: mindestens 384 px, hat {im.size[0]}"
    im.convert("RGB").save(ziel)
    print(f"  {ziel.name:26} {im.size}")


def main() -> int:
    if not ROH.is_dir():
        print(f"{ROH} fehlt — erst aufnehmen (siehe Kopf dieser Datei).", file=sys.stderr)
        return 1
    for art, macher in (("phoneScreenshots", telefon), ("wearScreenshots", uhr)):
        quelle = ROH / art
        if not quelle.is_dir():
            print(f"{quelle} fehlt, uebersprungen")
            continue
        ziel = ZIEL / art
        # Alte Bilder weg: sonst laedt jemand eine Ansicht hoch, die es in
        # dieser Fassung nicht mehr gibt.
        if ziel.is_dir():
            shutil.rmtree(ziel)
        ziel.mkdir(parents=True)
        print(f"{art}:")
        for datei in sorted(quelle.glob("*.png")):
            macher(datei, ziel / datei.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
