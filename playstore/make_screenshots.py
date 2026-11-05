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

# (Rohdatei, Gebet klein darüber, Aussage groß, Zeile). Reihenfolge = Tageslauf =
# Panorama von links. Seit 05.11.2026 (Rückmeldung des Testers) trägt die Aussage
# zur Funktion die Überschrift, der Gebetsname steht klein darüber.
SHOTS = [
    ("01_fajr.png", "Faǧr", "Der Tag beginnt\nmit dem ersten Licht.", "Welches Gebet gilt und wie lange noch"),
    ("02_zuhr.png", "Ẓuhr", "Jede Zeit erklärt.", "Beginn, Ende, Rakʿa und Quellen"),
    ("03_asr.png", "ʿAṣr", "Immer im Blick.", "Widgets, Statusleiste und Uhr"),
    ("04_maghrib.png", "Maġrib", "Der ganze Monat.", "Amtliche Diyanet-Zeiten, gegengeprüft"),
    ("05_isha.png", "ʿIšāʾ", "Ruhig, auch\nin der Nacht.", "Reines Schwarz. Keine Werbung, kein Konto."),
]
# Kein Uhr-Einsatz mehr: die vorhandene Aufnahme stammt vom 30.10. und zeigte
# andere Zeiten als das Telefon daneben. Wieder einsetzen mit neuer Aufnahme.
UHR_EINSATZ = None
NACHT = 4  # Index des letzten, dunklen Bildes

# Zeile in Tinte statt Stein (#4A4741): vor Ocker und Ton reichte Stein nicht
# fuer 4,5:1 (Bild 4, gemessen 2,4:1 am Zeilenende).
HELL = {"titel": INK, "zeile": INK, "kopf": INK, "rahmen": (0x1C, 0x1B, 0x1A)}
DUNKEL = {"titel": (0xF7, 0xF1, 0xE8), "zeile": (0xD8, 0xD2, 0xC8), "kopf": (0xD8, 0xD2, 0xC8), "rahmen": (0x2A, 0x2A, 0x2D)}

# Telefon schmal und tief genug, dass die Sonnenbahn im Streifen darueber
# sichtbar bleibt; die oberen gut drei Viertel des Bildschirms bleiben zu sehen.
TELEFON_BREITE = 700
RAHMEN = 12
TELEFON_OBEN = 700
ECKE = 40
# Kopf (Gebet), Aussage (bis zwei Zeilen) und Zeile über dem Horizont; die
# flache Sonnenbahn läuft darunter bis zum Telefon (y = 700).
KOPF_Y, TITEL_Y, ZEILE_Y = 110, 200, 380
TITEL_PX, TITEL_ABSTAND, ZEILE_PX, KOPF_PX = 84, 92, 38, 30
HORIZONT = 640 / HOEHE
# Flacher als bis 05.11.2026 (0,357): die Textzone reicht jetzt tiefer, die Sonne
# im Scheitel soll darunter bleiben statt hinter der Zeile zu stehen.
BAHN = 0.18


STRICHE = (chr(0x2014), chr(0x2013))
SCHRIFTDATEI = {"serif": "serif_regular.ttf", "sans": "sans_regular.ttf", "kopf": "sans_semibold.ttf"}


def zeilen(index: int) -> list:
    """(Text, Schrift, Grundlinie, Farbschlüssel) aller Textzeilen eines Bildes."""
    _, kopf, titel, zeile = SHOTS[index]
    teile = titel.split("\n")
    # Einzeilige Aussage etwas tiefer, damit der Abstand zur Zeile gleich wirkt.
    oben = TITEL_Y + (TITEL_ABSTAND // 2 if len(teile) == 1 else 0)
    out = [(kopf.upper(), font("kopf", KOPF_PX), KOPF_Y, "kopf")]
    out += [(t, font("serif", TITEL_PX), oben + i * TITEL_ABSTAND, "titel") for i, t in enumerate(teile)]
    out.append((zeile, font("sans", ZEILE_PX), ZEILE_Y, "zeile"))
    return out


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
    for text, schrift, y, rolle in zeilen(index):
        farbe = farben[rolle]
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
    for _, kopf, titel, zeile in SHOTS:
        for text in (kopf, titel, zeile):
            assert not any(s in text for s in STRICHE), f"Strich in „{text}“"
        assert not fehlende_zeichen(titel.replace("\n", " "), "serif"), f"Serife: {fehlende_zeichen(titel, 'serif')}"
        assert not fehlende_zeichen(zeile, "sans"), f"Grotesk: {fehlende_zeichen(zeile, 'sans')}"
        assert not fehlende_zeichen(kopf.upper(), "kopf"), f"Kopf: {fehlende_zeichen(kopf.upper(), 'kopf')}"



def _rund(im: Image.Image, radius: int) -> Image.Image:
    maske = Image.new("L", im.size, 0)
    ImageDraw.Draw(maske).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=radius, fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), maske)
    return out


def compose(raw: Image.Image, panel: Image.Image, index: int,
            inset: Image.Image | None = None, dunkel: bool = False) -> Image.Image:
    """Ein Screenshot 1080 x 1920: Himmel, Gebet, Aussage, Zeile, Aufnahme im Rahmen."""
    farben = DUNKEL if dunkel else HELL
    im = panel.convert("RGBA")
    d = ImageDraw.Draw(im)
    for text, schrift, y, rolle in zeilen(index):
        # Der Gebetsname leicht gesperrt, wie die kleinen Köpfe in der App.
        if rolle == "kopf":
            breite = sum(d.textlength(c, font=schrift) for c in text) + 4 * (len(text) - 1)
            x = BREITE / 2 - breite / 2
            for c in text:
                d.text((x, y), c, font=schrift, fill=farben[rolle], anchor="ls")
                x += d.textlength(c, font=schrift) + 4
        else:
            d.text((BREITE / 2, y), text, font=schrift, fill=farben[rolle], anchor="ms")

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
    fehlend = [n for n, _, _, _ in SHOTS if not (quelle / n).is_file()]
    assert not fehlend, f"Roh-Aufnahmen fehlen: {fehlend}"
    pano = render_panorama(BREITE * len(SHOTS), HOEHE, horizon=HORIZONT, bahn=BAHN)
    bilder = []
    for i, (name, _, _, _) in enumerate(SHOTS):
        panel = pano.crop((i * BREITE, 0, (i + 1) * BREITE, HOEHE))
        pruefe_kontrast(i, panel)
        einsatz = Image.open(quelle / UHR_EINSATZ) if UHR_EINSATZ and name == "03_asr.png" else None
        bild = compose(Image.open(quelle / name), panel, i, einsatz, dunkel=(i == NACHT))
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
