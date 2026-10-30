#!/usr/bin/env python3
"""Das App-Symbol aus einer Quelle: geometry.json.

Figur, Fuesse und Teppich sind Polygone, nachgezogen aus dem Entwurf
„Mitte 2“ (siehe trace_vorlage.py). Der Bogen ist kein nachgezogener
Pinselstrich, sondern wird hier geometrisch gebaut: ein andalusischer
Hufeisenbogen, oben rund und unten eingezogen wie der Miḥrāb von Córdoba.

Warum Polygone und keine Bézierkurven: dieselben Punkte ergeben ohne
Umrechnung die VectorDrawable-Pfade fuers Geraet und, mit Pillow gefuellt,
das 512er-Symbol fuer den Store. Eine Form, zwei Ausgaben, kein Abgleich.

    python build_app_icon.py             # schreibt die Drawables + icon.sha256
    python build_app_icon.py --check     # nur pruefen, nichts schreiben
    python build_app_icon.py --preview   # zusaetzlich preview.png: 512/192/48/32 px und einfarbig

Wiederholbar: zweimal laufen lassen ergibt byte-gleiche Dateien.
`AppIconAssetsTest` vergleicht die Drawables mit icon.sha256.
"""
import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
DRAWABLE = REPO / "app" / "src" / "main" / "res" / "drawable"
GEOMETRY = HERE / "geometry.json"
MANIFEST = HERE / "icon.sha256"
PREVIEW = HERE / "preview.png"
FOREGROUND_FILE = "ic_launcher_foreground.xml"
BACKGROUND_FILE = "ic_launcher_background.xml"
HEADER = "    <!-- Erzeugt von tools/app-icon/build_app_icon.py; nicht von Hand aendern. -->\n"

VIEWPORT = 108.0
# Schutzzone des adaptiven Symbols: Kreis mit 66 dp Durchmesser um die Mitte.
SAFE_RADIUS = 33.0
# Sichtbarer Ausschnitt auf dem Geraet: 72 von 108 dp, mittig.
VISIBLE = 72.0
MAX_POINTS = 600

CREAM = "#F7F1E8"
FOREST = "#224228"

Shape = tuple  # (name: str, points: list[tuple[float, float]])


# Strichbreite entlang des Bogens, relativ zu `stroke`, von links unten ueber
# den Scheitel nach rechts unten: duenn angesetzt, links oben voll wie ein
# Pinsel mit frischer Tinte, rechts auslaufend. Ein gleichmaessiges Band sah
# neben der Pinselfigur aus wie ein Schluesselloch.
BRUSH_PROFILE = [(0.00, 0.35), (0.12, 0.85), (0.38, 1.30), (0.55, 1.15), (0.80, 0.80), (0.94, 0.55), (1.00, 0.25)]


def _brush_width(t: float) -> float:
    for (t0, w0), (t1, w1) in zip(BRUSH_PROFILE, BRUSH_PROFILE[1:]):
        if t <= t1:
            u = (t - t0) / (t1 - t0)
            u = u * u * (3 - 2 * u)
            return w0 + (w1 - w0) * u
    return BRUSH_PROFILE[-1][1]


