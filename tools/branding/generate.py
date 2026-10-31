#!/usr/bin/env python3
"""Generuje assety brandingu z gotowego logo (KONSPEKT §4.0). Nie projektuje niczego od nowa:
tylko wycina, skaluje i składa piksele z branding/source/ketchup-player-logo.jpg.

Uruchomienie: python3 tools/branding/generate.py   (wymaga Pillow i numpy)
Wyniki: branding/derived/* oraz zasoby Androida w app/src/main/res.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "branding/source/ketchup-player-logo.jpg"
OUT = ROOT / "branding/derived"
RES = ROOT / "app/src/main/res"
BG = (0x11, 0x12, 0x17)  # tło logo #111217

# Współrzędne w oryginale 2752x1536 (zmierzone na miniaturze 1000x558, skala 2.752).
S = 2.752
SHIELD = tuple(int(v * S) for v in (280, 25, 722, 535))      # tarcza z napisem
RIBBON = tuple(int(v * S) for v in (283, 385, 720, 505))      # wstęga z napisem
CREST = tuple(int(v * S) for v in (283, 25, 720, 392))        # tarcza z postacią (bez napisu)
HEAD_SEARCH = tuple(int(v * S) for v in (398, 55, 590, 268))  # obszar poszukiwania głowy (bez butelki i małego pomidora)


def remove_background(img: Image.Image) -> Image.Image:
    """Przezroczyste tło: flood-fill od krawędzi (tarcza ze złotą ramką zatrzymuje wypełnienie)."""
    rgb = img.convert("RGB").copy()
    key = (255, 0, 255)
    w, h = rgb.size
    for xy in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0), (w // 2, h - 1), (0, h // 2), (w - 1, h // 2)]:
        if rgb.getpixel(xy) != key:
            ImageDraw.floodfill(rgb, xy, key, thresh=26)
    arr = np.array(rgb)
    is_bg = (arr[:, :, 0] == 255) & (arr[:, :, 1] == 0) & (arr[:, :, 2] == 255)
    alpha = Image.fromarray(np.where(is_bg, 0, 255).astype("uint8"), "L")
    # bez halo: zerodowanie o 1 px + lekkie rozmycie krawędzi
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    out = img.convert("RGBA")
    out.putalpha(alpha)
    return out


def crop(img: Image.Image, box) -> Image.Image:
    c = img.crop(box)
    bbox = c.getchannel("A").getbbox()
    return c.crop(bbox) if bbox else c


def fit(img: Image.Image, w=None, h=None) -> Image.Image:
    r = img.width / img.height
    if w and h:
        if w / h > r:
            w = round(h * r)
        else:
            h = round(w / r)
    elif w:
        h = round(w / r)
    else:
        w = round(h * r)
    return img.resize((w, h), Image.LANCZOS)


def on_canvas(size, bg=None):
    return Image.new("RGBA", size, bg if bg else (0, 0, 0, 0))


def paste_center(canvas, im, dx=0, dy=0):
    canvas.alpha_composite(im, ((canvas.width - im.width) // 2 + dx, (canvas.height - im.height) // 2 + dy))
    return canvas


def circle_mask(size, feather=2):
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    m = m.resize((size, size), Image.LANCZOS)
    return m.filter(ImageFilter.GaussianBlur(feather)) if feather else m


def tomato_bbox(src_rgb: Image.Image):
    """Obwiednia pomidora wyznaczona z pikseli (czerwień skórki + zieleń liści) w obszarze głowy.
    Percentyle 1-99 odrzucają pojedyncze krople i glitche."""
    x0, y0, x1, y1 = HEAD_SEARCH
    arr = np.array(src_rgb.crop((x0, y0, x1, y1))).astype(int)
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    red = (r > 150) & (g < 95) & (b < 95)
    green = (g > 110) & (r < 130) & (b < 100) & (g > r)
    ys, xs = np.nonzero(red | green)
    lo_x, hi_x = np.percentile(xs, [1, 99])
    lo_y, hi_y = np.percentile(ys, [1, 99])
    return x0 + lo_x, y0 + lo_y, x0 + hi_x, y0 + hi_y


def make_mark(src_rgb: Image.Image) -> Image.Image:
    """Sygnet: głowa pomidora wycentrowana według obwiedni z pikseli, w kole (przezroczyste tło poza kołem)."""
    bx0, by0, bx1, by1 = tomato_bbox(src_rgb)
    cx, cy = (bx0 + bx1) / 2, (by0 + by1) / 2
    r = int(max(bx1 - bx0, by1 - by0) / 2 * 1.28)
    head = src_rgb.crop((int(cx - r), int(cy - r), int(cx + r), int(cy + r))).convert("RGBA")
    head.putalpha(circle_mask(head.width, feather=3))
    return head


def monochrome_mark(size: int) -> Image.Image:
    """Warstwa monochromatyczna (Android 13+): koło-głowa z wyciętym ▶."""
    big = size * 4
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((0, 0, big - 1, big - 1), fill=(255, 255, 255, 255))
    tri = [(big * 0.38, big * 0.28), (big * 0.38, big * 0.72), (big * 0.74, big * 0.5)]
    d.polygon(tri, fill=(0, 0, 0, 0))
    return im.resize((size, size), Image.LANCZOS)


def save(img, path, **kw):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, **kw)
    print("zapisano", path.relative_to(ROOT), img.size)


def main():
    src = Image.open(SRC).convert("RGB")
    transparent = remove_background(src)
    full = crop(transparent, SHIELD)
    ribbon = crop(transparent, RIBBON)
    crest = crop(transparent, CREST)
    save(full, OUT / "logo-transparent.png")

    mark = make_mark(src)
    save(mark.resize((512, 512), Image.LANCZOS), OUT / "logo-mark.png")

    # Podglądy kontroli halo na jasnym i ciemnym tle
    for name, color in (("light", (245, 245, 245, 255)), ("dark", (20, 20, 20, 255))):
        bgc = on_canvas((full.width // 2, full.height // 2), color)
        paste_center(bgc, fit(full, h=full.height // 2))
        save(bgc.convert("RGB"), OUT / f"preview-transparent-{name}.png")

    # Adaptive icon: foreground 432x432 (108dp @xxxhdpi), sygnet w bezpiecznym kole 66dp = 264 px
    fg = on_canvas((432, 432))
    paste_center(fg, mark.resize((264, 264), Image.LANCZOS))
    save(fg, RES / "drawable-nodpi/ic_launcher_foreground.png")
    mono = on_canvas((432, 432))
    paste_center(mono, monochrome_mark(264))
    save(mono, RES / "drawable-nodpi/ic_launcher_monochrome.png")

    # Legacy mipmapy (mdpi..xxxhdpi) pomijamy: minSdk 28 >= 26, więc wystarcza adaptive icon (lint: IconXmlAndPng).
    play = on_canvas((512, 512), BG + (255,))
    paste_center(play, mark.resize((420, 420), Image.LANCZOS))
    save(play.convert("RGB"), OUT / "play-store-icon-512.png")

    # Banner Android TV: tarcza z postacią + wstęga z napisem (napis szeroki = czytelny na kafelku)
    def banner(w, h):
        c = on_canvas((w, h), BG + (255,))
        rib = fit(ribbon, w=round(w * 0.88))
        crest_h = h - rib.height - round(h * 0.04)
        cr = fit(crest, h=crest_h)
        c.alpha_composite(cr, ((w - cr.width) // 2, round(h * 0.02)))
        c.alpha_composite(rib, ((w - rib.width) // 2, h - rib.height - round(h * 0.02)))
        return c.convert("RGB")
    # Banner TV = 320x180 dp; 640x360 px (xhdpi) jako nodpi, żeby lint nie wymagał wariantów gęstości.
    save(banner(640, 360), RES / "drawable-nodpi/banner.png")
    save(banner(320, 180), OUT / "banner-320x180.png")

    # Splash (Android 12+): sygnet w środku okręgu 2/3 ikony 288dp (1152 px)
    sp = on_canvas((1152, 1152))
    paste_center(sp, mark.resize((640, 640), Image.LANCZOS))
    save(sp, RES / "drawable-nodpi/splash_icon.png")

    # Wersja pozioma (tarcza + napis obok siebie) i feature-graphic 1024x500
    h = 360
    cr = fit(crest, h=h)
    rb = fit(ribbon, w=900)
    horiz = on_canvas((cr.width + 40 + rb.width, h))
    horiz.alpha_composite(cr, (0, 0))
    horiz.alpha_composite(rb, (cr.width + 40, (h - rb.height) // 2))
    save(horiz, OUT / "logo-horizontal.png")
    fg_img = on_canvas((1024, 500), BG + (255,))
    paste_center(fg_img, fit(full, h=470))
    save(fg_img.convert("RGB"), OUT / "feature-graphic-1024x500.png")


if __name__ == "__main__":
    main()
