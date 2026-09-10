#!/usr/bin/env python3
"""Erzeugt die 23 Countdown-Symbole in app/src/main/res/drawable/.

Ablauf: elf Zeichen (`0`-`9` und `h`) einmal als Sieben-Segment-Geometrie
definieren -> zu 23 Symbolen zusammensetzen (`1h`…`9h`, `50 40 30 20 10`,
`9`…`1`) -> je eine Vektordatei in der Form von `ic_notification.xml`
schreiben -> `icons.sha256` als Manifest daneben legen.

Warum ein Generator und keine 23 handgeschriebenen Dateien: 23 Dateien mit
Pfaddaten von Hand sind 23 Gelegenheiten fuer einen Zahlendreher, den im
Quelltext niemand sieht. Hier stehen die Ziffern genau einmal.

Warum keine Schriftart: keine Font-Abhaengigkeit, keine Lizenzfrage, und bei
24 dp sind Rechteck-Segmente eindeutiger als extrahierte Schriftumrisse.

Eine Ausnahme von der reinen Sieben-Segment-Form gibt es: die alleinstehende
`1` bekommt Fuss und Fahne, sonst waere sie in der Statusleiste ein blosser
Strich (siehe SINGLE_SEGMENTS).

Wiederholbar: zweimal laufen lassen ergibt byte-gleiche Dateien.
Eingebaute Pruefungen (Skript bricht hart ab): genau 23 Symbole, jedes Zeichen
definiert, alle Koordinaten im Viewport, kein Symbol pfadgleich mit einem
anderen.

    python build_icons.py             # schreibt die Vektoren + das Manifest
    python build_icons.py --check     # nur pruefen, nichts schreiben
    python build_icons.py --preview   # zusaetzlich preview.png (braucht Pillow)

Nur ausfuehren, wenn sich die Ziffernform aendert (siehe README.md).
"""
import argparse
import hashlib
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DRAWABLE = REPO / "app" / "src" / "main" / "res" / "drawable"
MANIFEST = Path(__file__).resolve().parent / "icons.sha256"
PREVIEW = Path(__file__).resolve().parent / "preview.png"

# --- Masse im 24x24-Viewport -------------------------------------------------
# Alles hier ist dp im Viewport, nicht Pixel. Wer die Symbole auf einem echten
# Geraet zu klein findet, dreht an genau diesen Zahlen — sonst nirgends.

VIEWPORT = 24.0
MARGIN = 2.0            # Randabstand links/rechts beim breitesten Symbol
GLYPH_HEIGHT = 19.0     # Zeichenhoehe; oben/unten bleiben je 2.5 dp
GLYPH_TOP = (VIEWPORT - GLYPH_HEIGHT) / 2.0

# Zweistellig muss enger stehen als einstellig, sonst wird es unleserlich:
# zwei Zeichen plus Luecke fuellen die Breite zwischen den Raendern exakt aus.
# Die Luecke ist bewusst breiter als ein Balken: in `1h` stehen sonst der
# Balken der `1` und der linke Stamm des `h` so dicht, dass es wie `11` aussieht.
PAIR_GAP = 2.8
PAIR_WIDTH = (VIEWPORT - 2 * MARGIN - PAIR_GAP) / 2.0   # = 8.6
PAIR_STROKE = 2.6

# Ein einzelnes Zeichen darf breiter und fetter sein — es hat den Platz.
SINGLE_WIDTH = 13.0
SINGLE_STROKE = 3.4

# Breite der alleinstehenden `1` (siehe SINGLE_SEGMENTS weiter unten). Sie ist
# schmaler als SINGLE_WIDTH, weil ein 13 dp breiter Fuss unter einem 3.4 dp
# schmalen Stamm wie ein Sockel aussieht und nicht wie eine Ziffer.
ONE_WIDTH = 9.0