def horseshoe_arch(cx: float, top: float, width: float, height: float, pinch: float, stroke: float) -> list:
    """Hufeisenbogen als ein Pinselzug: geschlossenes Polygon um eine Mittellinie.

    `width` ist die groesste Aussenbreite, `top` die Oberkante, `height` die
    Hoehe bis zum Fuss der Pfosten. `pinch` ist der Anteil des Radius, um den
    die Pfosten gegenueber der breitesten Stelle nach innen stehen; daraus
    folgt, wie weit der Kreisbogen unter die Mitte hinabreicht (Córdoba).
    `stroke` ist die mittlere Strichbreite; sie schwankt nach BRUSH_PROFILE.
    """
    assert 0 < pinch < 0.5 and 0 < stroke < width / 4
    rc = width / 2 - stroke / 2
    cy = top + width / 2
    bottom = top + height
    theta = math.acos(1 - pinch)
    jamb = rc * (1 - pinch)
    assert cy + rc * math.sin(theta) < bottom, "Pfosten zu kurz fuer den Bogen"

    line = []
    for k in range(13):
        line.append((cx - jamb, bottom - (bottom - cy - rc * math.sin(theta)) * k / 12))
    for k in range(1, 73):
        a = math.pi + theta - (math.pi + 2 * theta) * k / 72
        line.append((cx + rc * math.cos(a), cy - rc * math.sin(a)))
    for k in range(1, 13):
        line.append((cx + jamb, cy + rc * math.sin(theta) + (bottom - cy - rc * math.sin(theta)) * k / 12))

    lengths = [0.0]
    for (x0, y0), (x1, y1) in zip(line, line[1:]):
        lengths.append(lengths[-1] + math.hypot(x1 - x0, y1 - y0))
    left, right = [], []
    for i, (x, y) in enumerate(line):
        xa, ya = line[max(i - 1, 0)]
        xb, yb = line[min(i + 1, len(line) - 1)]
        dx, dy = xb - xa, yb - ya
        n = math.hypot(dx, dy)
        nx, ny = -dy / n, dx / n
        w = stroke * _brush_width(lengths[i] / lengths[-1]) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    return left + right[::-1]


def _area(pts: list) -> float:
    return abs(sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]))) / 2


def check(shapes: list) -> None:
    """Bricht hart ab, wenn eine Form aus der Schutzzone ragt oder entartet ist."""
    c = VIEWPORT / 2
    total = 0
    for name, pts in shapes:
        assert len(pts) >= 3, f"{name}: weniger als 3 Punkte"
        assert _area(pts) > 0.5, f"{name}: keine Flaeche"
        for x, y in pts:
            d = math.hypot(x - c, y - c)
            assert d <= SAFE_RADIUS + 1e-6, f"{name}: Punkt ({x:.1f}, {y:.1f}) {d:.1f} dp von der Mitte, Schutzzone {SAFE_RADIUS}"
        total += len(pts)
    assert total <= MAX_POINTS, f"{total} Punkte, hoechstens {MAX_POINTS}"


def load_geometry(path: Path = GEOMETRY) -> list:
    """Figur-Polygone aus geometry.json plus der daraus gebaute Bogen, geprueft."""
    data = json.loads(path.read_text(encoding="utf-8"))
    assert data["viewport"] == VIEWPORT, "geometry.json hat einen anderen Viewport"
    shapes = [("bogen", horseshoe_arch(**data["arch"]))]
    shapes += [(s["name"], [tuple(p) for p in s["points"]]) for s in data["shapes"]]
    check(shapes)
    return shapes


