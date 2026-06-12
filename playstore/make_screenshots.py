# Prepares Play-compliant screenshots from today's device captures:
# phone shots cropped to 9:16 (Play maximum aspect is 2:1), wear stays 1:1.
import os
from PIL import Image

SRC = r"C:\Users\ehren.HHK\GebetszeitenApp\build"
DST = r"C:\Users\ehren.HHK\GebetszeitenApp\playstore\screenshots"

os.makedirs(os.path.join(DST, "phone"), exist_ok=True)
os.makedirs(os.path.join(DST, "wear"), exist_ok=True)

phone_shots = [
    ("phone_014.png", "01_zeitstrahl.png"),
    ("phone_now.png", "02_duha_makruh.png"),
]
for src, dst in phone_shots:
    img = Image.open(os.path.join(SRC, src))
    w, h = img.size
    target_h = int(w * 16 / 9)
    if h > target_h:
        img = img.crop((0, 0, w, target_h))  # keep the top (header + timeline)
    img.convert("RGB").save(os.path.join(DST, "phone", dst))
    print(dst, img.size)

wear_shots = [
    ("watch_014c.png", "01_naechstes_gebet.png"),
    ("w019_top.png", "02_restzeit.png"),
    ("w019_bottom.png", "03_danach_liste.png"),
]
for src, dst in wear_shots:
    img = Image.open(os.path.join(SRC, src))
    img.convert("RGB").save(os.path.join(DST, "wear", dst))
    print(dst, img.size)