# --- Die elf Zeichen ---------------------------------------------------------
# Sieben-Segment-Belegung. Buchstaben wie auf jedem Datenblatt:
#   a = oben, b = rechts oben, c = rechts unten, d = unten,
#   e = links unten, f = links oben, g = Mitte.
# Dazu drei Felder, die kein Datenblatt kennt und die nur die alleinstehende
# `1` benutzt (siehe SINGLE_SEGMENTS):
#   i = Stamm oben (Mitte), j = Stamm unten (Mitte), k = Fahne oben links.
SEGMENTS = {
    "0": "abcdef",
    "1": "bc",
    "2": "abged",
    "3": "abgcd",
    "4": "fgbc",
    "5": "afgcd",
    "6": "afgecd",
    "7": "abc",
    "8": "abcdefg",
    "9": "abcdfg",
    # Kleines h: durchgehender linker Stamm (f+e), Querbalken (g), rechter
    # Stamm nur unten (c).
    "h": "fegc",
}

# Die `1` bekommt nur die Breite ihres Balkens, sonst klaffte in `1h` und `10`
# eine Luecke, die wie ein Leerzeichen aussieht.
NARROW = "1"

# Belegung, die NUR fuer ein alleinstehendes Zeichen gilt.
#
# Die Sieben-Segment-`1` ist ein einzelner senkrechter Balken. Auf einem
# Display steht sie in einer bekannten Ziffernzelle neben ihresgleichen; in
# der Statusleiste steht sie allein zwischen Systemsymbolen, und dort ist ein
# 3.4 x 19 dp Strich eher ein Trennstrich oder ein Renderfehler als eine Eins.
# Ausgerechnet dieses Symbol steht in der letzten Minute vor dem Gebet, wird
# also am haeufigsten angesehen. Es bekommt darum Fuss und Fahne.
#
# In `1h` und `10` bleibt es beim blossen Balken: dort liest der Nachbar mit,
# und Fuss plus Fahne machten die ohnehin engen Doppelsymbole nur voller.
SINGLE_SEGMENTS = {
    "1": "ijdk",
}


def _boxes(x0: float, width: float, stroke: float) -> dict:
    """Die Felder eines Zeichens als (x, y, breite, hoehe) — die sieben
    Segmente plus die drei Sonderfelder der alleinstehenden `1`."""
    y0 = GLYPH_TOP
    h = GLYPH_HEIGHT
    t = stroke
    mid = y0 + (h - t) / 2.0    # Oberkante des Mittelbalkens
    half = (h + t) / 2.0        # Laenge eines senkrechten Segments
    return {
        "a": (x0, y0, width, t),
        "b": (x0 + width - t, y0, t, half),
        "c": (x0 + width - t, mid, t, half),
        "d": (x0, y0 + h - t, width, t),
        "e": (x0, mid, t, half),
        "f": (x0, y0, t, half),
        "g": (x0, mid, width, t),
        # Nur fuer die alleinstehende `1`: Stamm in der Mitte statt rechts,
        # dazu die Fahne links oben, die bis an den Stamm heranreicht.
        "i": (x0 + (width - t) / 2.0, y0, t, half),
        "j": (x0 + (width - t) / 2.0, mid, t, half),
        # Die Fahne reicht bis an die rechte Kante des Stamms heran und
        # ueberlappt ihn: als Balken (breiter als hoch) ist sie eindeutig ein
        # waagerechtes Feld, und das Bild ist dasselbe wie bei einem Stummel,
        # der am Stamm endet.
        "k": (x0, y0, (width - t) / 2.0 + t, t),
    }


def _segments(char: str, single: bool) -> str:
    """Belegung eines Zeichens; alleinstehend gilt SINGLE_SEGMENTS zuerst."""
    if single and char in SINGLE_SEGMENTS:
        return SINGLE_SEGMENTS[char]
    return SEGMENTS[char]


def _advance(char: str, single: bool, width: float, stroke: float) -> float:
    if single and char in SINGLE_SEGMENTS:
        return ONE_WIDTH
    return stroke if char in NARROW else width


