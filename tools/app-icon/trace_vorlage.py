#!/usr/bin/env python3
"""Zieht die Figur aus der Icon-Vorlage nach und schreibt geometry.json.

Einmalig gelaufen am 30.10.2026 gegen den Entwurf „Mitte 2“ aus dem
Bildgenerator (`.superpowers/brainstorm/1673-1793359476/icon-vorlage-mitte2.png`,
1280 x 720, nicht eingecheckt). Danach ist geometry.json die Quelle; dieses
Skript braucht nur, wer die Figur aus einer neuen Vorlage holen will.

Was es tut:
- Tinte (dunkel) vom Papier trennen, Pinselkanten glaetten und kleine
  Trockenpinsel-Luecken schliessen, damit die Form in 48 px nicht rauscht.
- Den Bogen der Vorlage verwerfen: er ist dort spitz, gewollt ist ein runder
  andalusischer Hufeisenbogen. Er wird in build_app_icon.py geometrisch
  gebaut; hier stehen nur seine Lage und Masse in Vorlagen-Pixeln.
- Alles so skalieren und mittig setzen, dass jede Form in der 66-dp-Schutzzone
  des adaptiven Symbols liegt.

    python tools/app-icon/trace_vorlage.py <vorlage.png> tools/app-icon/geometry.json
"""
import json
import math
import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_app_icon import SAFE_RADIUS, VIEWPORT, horseshoe_arch  # noqa: E402

# Bogen in Vorlagen-Pixeln: aussen 282 px breit, oben bei y = 87, Pfosten
# enden bei y = 451 (Lage des Bogens in der Vorlage, nur oben rund statt spitz).
ARCH_PX = {"cx": 812.0, "top": 87.0, "width": 282.0, "height": 364.0, "pinch": 0.16, "stroke": 22.0}

# Tinte = Grauwert unter dieser Schwelle (Waldgruen auf Creme).
INK_THRESHOLD = 150
# Groesste Abweichung der Polygone von der geglaetteten Kontur, in Vorlagen-Pixeln.
EPSILON_PX = 2.2
# Formen kleiner als das (Spritzer, Fasern) fallen weg.
MIN_AREA_PX = 400
# Fensterbreite des Glaettens entlang der Kontur, in Konturpunkten (~Pixeln).
SMOOTH_WINDOW = 11


def ink_mask(path: Path) -> np.ndarray:
    grey = cv2.imread(str(path), cv2.IMREAD_GRAYSCALE)
    assert grey is not None, f"{path} nicht lesbar"
    grey = cv2.GaussianBlur(grey, (0, 0), 2.0)
    _, bw = cv2.threshold(grey, INK_THRESHOLD, 255, cv2.THRESH_BINARY_INV)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))
    bw = cv2.morphologyEx(bw, cv2.MORPH_CLOSE, kernel)
    bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    return bw


def smooth(contour: np.ndarray, window: int) -> np.ndarray:
    """Gleitender Mittelwert ueber die geschlossene Kontur: nimmt die Fransen
    des Trockenpinsels weg, laesst die Form des Zuges stehen."""
    pts = contour[:, 0, :].astype(np.float64)
    n = len(pts)
    if n < window * 2:
        return contour
    kernel = np.ones(window) / window
    padded = np.vstack([pts[-window:], pts, pts[:window]])
    xs = np.convolve(padded[:, 0], kernel, mode="same")[window:window + n]
    ys = np.convolve(padded[:, 1], kernel, mode="same")[window:window + n]
    return np.stack([xs, ys], axis=1).reshape(-1, 1, 2).astype(np.float32)


def figure_contours(bw: np.ndarray) -> list:
    """Alle Aussenkonturen ausser dem Bogen der Vorlage (der liegt oben rechts)."""
    contours, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    kept = []
    for c in contours:
        if cv2.contourArea(c) < MIN_AREA_PX:
            continue
        x, y, w, h = cv2.boundingRect(c)
        if x > ARCH_PX["cx"] - ARCH_PX["width"] / 2 - 5 and y < ARCH_PX["top"] + 40:
            continue  # der spitze Bogen der Vorlage
        approx = cv2.approxPolyDP(smooth(c, SMOOTH_WINDOW), EPSILON_PX, True)
        kept.append([(float(p[0][0]), float(p[0][1])) for p in approx])
    return kept


def fit(shapes_px: list, arch_px: list) -> tuple:
    """Skalierung und Verschiebung, die alles in die Schutzzone um (54, 54) legt."""
    # Kleinster umschliessender Kreis statt Mitte des Rechtecks: die Szene
    # laeuft diagonal von links unten nach rechts oben, und der Kreis der
    # Schutzzone nimmt sie so am groessten auf.
    pts = np.array([p for s in shapes_px for p in s] + arch_px, dtype=np.float32)
    (cx, cy), reach = cv2.minEnclosingCircle(pts)
    scale = (SAFE_RADIUS - 0.5) / reach
    return scale, float(cx), float(cy)


def main() -> int:
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    shapes = figure_contours(ink_mask(src))
    arch_poly_px = horseshoe_arch(**ARCH_PX)
    scale, cx, cy = fit(shapes, arch_poly_px)
    c = VIEWPORT / 2

    def to_vp(p):
        return (round(c + (p[0] - cx) * scale, 2), round(c + (p[1] - cy) * scale, 2))

    data = {
        "viewport": VIEWPORT,
        "source": "Entwurf Mitte 2, Bildgenerator, 30.10.2026",
        "arch": {
            "cx": round(c + (ARCH_PX["cx"] - cx) * scale, 2),
            "top": round(c + (ARCH_PX["top"] - cy) * scale, 2),
            "width": round(ARCH_PX["width"] * scale, 2),
            "height": round(ARCH_PX["height"] * scale, 2),
            "pinch": ARCH_PX["pinch"],
            "stroke": round(ARCH_PX["stroke"] * scale, 2),
        },
        "shapes": [{"name": f"figur_{i + 1}", "points": [list(to_vp(p)) for p in s]} for i, s in enumerate(shapes)],
    }
    out.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(shapes)} Formen, {sum(len(s) for s in shapes)} Punkte, Massstab {scale:.4f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
