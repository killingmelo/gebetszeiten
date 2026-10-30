#!/usr/bin/env python3
"""Der Himmel fuer Feature-Bild und Screenshots: ein Tag von links nach rechts.

Grau der Daemmerung, Creme, Gold, Ocker, Ton, dann Nacht. Darueber die
Sonnenbahn: unter dem Horizont bei Faǧr, im Scheitel bei Ẓuhr, im Abstieg bei
ʿAṣr, auf dem Horizont bei Maġrib, Sterne bei ʿIšāʾ. Die fuenf Screenshots
schneiden dieses eine Bild in fuenf Streifen; das Feature-Bild zeigt es klein.
Alle Lagen sind relativ zu Breite und Hoehe, damit beide dasselbe zeigen.
Vorbild: Mockup `screenshots-tag.html` der Abstimmung vom 30.10.2026.

    python playstore/panorama.py     # Selbstpruefung: Groesse, Farben, keine Naht
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[1]
FONTS = REPO / "app" / "src" / "main" / "res" / "font"

# Zwischen Bild 4 und 5 (Grenze bei 0,80) liegen die Zeilen nur 0,03 von der
# Kante entfernt: links muss Tinte, rechts Creme 4,5:1 erreichen. Darum faellt
# der Ton erst ab 0,77 und ist bei 0,83 schon tief (PaletteContrast-Regel).
STOPS = [
    (0.00, "#C8CBC6"), (0.14, "#ECE6DA"), (0.30, "#F7F1E8"), (0.48, "#F4E6C9"),
    (0.62, "#E2BF86"), (0.77, "#B9775A"), (0.83, "#5A3A33"), (0.90, "#2A1F22"),
    (1.00, "#000000"),
]
INK = (0x1C, 0x1B, 0x1A)
CREAM = (0xF7, 0xF1, 0xE8)
GOLD = (0xDD, 0xAA, 0x61)
OCHRE = (0xBC, 0x92, 0x4C)

# Sonnenbahn als kubische Bézierkurve in Anteilen von Breite und Hoehe
# (aus dem Mockup, 1000 x 356). Endet auf dem Horizont bei Maġrib.
BAHN = [(0.060, 0.843), (0.150, 0.112), (0.450, 0.112), (0.700, 0.736)]
SONNEN = [  # (t auf der Bahn, Farbe, Radius in Anteilen der Hoehe)
    (0.50, GOLD, 0.025),   # Ẓuhr
    (0.75, GOLD, 0.025),   # ʿAṣr
    (1.00, OCHRE, 0.025),  # Maġrib
]
# Sterne unterhalb der Ueberschrift-Zone (die Zeile im fuenften Screenshot
# endet bei etwa 0,14 der Hoehe), sonst sitzt einer mitten im Text.
STERNE = [(0.85, 0.20, 1.6), (0.93, 0.26, 1.2), (0.965, 0.18, 1.4), (0.89, 0.31, 1.0), (0.985, 0.29, 1.1)]


def _hex(h: str) -> tuple:
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def _verlauf(width: int) -> list:
    stops = [(p, _hex(c)) for p, c in STOPS]
    out = []
    for x in range(width):
        t = x / max(width - 1, 1)
        for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
            if t <= p1:
                u = (t - p0) / (p1 - p0)
                out.append(tuple(round(a + (b - a) * u) for a, b in zip(c0, c1)))
                break
    return out


def _bahn(t: float) -> tuple:
    m = 1 - t
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = BAHN
    return (m ** 3 * x0 + 3 * m * m * t * x1 + 3 * m * t * t * x2 + t ** 3 * x3,
            m ** 3 * y0 + 3 * m * m * t * y1 + 3 * m * t * t * y2 + t ** 3 * y3)


def render_panorama(width: int, height: int, horizon: float = 0.736, sun: bool = True,
                    bahn: float = 1.0, bahn_ab: float = 0.0) -> Image.Image:
    """Der ganze Tag als ein Bild, RGB.

    `horizon` legt die Horizontlinie fest (Anteil der Hoehe), `bahn` staucht
    die Sonnenbahn senkrecht zum Horizont hin: die Screenshots brauchen sie
    flach im Streifen zwischen Ueberschrift und Telefon, das Feature-Bild hoch.
    `bahn_ab` laesst die gestrichelte Bahn erst ab diesem Anteil der Breite
    beginnen (Feature-Bild: links steht die Schrift).
    """
    zeile = Image.new("RGB", (width, 1))
    zeile.putdata(_verlauf(width))
    im = zeile.resize((width, height), Image.NEAREST).convert("RGBA")
    if not sun:
        return im.convert("RGB")
    lay = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    strich = max(1, round(height * 0.0034))
    d.rectangle([0, horizon * height - strich / 2, width, horizon * height + strich / 2], fill=INK + (46,))
    # gestrichelte Bahn: Strich und Luecke in festen Laengen entlang der Kurve
    def lage(x: float, y: float) -> tuple:
        return x * width, (horizon + (y - BAHN[-1][1]) * bahn) * height

    punkte = [lage(*_bahn(k / 600)) for k in range(601)]
    strich_l, luecke = height * 0.0084, height * 0.014
    lauf, an = 0.0, True
    for (xa, ya), (xb, yb) in zip(punkte, punkte[1:]):
        lauf += math.hypot(xb - xa, yb - ya)
        if an and xa >= bahn_ab * width:
            d.line([(xa, ya), (xb, yb)], fill=INK + (56,), width=strich)
        if lauf >= (strich_l if an else luecke):
            lauf, an = 0.0, not an
    # Faǧr: ein Schimmer unter dem Horizont, noch keine Sonne
    fx, fy, fr = 0.07 * width, horizon * height, 0.073 * height
    # nur die untere Haelfte: darueber saehe es aus wie die aufgehende Sonne
    d.chord([fx - fr, fy - fr, fx + fr, fy + fr], 0, 180, fill=CREAM + (90,))
    for t, farbe, r in SONNEN:
        x, y = lage(*_bahn(t))
        r = r * height
        d.ellipse([x - r, y - r, x + r, y + r], fill=farbe + (255,))
    for sx, sy, sr in STERNE:
        r = sr * height / 356
        d.ellipse([sx * width - r, sy * height - r, sx * width + r, sy * height + r], fill=CREAM + (235,))
    return Image.alpha_composite(im, lay).convert("RGB")


def font(role: str, px: int) -> ImageFont.FreeTypeFont:
    """Die gebuendelten Schriften der App: Serife fuer Ueberschriften, Grotesk fuer Zeilen."""
    datei = {"serif": "serif_light.ttf", "sans": "sans_regular.ttf"}[role]
    return ImageFont.truetype(str(FONTS / datei), px)


def _selbstpruefung() -> None:
    im = render_panorama(5400, 1920)
    assert im.size == (5400, 1920), im.size
    links = im.getpixel((2, 40))
    rechts = im.getpixel((5397, 40))
    assert all(abs(a - b) <= 6 for a, b in zip(links, (0xC8, 0xCB, 0xC6))), f"links {links}"
    assert all(v <= 6 for v in rechts), f"rechts {rechts}"
    for k in range(1, 5):
        x = 1080 * k
        for y in (40, 960, 1800):
            a, b = im.getpixel((x - 1, y)), im.getpixel((x, y))
            assert all(abs(p - q) <= 3 for p, q in zip(a, b)), f"Naht bei x={x}, y={y}: {a} {b}"
    # Faǧr: das Licht liegt noch UNTER dem Horizont. Ein Schimmer darueber
    # saehe aus wie die aufgehende Sonne (Šurūq).
    w, h, hz = 1024, 500, 0.736
    leer = render_panorama(w, h, horizon=hz, sun=False)
    mit = render_panorama(w, h, horizon=hz)
    for dy in (4, 12, 24):
        p = (int(0.07 * w), int(hz * h) - dy)
        assert mit.getpixel(p) == leer.getpixel(p), f"Faǧr-Schimmer ueber dem Horizont bei {p}"
    # Feature-Bild: links steht die Schrift (y = 196 bis 350), dort keine
    # Sonnenbahn. Die Ẓuhr-Sonne darueber (bis y = 152) darf bleiben.
    frei = render_panorama(w, h, horizon=hz, bahn_ab=0.30)
    for x in range(0, int(0.45 * w), 3):
        for y in range(int(0.38 * h), int(0.72 * h), 3):
            assert frei.getpixel((x, y)) == leer.getpixel((x, y)), f"Sonnenbahn in der Schrift bei {(x, y)}"
    print("ok")


if __name__ == "__main__":
    _selbstpruefung()
    sys.exit(0)
