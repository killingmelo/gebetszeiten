#!/usr/bin/env python3
"""Setzt die Store-Screenshots aus Roh-Aufnahmen vom Emulator zusammen und legt
sie dorthin, wo hochgeladen wird: fastlane/metadata/android/de-DE/images/.

TELEFON: „Ein Tag in fünf Bildern“ (Spec 2026-10-30-playstore-auftritt). Ein
Himmel aus `panorama.py` läuft über alle fünf Bilder; jedes trägt den Namen
eines Gebets und zeigt eine echte Aufnahme der App. Ergebnis 1080 x 1920 (9:16;
Play nimmt höchstens 2:1).

UHR: Aufnahmen werden nur geprüft (quadratisch, mindestens 384 px) und kopiert.

AUFNEHMEN (Emulator läuft, App installiert, Ort Nürnberg):

- Zeitzone Europe/Berlin, automatische Zeit aus, Uhrzeit über Einstellungen >
  Datum und Uhrzeit setzen (adb root geht auf Play-Images nicht; die App wird
  dafür nicht geändert). Der Emulator steht sonst auf UTC, und „Heute“ zeigt
  das falsche laufende Gebet.
- Statusleiste im Demo-Modus (`am broadcast -a com.android.systemui.demo`),
  Uhrzeit dort gleich der eingestellten. Systemsprache Deutsch (sonst steht
  „Fri, Oct 30“ auf dem Startbildschirm), Nachtansicht für ʿIšāʾ mit
  `cmd uimode night yes`.
- `adb shell screencap -p /sdcard/s.png`, dann `adb pull` nach
  build/roh/phoneScreenshots/<Dateiname aus SHOTS>; in Git Bash
  `MSYS_NO_PATHCONV=1` davorsetzen. Uhr-Kachel nach `03_asr_uhr.png`,
  Uhr-Bilder nach build/roh/wearScreenshots/.
- Angezeigte Zeiten gegen die eingebaute Tabelle für Nürnberg prüfen.

Dann:

    python playstore/make_screenshots.py
"""
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from panorama import INK, font, render_panorama  # noqa: E402

REPO = HERE.parent
ROH = REPO / "build" / "roh"
ZIEL = REPO / "fastlane" / "metadata" / "android" / "de-DE" / "images"

# Play-Grenzen: Telefon höchstens 2:1, Uhr genau 1:1 und mindestens 384 px.
MAX_VERHAELTNIS = 2.0
BREITE, HOEHE = 1080, 1920

# (Rohdatei, Überschrift, Zeile). Reihenfolge = Tageslauf = Panorama von links.
SHOTS = [
    ("01_fajr.png", "Faǧr", "Welches Gebet gerade gilt und wie lange noch"),
    ("02_zuhr.png", "Ẓuhr", "Warum die Zeit dann beginnt"),
    ("03_asr.png", "ʿAṣr", "Auf dem Startbildschirm und am Handgelenk"),
    ("04_maghrib.png", "Maġrib", "Amtliche Diyanet-Zeiten, gegengeprüft"),
    ("05_isha.png", "ʿIšāʾ", "Keine Werbung, kein Konto, akkusparend"),
]
UHR_EINSATZ = "03_asr_uhr.png"
NACHT = 4  # Index des letzten, dunklen Bildes

# Zeile in Tinte statt Stein (#4A4741): vor Ocker und Ton reichte Stein nicht
# fuer 4,5:1 (Bild 4, gemessen 2,4:1 am Zeilenende).
HELL = {"titel": INK, "zeile": INK, "rahmen": (0x1C, 0x1B, 0x1A)}
DUNKEL = {"titel": (0xF7, 0xF1, 0xE8), "zeile": (0xD8, 0xD2, 0xC8), "rahmen": (0x2A, 0x2A, 0x2D)}

# Telefon schmal und tief genug, dass die Sonnenbahn im Streifen darueber
# sichtbar bleibt; die oberen gut drei Viertel des Bildschirms bleiben zu sehen.
TELEFON_BREITE = 700
RAHMEN = 12
TELEFON_OBEN = 700
ECKE = 40
# Horizont und flache Sonnenbahn zwischen Zeile (y = 250) und Telefon (y = 700).
TITEL_Y, ZEILE_Y = 170, 250
HORIZONT = 640 / HOEHE
BAHN = 0.357


