"""Window icon of the game: the lens of a body camera on a dark worn plate, with a dried-blood REC dot.

Usage: python -I tools/assets/make_icon.py <repo root>

Writes (drawn here from scratch):
  src/main/resources/assets/bodycamhorror/icons/icon_<N>.png  for N = 16, 24, 32, 48, 64, 128, 256
Preview: docs/previews/icon_preview.png (all sizes on black and on a light taskbar grey)
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SIZES = (16, 24, 32, 48, 64, 128, 256)
BIG = 1024

# Palette (project): bone C4BCAA, ash 787266, soot 4E4A42, dried blood 7A1612.
PLATE = (14, 13, 12)
RIM = (78, 74, 66)
RING = (150, 143, 128)
BONE = (196, 188, 170)
GLASS = (22, 21, 19)
BLOOD = (140, 26, 20)


def master():
    """The 1024 px drawing all sizes are taken from."""
    img = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = 40
    d.rounded_rectangle((pad, pad, BIG - pad, BIG - pad), radius=150, fill=PLATE + (255,), outline=RIM + (255,), width=26)

    # Film grain on the plate only.
    rng = np.random.default_rng(1998)
    arr = np.asarray(img).astype(np.float32)
    grain = rng.normal(0, 7, (BIG, BIG, 1))
    mask = arr[..., 3:4] > 0
    arr[..., :3] = np.where(mask, np.clip(arr[..., :3] + grain, 0, 255), arr[..., :3])
    img = Image.fromarray(arr.astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)

    c = BIG // 2 + 30
    # Lens barrel: thick pale ring, dark gap, inner ring, glass.
    for r, col in ((330, RING), (282, PLATE), (246, RIM), (214, GLASS)):
        d.ellipse((c - r, c - r, c + r, c + r), fill=col + (255,))
    # Pupil.
    d.ellipse((c - 92, c - 92, c + 92, c + 92), fill=(6, 6, 5, 255))
    # A tired highlight on the glass, upper left.
    hl = Image.new("L", (BIG, BIG), 0)
    hd = ImageDraw.Draw(hl)
    hd.arc((c - 175, c - 175, c + 175, c + 175), start=200, end=260, fill=200, width=34)
    hd.ellipse((c - 120, c - 150, c - 72, c - 102), fill=150)
    hl = hl.filter(ImageFilter.GaussianBlur(6))
    glow = Image.new("RGBA", (BIG, BIG), BONE + (0,))
    glow.putalpha(hl)
    img.alpha_composite(glow)

    # REC dot, upper right corner of the plate.
    d = ImageDraw.Draw(img)
    rx, ry, rr = BIG - 205, 205, 72
    d.ellipse((rx - rr, ry - rr, rx + rr, ry + rr), fill=BLOOD + (255,))
    return img


def sized(src, n):
    out = src.resize((n, n), Image.LANCZOS)
    if n <= 32:
        # Small sizes: a touch of extra contrast so the ring reads on the taskbar.
        a = np.asarray(out).astype(np.float32)
        a[..., :3] = np.clip((a[..., :3] - 20) * 1.12 + 20, 0, 255)
        out = Image.fromarray(a.astype(np.uint8), "RGBA")
    return out


def preview(root, icons):
    W = sum(n + 16 for n in SIZES) + 16
    H = (256 + 32) * 2
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    ImageDraw.Draw(canvas).rectangle((0, H // 2, W, H), fill=(200, 200, 200, 255))
    for row in range(2):
        x = 16
        for n in SIZES:
            canvas.alpha_composite(icons[n], (x, row * (H // 2) + 16 + 256 - n))
            x += n + 16
    canvas.convert("RGB").save(root / "docs/previews/icon_preview.png")


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out = root / "src/main/resources/assets/bodycamhorror/icons"
    out.mkdir(parents=True, exist_ok=True)
    big = master()
    icons = {n: sized(big, n) for n in SIZES}
    for n, im in icons.items():
        im.save(out / f"icon_{n}.png", optimize=True)
    preview(root, icons)
    print("wrote", ", ".join(f"icon_{n}.png" for n in SIZES), "and docs/previews/icon_preview.png")


if __name__ == "__main__":
    main()