def _num(value: float) -> str:
    """Kurze, stabile Dezimaldarstellung — sonst waere der Lauf nicht
    wiederholbar, sobald irgendwo ein 1e-15 auftaucht."""
    text = f"{round(value, 2):.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def _rect(x: float, y: float, w: float, h: float) -> str:
    """Ein Rechteck, immer im Uhrzeigersinn. Gleicher Umlaufsinn fuer alle
    Rechtecke ist Pflicht: die Vektorgrafik fuellt nach nonZero, bei
    gemischtem Umlauf loeschten sich Ueberlappungen gegenseitig weg."""
    return f"M{_num(x)},{_num(y)}H{_num(x + w)}V{_num(y + h)}H{_num(x)}Z"


def rects_for(text: str) -> list:
    """Alle Rechtecke eines Symbols, horizontal und vertikal zentriert."""
    single = len(text) == 1
    width = SINGLE_WIDTH if single else PAIR_WIDTH
    stroke = SINGLE_STROKE if single else PAIR_STROKE
    gap = 0.0 if single else PAIR_GAP

    advances = [_advance(c, single, width, stroke) for c in text]
    total = sum(advances) + gap * (len(text) - 1)
    x = (VIEWPORT - total) / 2.0

    out = []
    for char, advance in zip(text, advances):
        boxes = _boxes(x, advance, stroke)
        for name in _segments(char, single):
            out.append(boxes[name])
        x += advance + gap
    return out


def path_data(text: str) -> str:
    return " ".join(_rect(*box) for box in rects_for(text))


def vector_xml(text: str) -> str:
    """Dateiform exakt wie app/src/main/res/drawable/ic_notification.xml."""
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24"\n'
        '    android:tint="#FFFFFFFF">\n'
        # Kein "--" im Kommentar: XML verbietet es, und aapt2 bricht darueber ab.
        "    <!-- Erzeugt von tools/notification-icons/build_icons.py;"
        " nicht von Hand aendern. -->\n"
        "    <path\n"
        '        android:fillColor="#FFFFFFFF"\n'
        f'        android:pathData="{path_data(text)}" />\n'
        "</vector>\n"
    )


def icon_texts() -> list:
    """Die 23 Symbole in der Reihenfolge, in der die Restzeit sie durchlaeuft."""
    return (
        [f"{h}h" for h in range(9, 0, -1)]
        + [f"{m}0" for m in range(5, 0, -1)]
        + [str(m) for m in range(9, 0, -1)]
    )


def filename(text: str) -> str:
    return f"ic_countdown_{text}.xml"


def build() -> dict:
    """Dateiname -> Inhalt. Enthaelt alle harten Pruefungen."""
    texts = icon_texts()
    assert len(texts) == 23, f"23 Symbole erwartet, {len(texts)} gebaut"
    assert len(set(texts)) == 23, "doppelter Symbolname"

    files = {}
    seen = {}
    for text in texts:
        for char in text:
            assert char in SEGMENTS, f"Zeichen '{char}' ist nicht definiert"
        boxes = rects_for(text)
        assert boxes, f"{text}: leerer Pfad"
        for x, y, w, h in boxes:
            assert w > 0 and h > 0, f"{text}: Rechteck ohne Flaeche"
            assert -0.001 <= x and x + w <= VIEWPORT + 0.001, f"{text}: x ausserhalb des Viewports"
            assert -0.001 <= y and y + h <= VIEWPORT + 0.001, f"{text}: y ausserhalb des Viewports"
        data = path_data(text)
        assert data not in seen, f"{text} ist pfadgleich mit {seen.get(data)}"
        seen[data] = text
        files[filename(text)] = vector_xml(text)
    return files


def digest(content: str) -> str:
    """Hasht mit normierten Zeilenenden — dieses Repo laeuft mit
    core.autocrlf=true, im Arbeitsbaum stehen also je nach Auscheckvorgang
    CRLF. Der Inhalt ist trotzdem derselbe."""
    normalized = content.replace("\r\n", "\n")
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def manifest_text(files: dict) -> str:
    lines = [f"{digest(files[name])}  {name}" for name in sorted(files)]
    return "\n".join(lines) + "\n"


