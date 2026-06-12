from PIL import Image
import os

out_dir = r"C:\Users\ehren.HHK\GebetszeitenApp\playstore\screenshots\phone"
os.makedirs(out_dir, exist_ok=True)

# Verwende die ursprünglichen Screenshots mit besserer Auswahl
originals = [
    (r"C:\Users\ehren.HHK\Downloads\screenshot.png", "01_zeitstrahl.png", "Oben — Hauptscreen"),
    (r"C:\Users\ehren.HHK\Downloads\screenshot2.png", "02_makruh.png", "Mitte — Makruh-Zeiten"),
    (r"C:\Users\ehren.HHK\Downloads\screenshot3.png", "03_einstellungen.png", "Einstellungen"),
    (r"C:\Users\ehren.HHK\Downloads\screenshot4.png", "04_widget.png", "Widget-Info"),
]

for input_path, output_name, label in originals:
    if not os.path.exists(input_path):
        print(f"Fehler: {input_path} nicht gefunden")
        continue

    img = Image.open(input_path)
    print(f"{label}: {img.size}")

    # Crop: Remove status bar (top ~100px) and nav bar (bottom ~150px)
    w, h = img.size
    crop_box = (0, 100, w, h - 150)
    cropped = img.crop(crop_box)

    # Resize to Play Store standard (ensure 9:16 aspect)
    # Target: 1440 x 2560 is good for Play Store
    target_w = 1440
    target_h = int(1440 * 16 / 9)

    if cropped.width != target_w:
        aspect = cropped.width / cropped.height
        if aspect > 9/16:
            # Too wide, crop sides
            new_w = int(cropped.height * 9 / 16)
            left = (cropped.width - new_w) // 2
            cropped = cropped.crop((left, 0, left + new_w, cropped.height))

        cropped = cropped.resize((target_w, target_h), Image.Resampling.LANCZOS)

    out_path = os.path.join(out_dir, output_name)
    cropped.save(out_path, quality=90)
    print(f"  > {output_name} ({cropped.size})")

print("\nAlle Phone-Screenshots neu verarbeitet!")
