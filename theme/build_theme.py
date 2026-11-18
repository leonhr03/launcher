"""Baut das Xiaomi/HyperOS-Theme "Clean iOS" (.mtz).

Inhalt: Hintergrundbilder (Home + Sperrbildschirm) und iOS-Symbolform (Squircle) für alle Apps.
Aufruf: python3 theme/build_theme.py  ->  theme/CleanIOS.mtz
"""
import io, math, os, random, zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "CleanIOS.mtz")
FONT = os.path.join(HERE, "..", "app", "src", "main", "res", "font", "inter_bold.ttf")
FONT_REG = os.path.join(HERE, "..", "app", "src", "main", "res", "font", "inter_semibold.ttf")


def wallpaper(w, h, seed=7):
    """Eigenes, weiches Farbverlaufs-Hintergrundbild im iOS-Stil."""
    random.seed(seed)
    img = Image.new("RGB", (w, h), (12, 14, 30))
    blobs = Image.new("RGB", (w, h), (12, 14, 30))
    d = ImageDraw.Draw(blobs)
    colors = [(88, 86, 214), (0, 122, 255), (255, 45, 85), (175, 82, 222), (90, 200, 250)]
    for i, c in enumerate(colors):
        cx = int(w * (0.15 + 0.7 * random.random()))
        cy = int(h * (0.1 + 0.8 * i / len(colors)))
        r = int(w * (0.45 + 0.25 * random.random()))
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c)
    blobs = blobs.filter(ImageFilter.GaussianBlur(w // 5))
    img = Image.blend(img, blobs, 0.85)
    # leichte Abdunklung oben/unten für gut lesbare Uhr & Symbole
    shade = Image.new("L", (1, h))
    for y in range(h):
        t = y / h
        shade.putpixel((0, y), int(70 * (1 - min(t, 1 - t) * 2) ** 2))
    shade = shade.resize((w, h))
    img = Image.composite(Image.new("RGB", (w, h), (0, 0, 0)), img, shade)
    return img


def squircle(size, n=5.0, inset=0):
    """Superellipse wie bei iOS (kontinuierliche Ecken), 4x supersampled."""
    s = size * 4
    m = Image.new("L", (s, s), 0)
    pts = []
    a = (s - 2 * inset * 4) / 2
    c = s / 2
    for i in range(720):
        t = 2 * math.pi * i / 720
        ct, st = math.cos(t), math.sin(t)
        x = c + a * math.copysign(abs(ct) ** (2 / n), ct)
        y = c + a * math.copysign(abs(st) ** (2 / n), st)
        pts.append((x, y))
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def png(img):
    b = io.BytesIO()
    img.save(b, "PNG")
    return b.getvalue()


def jpg(img, q=92):
    b = io.BytesIO()
    img.convert("RGB").save(b, "JPEG", quality=q)
    return b.getvalue()


def icons_module():
    """Symbol-Modul: Maske + Hintergrund in Squircle-Form für alle App-Symbole."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for folder, size in [("drawable-xxhdpi", 192), ("drawable-xxxhdpi", 288)]:
            inset = int(size * 0.035)
            mask = squircle(size, inset=inset)
            mask_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            mask_img.putalpha(mask)
            black = Image.new("RGBA", (size, size), (0, 0, 0, 255))
            black.putalpha(mask)
            bg = Image.new("RGBA", (size, size), (255, 255, 255, 255))
            bg.putalpha(mask)
            z.writestr(f"res/{folder}/icon_mask.png", png(black))
            z.writestr(f"res/{folder}/icon_background.png", png(bg))
            z.writestr(f"res/{folder}/icon_border.png", png(Image.new("RGBA", (size, size), (0, 0, 0, 0))))
        # Symbole ohne eigenes Theme-Bild etwas verkleinert in die Squircle-Fläche setzen
        z.writestr("transform_config.xml", """<?xml version="1.0" encoding="utf-8"?>
<IconTransform>
    <PointsMapping>
        <Point fromX="0" fromY="0" toX="14" toY="14" />
        <Point fromX="192" fromY="0" toX="178" toY="14" />
        <Point fromX="0" fromY="192" toX="14" toY="178" />
    </PointsMapping>
</IconTransform>
""")
    return buf.getvalue()


def preview(wall, title, icons=True):
    p = wall.resize((540, 1200))
    d = ImageDraw.Draw(p)
    try:
        f_big = ImageFont.truetype(FONT, 110)
        f_small = ImageFont.truetype(FONT_REG, 30)
    except Exception:
        f_big = f_small = ImageFont.load_default()
    if icons:
        m = squircle(96, inset=3)
        cols = [(52, 199, 89), (0, 122, 255), (255, 149, 0), (255, 59, 48), (175, 82, 222), (90, 200, 250), (255, 204, 0), (142, 142, 147)]
        k = 0
        for row in range(4):
            for col in range(4):
                tile = Image.new("RGB", (96, 96), cols[k % len(cols)])
                k += 1
                p.paste(tile, (40 + col * 120, 180 + row * 150), m)
        dock = Image.new("RGB", (500, 130), (255, 255, 255))
        dm = Image.new("L", (500, 130), 0)
        ImageDraw.Draw(dm).rounded_rectangle([0, 0, 499, 129], 45, fill=70)
        p.paste(dock, (20, 1040), dm)
        for col in range(4):
            p.paste(Image.new("RGB", (96, 96), cols[(col + 3) % len(cols)]), (40 + col * 120, 1057), m)
    else:
        d.text((270, 120), "Dienstag, 18. November", fill="white", font=f_small, anchor="mm")
        d.text((270, 230), "9:41", fill="white", font=f_big, anchor="mm")
    d.text((270, 1180), title, fill=(255, 255, 255), font=f_small, anchor="mm")
    return p


def main():
    home = wallpaper(1440, 3200, seed=7)
    lock = wallpaper(1440, 3200, seed=11)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("description.xml", """<?xml version="1.0" encoding="UTF-8"?>
<theme>
    <title>Clean iOS</title>
    <designer>Clean Launcher</designer>
    <author>Clean Launcher</author>
    <version>1.0</version>
    <uiVersion>14</uiVersion>
    <description>Aufgeräumtes iOS-Design: Squircle-Symbole und weiche Farbverlauf-Hintergründe.</description>
</theme>
""")
        z.writestr("wallpaper/default_wallpaper.jpg", jpg(home))
        z.writestr("wallpaper/default_lock_wallpaper.jpg", jpg(lock))
        z.writestr("icons", icons_module())
        z.writestr("preview/preview_launcher_0.jpg", jpg(preview(home, "Startbildschirm")))
        z.writestr("preview/preview_lockscreen_0.jpg", jpg(preview(lock, "Sperrbildschirm", icons=False)))
        z.writestr("preview/preview_icons_0.jpg", jpg(preview(home, "Symbole")))
    home.save(os.path.join(HERE, "hintergrund-home.jpg"), quality=92)
    lock.save(os.path.join(HERE, "hintergrund-sperrbildschirm.jpg"), quality=92)
    preview(home, "Startbildschirm").save(os.path.join(HERE, "vorschau-home.jpg"), quality=90)
    preview(lock, "Sperrbildschirm", icons=False).save(os.path.join(HERE, "vorschau-sperrbildschirm.jpg"), quality=90)
    print("fertig:", OUT, os.path.getsize(OUT), "Bytes")


if __name__ == "__main__":
    main()
