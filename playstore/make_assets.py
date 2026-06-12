# Generates Play Store listing graphics matching the app's adaptive icon
# (green #0E7A5F background, white crescent).
from PIL import Image, ImageDraw, ImageFont

GREEN = (14, 122, 95, 255)
WHITE = (255, 255, 255, 255)


def crescent(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, fill, bg):
    """Crescent = full circle minus a circle shifted to the upper right."""
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill)
    off = r * 0.42
    r2 = r * 0.86
    draw.ellipse([cx + off - r2, cy - off - r2, cx + off + r2, cy - off + r2], fill=bg)


# --- 512x512 app icon (full-bleed green, Play renders its own mask) ---
icon = Image.new("RGBA", (512, 512), GREEN)
d = ImageDraw.Draw(icon)
crescent(d, 256, 276, 150, WHITE, GREEN)
icon.convert("RGB").save(r"C:\Users\ehren.HHK\GebetszeitenApp\playstore\icon_512.png")

# --- 1024x500 feature graphic ---
fg = Image.new("RGBA", (1024, 500), GREEN)
d = ImageDraw.Draw(fg)
crescent(d, 200, 270, 120, WHITE, GREEN)
try:
    font_big = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 88)
    font_small = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 36)
except OSError:
    font_big = ImageFont.load_default()
    font_small = ImageFont.load_default()
d.text((380, 160), "Gebetszeiten", font=font_big, fill=WHITE)
d.text((384, 280), "Offline · privat · maximal akkusparend", font=font_small, fill=(214, 240, 230, 255))
fg.convert("RGB").save(r"C:\Users\ehren.HHK\GebetszeitenApp\playstore\feature_1024x500.png")

print("ok")