STRICHE = (chr(0x2014), chr(0x2013))
SCHRIFTDATEI = {"serif": "serif_light.ttf", "sans": "sans_regular.ttf"}


def fehlende_zeichen(text: str, role: str) -> set:
    """Zeichen aus `text`, die die gebuendelte Schrift nicht enthaelt (cmap)."""
    from fontTools.ttLib import TTFont

    cmap = TTFont(REPO / "app" / "src" / "main" / "res" / "font" / SCHRIFTDATEI[role]).getBestCmap()
    return {ch for ch in text if not ch.isspace() and ord(ch) not in cmap}


def _luminanz(rgb: tuple) -> float:
    def kanal(c: int) -> float:
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (kanal(c) for c in rgb[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def kontrast(a: tuple, b: tuple) -> float:
    la, lb = sorted((_luminanz(a), _luminanz(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


MIN_KONTRAST = 4.5  # wie PaletteContrastTest fuer Text und Nebentext


def pruefe_kontrast(index: int, panel: Image.Image) -> None:
    """Ueberschrift und Zeile gegen den Himmel dahinter, ueber ihre ganze Breite."""
    farben = DUNKEL if index == NACHT else HELL
    d = ImageDraw.Draw(panel)
    _, titel, zeile = SHOTS[index]
    for text, schrift, y, farbe in ((titel, font("serif", 120), TITEL_Y, farben["titel"]),
                                    (zeile, font("sans", 40), ZEILE_Y, farben["zeile"])):
        x0, y0, x1, y1 = d.textbbox((BREITE / 2, y), text, font=schrift, anchor="ms")
        for x in range(int(x0), int(x1), 6):
            for yy in (int(y0) + 2, int((y0 + y1) / 2), int(y1) - 2):
                k = kontrast(farbe, panel.getpixel((x, yy)))
                assert k >= MIN_KONTRAST, (
                    f"Bild {index + 1}, „{text}“: Kontrast {k:.2f} bei x={x}, y={yy}"
                )


def pruefe_texte() -> None:
    # Der Pruefer muss fehlschlagen koennen: ein arabischer Buchstabe steht in
    # keiner der beiden Schriften. Ein Bild-Vergleich (getmask) taugt dafuer
    # nicht, weil Pillow fuer fehlende Zeichen ein Ersatzkaestchen malt.
    assert fehlende_zeichen(chr(0x0635), "serif") == {chr(0x0635)}, "Glyphenpruefung erkennt nichts"
    for _, titel, zeile in SHOTS:
        for text in (titel, zeile):
            assert not any(s in text for s in STRICHE), f"Strich in „{text}“"
        assert not fehlende_zeichen(titel, "serif"), f"Serife: {fehlende_zeichen(titel, 'serif')}"
        assert not fehlende_zeichen(zeile, "sans"), f"Grotesk: {fehlende_zeichen(zeile, 'sans')}"



def _rund(im: Image.Image, radius: int) -> Image.Image:
    maske = Image.new("L", im.size, 0)
    ImageDraw.Draw(maske).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=radius, fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), maske)
    return out


def compose(raw: Image.Image, panel: Image.Image, title: str, line: str,
            inset: Image.Image | None = None, dunkel: bool = False) -> Image.Image:
    """Ein Screenshot 1080 x 1920: Himmel, Überschrift, Zeile, Aufnahme im Rahmen."""
    farben = DUNKEL if dunkel else HELL
    im = panel.convert("RGBA")
    d = ImageDraw.Draw(im)
    serif, sans = font("serif", 120), font("sans", 40)
    d.text((BREITE / 2, TITEL_Y), title, font=serif, fill=farben["titel"], anchor="ms")
    d.text((BREITE / 2, ZEILE_Y), line, font=sans, fill=farben["zeile"], anchor="ms")

    h = round(raw.size[1] * TELEFON_BREITE / raw.size[0])
    bild = raw.convert("RGB").resize((TELEFON_BREITE, h), Image.LANCZOS)
    aussen = Image.new("RGB", (TELEFON_BREITE + 2 * RAHMEN, h + 2 * RAHMEN), farben["rahmen"])
    aussen.paste(_rund(bild, ECKE), (RAHMEN, RAHMEN), _rund(bild, ECKE))
    aussen = _rund(aussen, ECKE + RAHMEN)
    im.alpha_composite(aussen, ((BREITE - aussen.size[0]) // 2, TELEFON_OBEN))

    if inset is not None:
        g = 330
        rund = inset.convert("RGB").resize((g, g), Image.LANCZOS)
        maske = Image.new("L", (g, g), 0)
        ImageDraw.Draw(maske).ellipse([0, 0, g - 1, g - 1], fill=255)
        ring = Image.new("RGBA", (g + 2 * RAHMEN, g + 2 * RAHMEN), (0, 0, 0, 0))
        ImageDraw.Draw(ring).ellipse([0, 0, g + 2 * RAHMEN - 1, g + 2 * RAHMEN - 1], fill=farben["rahmen"])
        ring.paste(rund, (RAHMEN, RAHMEN), maske)
        # Rechts auf dem leeren Himmel des Startbildschirms, ueber den Widgets:
        # weiter unten verdeckte die Uhr deren Zeilen (seit 0.4.0 stehen dort
        # zwei Widgets, das kleine sitzt hoeher als frueher das grosse).
        im.alpha_composite(ring, (BREITE - ring.size[0] - 60, 720))
    return im.convert("RGB")


def telefon() -> list:
    quelle = ROH / "phoneScreenshots"
    fehlend = [n for n, _, _ in SHOTS if not (quelle / n).is_file()]
    assert not fehlend, f"Roh-Aufnahmen fehlen: {fehlend}"
    pano = render_panorama(BREITE * len(SHOTS), HOEHE, horizon=HORIZONT, bahn=BAHN)
    bilder = []
    for i, (name, titel, zeile) in enumerate(SHOTS):
        panel = pano.crop((i * BREITE, 0, (i + 1) * BREITE, HOEHE))
        pruefe_kontrast(i, panel)
        einsatz = Image.open(quelle / UHR_EINSATZ) if name == "03_asr.png" else None
        bild = compose(Image.open(quelle / name), panel, titel, zeile, einsatz, dunkel=(i == NACHT))
        assert bild.size == (BREITE, HOEHE)
        assert bild.size[1] / bild.size[0] <= MAX_VERHAELTNIS, f"{name}: zu hoch für Play"
        bilder.append((name, bild))
    # Naht: rechte Randspalte von Bild n und linke von Bild n+1 liegen im
    # Panorama nebeneinander und dürfen sich kaum unterscheiden.
    for (na, a), (nb, b) in zip(bilder, bilder[1:]):
        for y in range(0, HOEHE, 40):
            pa, pb = a.getpixel((BREITE - 1, y)), b.getpixel((0, y))
            assert all(abs(p - q) <= 3 for p, q in zip(pa, pb)), f"Naht {na}|{nb} bei y={y}: {pa} {pb}"
    return bilder


def uhr(quelle: Path, ziel: Path) -> None:
    im = Image.open(quelle)
    assert im.size[0] == im.size[1], f"{ziel.name}: Uhr-Bilder müssen quadratisch sein"
    assert im.size[0] >= 384, f"{ziel.name}: mindestens 384 px, hat {im.size[0]}"
    im.convert("RGB").save(ziel)
    print(f"  {ziel.name:26} {im.size}")


def neu(ordner: Path) -> None:
    # Alte Bilder weg: sonst lädt jemand eine Ansicht hoch, die es nicht mehr gibt.
    if ordner.is_dir():
        shutil.rmtree(ordner)
    ordner.mkdir(parents=True)


def main() -> int:
    pruefe_texte()
    bilder = telefon()
    ziel = ZIEL / "phoneScreenshots"
    neu(ziel)
    print("phoneScreenshots:")
    for name, bild in bilder:
        bild.save(ziel / name)
        print(f"  {name:26} {bild.size}")
    quelle = ROH / "wearScreenshots"
    if quelle.is_dir():
        ziel = ZIEL / "wearScreenshots"
        neu(ziel)
        print("wearScreenshots:")
        for datei in sorted(quelle.glob("*.png")):
            uhr(datei, ziel / datei.name)
    else:
        print(f"{quelle} fehlt, Uhr-Bilder bleiben wie sie sind")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
