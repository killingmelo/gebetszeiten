#!/usr/bin/env python3
"""Store-Icon (512 x 512) und Feature-Bild (1024 x 500).

Das Icon kommt aus derselben Geometrie wie das Startbildschirm-Symbol
(`tools/app-icon/geometry.json`), der Himmel des Feature-Bilds aus demselben
Panorama wie die Screenshots (`playstore/panorama.py`). Bis zum 30.10.2026
zeichnete dieses Skript eine Sichel auf Tuerkisgruen; ein Store-Symbol, das
anders aussieht als das auf dem Geraet, faellt niemandem auf, der beide nicht
nebeneinander haelt. Deshalb eine Quelle.

    python playstore/make_assets.py
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(REPO / "tools" / "app-icon"))
sys.path.insert(0, str(HERE))
from build_app_icon import render_png  # noqa: E402
from panorama import INK, font, render_panorama  # noqa: E402

ICON = HERE / "icon_512.png"
FEATURE = HERE / "feature_1024x500.png"


def kachel(size: int) -> Image.Image:
    """Das Icon als abgerundete Kachel mit weichem Schatten, RGBA."""
    icon = render_png(size)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.22, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(icon, (0, 0), mask)
    return out


def feature() -> Image.Image:
    w, h = 1024, 500
    im = render_panorama(w, h, bahn_ab=0.30).convert("RGBA")
    d = ImageDraw.Draw(im)
    serif = font("serif", 70)
    d.text((64, 196), "Gebetszeiten", font=serif, fill=INK + (255,))
    d.text((64, 276), "Islam", font=serif, fill=INK + (255,))
    k = kachel(250)
    im = Image.alpha_composite(im, schatten(w, h))
    # rechts von der Maġrib-Sonne, die auf dem Horizont bei x = 717 steht
    im.alpha_composite(k, (740, 125))
    return im.convert("RGB")


def schatten(w: int, h: int) -> Image.Image:
    """Schatten unter der Icon-Kachel, RGBA in voller Bildgroesse."""
    lage = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(lage).rounded_rectangle([748, 140, 998, 390], radius=55, fill=(0, 0, 0, 70))
    return lage.filter(ImageFilter.GaussianBlur(14))


def main() -> int:
    icon = render_png(512).convert("RGB")
    assert icon.size == (512, 512)
    icon.save(ICON)
    # DESIGN.md: Schatten weich und leicht, nie hart. Ein harter Rand hat zwei
    # Alphastufen (0 und 90); ein weicher laeuft ueber viele aus.
    stufen = sum(1 for n in schatten(1024, 500).getchannel("A").histogram() if n)
    assert stufen > 20, f"Schatten hat nur {stufen} Alphastufen, also eine harte Kante"
    fg = feature()
    assert fg.size == (1024, 500) and fg.mode == "RGB"
    fg.save(FEATURE)
    print(f"{ICON.name} {icon.size}, {FEATURE.name} {fg.size}")
    print("ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