def render_png(size: int, background: str | None = CREAM, fill: str = FOREST, shapes: list | None = None) -> Image.Image:
    """Der sichtbare 72-dp-Ausschnitt, so wie das Geraet ihn zeigt, in `size` px.

    `background=None` ergibt transparenten Grund (fuer die einfarbige Vorschau).
    """
    shapes = shapes if shapes is not None else load_geometry()
    ss = 4
    big = size * ss
    k = big / VISIBLE
    off = (VIEWPORT - VISIBLE) / 2
    im = Image.new("RGBA", (big, big), background or (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _, pts in shapes:
        d.polygon([((x - off) * k, (y - off) * k) for x, y in pts], fill=fill)
    return im.resize((size, size), Image.LANCZOS)


def _rounded(im: Image.Image, radius: float) -> Image.Image:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=radius, fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


def preview(path: Path = PREVIEW) -> None:
    shapes = load_geometry()
    sizes = [512, 192, 48, 32]
    pad = 24
    width = sum(sizes) * 2 + pad * (2 * len(sizes) + 3) + 96
    sheet = Image.new("RGB", (width, 512 + 2 * pad), "#2A2A2D")
    x = pad
    for s in sizes:
        tile = render_png(s, shapes=shapes)
        sheet.paste(_rounded(tile, s * 0.22), (x, pad + 512 - s), _rounded(tile, s * 0.22))
        x += s + pad
    for s in sizes:
        tile = render_png(s, shapes=shapes)
        circle = Image.new("L", (s, s), 0)
        ImageDraw.Draw(circle).ellipse([0, 0, s - 1, s - 1], fill=255)
        sheet.paste(tile, (x, pad + 512 - s), circle)
        x += s + pad
    mono = render_png(48 * 2, background="#3A3A3D", fill="#FFFFFF", shapes=shapes).resize((48, 48), Image.LANCZOS)
    circle = Image.new("L", (48, 48), 0)
    ImageDraw.Draw(circle).ellipse([0, 0, 47, 47], fill=255)
    sheet.paste(mono, (x + pad, pad + 512 - 48), circle)
    sheet.save(path)
    print(f"{path.name}: {sheet.size}")


def _num(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def path_data(pts: list) -> str:
    head, *rest = pts
    return f"M{_num(head[0])},{_num(head[1])} " + " ".join(f"L{_num(x)},{_num(y)}" for x, y in rest) + " Z"


def foreground_xml(shapes: list) -> str:
    """Vordergrund 108 x 108, ein Pfad je Form.

    Kein `android:tint`: die Datei dient auch als `monochrome`-Ebene, die das
    System bei Themen-Icons selbst einfaerbt (nur der Alphakanal zaehlt).
    """
    paths = "".join(
        f"    <!-- {name} -->\n"
        "    <path\n"
        f'        android:fillColor="#FF{FOREST[1:]}"\n'
        f'        android:pathData="{path_data(pts)}" />\n'
        for name, pts in shapes
    )
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        + HEADER + paths + "</vector>\n"
    )


def background_xml() -> str:
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        + HEADER
        + "    <path\n"
        f'        android:fillColor="#FF{CREAM[1:]}"\n'
        '        android:pathData="M0,0h108v108h-108z" />\n'
        "</vector>\n"
    )


def build() -> dict:
    """Dateiname -> Inhalt; laedt und prueft die Geometrie."""
    shapes = load_geometry()
    return {FOREGROUND_FILE: foreground_xml(shapes), BACKGROUND_FILE: background_xml()}


def digest(content: str) -> str:
    """Hasht mit normierten Zeilenenden (core.autocrlf=true im Repo)."""
    return hashlib.sha256(content.replace("\r\n", "\n").encode("utf-8")).hexdigest()


def manifest_text(files: dict) -> str:
    return "".join(f"{digest(files[name])}  {name}\n" for name in sorted(files))


def check_files(files: dict) -> int:
    bad = [n for n in sorted(files)
           if not (DRAWABLE / n).is_file() or digest((DRAWABLE / n).read_text(encoding="utf-8")) != digest(files[n])]
    stale = not MANIFEST.is_file() or MANIFEST.read_text(encoding="utf-8").replace("\r\n", "\n") != manifest_text(files)
    for n in bad:
        print(f"weicht ab: {n}", file=sys.stderr)
    if stale:
        print(f"{MANIFEST.name} ist nicht aktuell", file=sys.stderr)
    return 1 if bad or stale else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="nur pruefen, nichts schreiben")
    ap.add_argument("--preview", action="store_true", help="zusaetzlich preview.png rendern")
    args = ap.parse_args()
    files = build()
    shapes = load_geometry()
    print(f"{len(shapes)} Formen, {sum(len(p) for _, p in shapes)} Punkte, alle in der Schutzzone")
    if args.check:
        return check_files(files)
    assert DRAWABLE.is_dir(), f"{DRAWABLE} fehlt"
    for name in sorted(files):
        (DRAWABLE / name).write_text(files[name], encoding="utf-8", newline="\n")
    MANIFEST.write_text(manifest_text(files), encoding="utf-8", newline="\n")
    print(f"{len(files)} Dateien + {MANIFEST.name} geschrieben")
    if args.preview:
        preview()
    return 0


if __name__ == "__main__":
    sys.exit(main())