def write(files: dict) -> None:
    assert DRAWABLE.is_dir(), f"{DRAWABLE} fehlt — Modulstruktur geaendert?"
    for name in sorted(files):
        (DRAWABLE / name).write_text(files[name], encoding="utf-8", newline="\n")
    MANIFEST.write_text(manifest_text(files), encoding="utf-8", newline="\n")
    print(f"{len(files)} Symbole nach {DRAWABLE} geschrieben, Manifest: {MANIFEST.name}")


def check(files: dict) -> int:
    """Vergleicht Repo-Stand mit dem, was das Skript erzeugen wuerde."""
    problems = []
    for name in sorted(files):
        target = DRAWABLE / name
        if not target.is_file():
            problems.append(f"fehlt: {name}")
        elif digest(target.read_text(encoding="utf-8")) != digest(files[name]):
            problems.append(f"abweichend: {name}")
    if not MANIFEST.is_file():
        problems.append(f"fehlt: {MANIFEST.name}")
    elif digest(MANIFEST.read_text(encoding="utf-8")) != digest(manifest_text(files)):
        problems.append(f"abweichend: {MANIFEST.name}")
    if problems:
        print("Repo weicht vom Generator ab:\n  " + "\n  ".join(problems), file=sys.stderr)
        return 1
    print(f"{len(files)} Symbole + Manifest stimmen mit dem Generator ueberein.")
    return 0


def preview() -> None:
    """Rendert eine Kontaktbogen-PNG: jedes Symbol gross und in Originalgroesse.
    Nur ein Hilfsmittel fuer das Auge, nichts davon geht in die App."""
    from PIL import Image, ImageDraw  # optional, nur fuer --preview

    texts = icon_texts()
    scale = 16          # Ueberabtastung fuer weiche Kanten
    big = 72            # Kantenlaenge der grossen Darstellung
    real = 24           # Originalgroesse, so gross ist das Symbol im Geraet
    cols = 12
    pad = 10
    strip_h = real + 2 * pad
    rows = (len(texts) + cols - 1) // cols
    cell = big + pad
    width = cols * cell + pad
    height = strip_h + rows * (cell + 14) + pad

    sheet = Image.new("RGB", (width, height), (24, 24, 28))
    draw = ImageDraw.Draw(sheet)

    edge = int(VIEWPORT) * scale

    def glyph_image(text: str, size: int) -> Image.Image:
        img = Image.new("L", (edge, edge), 0)
        d = ImageDraw.Draw(img)
        for x, y, w, h in rects_for(text):
            d.rectangle(
                [x * scale, y * scale, (x + w) * scale - 1, (y + h) * scale - 1],
                fill=255,
            )
        return img.resize((size, size), Image.LANCZOS)

    # Oberer Streifen: alle Symbole in Originalgroesse nebeneinander, so wie
    # sie in der Statusleiste stuenden.
    draw.rectangle([0, 0, width, strip_h], fill=(10, 10, 12))
    x = pad
    for text in texts:
        sheet.paste((235, 235, 240), (x, pad), glyph_image(text, real))
        x += real + 2

    for index, text in enumerate(texts):
        col, row = index % cols, index // cols
        px = pad + col * cell
        py = strip_h + row * (cell + 14)
        sheet.paste((235, 235, 240), (px, py), glyph_image(text, big))
        draw.text((px, py + big + 2), text, fill=(150, 150, 160))

    sheet.save(PREVIEW)
    print(f"Vorschau: {PREVIEW}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="nur pruefen, nichts schreiben")
    parser.add_argument("--preview", action="store_true", help="zusaetzlich preview.png rendern")
    args = parser.parse_args()

    files = build()
    if args.check:
        return check(files)
    write(files)
    if args.preview:
        preview()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
