"""Generates item textures (16x16 RGBA PNG) for the mod.

Usage: python -I tools/assets/make_items.py <repo root>
flashlight.png: a handheld tactical flashlight lying on the diagonal like vanilla tools
(tail bottom-left, head top-right), black anodized body with knurled grip, a switch,
a wide bezel and a pale lens.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

N = 16
COL = {
    "outline": (18, 18, 20),
    "body": (52, 54, 58),
    "knurl": (36, 37, 40),
    "hi": (96, 100, 106),
    "rim": (128, 132, 138),
    "switch": (70, 82, 60),
    "lens": (226, 222, 190),
    "lens_hot": (255, 253, 235),
    "cap": (44, 45, 48),
}


def flashlight(seed=7):
    rng = np.random.default_rng(seed)
    tail = np.array([2.0, 14.0])
    head = np.array([14.0, 2.0])
    axis = head - tail
    length = np.linalg.norm(axis)
    ax = axis / length
    perp = np.array([ax[1], -ax[0]])  # points to the lower-right side

    region = np.full((N, N), "", dtype=object)
    for y in range(N):
        for x in range(N):
            p = np.array([x + 0.5, y + 0.5]) - tail
            u = p @ ax
            v = p @ perp
            av = abs(v)
            r = ""
            if 0 <= u < 1.3 and av <= 1.8:
                r = "cap"
            elif 1.3 <= u < 9.5 and av <= 1.8:
                r = "knurl" if (x + y) % 2 == 0 and 2.5 < u < 8.0 else "body"
                if v < -0.6:
                    r = "hi"
            elif 4.5 <= u < 6.3 and -2.7 <= v < -1.8:
                r = "switch"
            elif 9.5 <= u < 11.0 and av <= 1.8 + (u - 9.5) * 0.9:
                r = "body" if v >= -0.7 else "hi"
            elif 11.0 <= u < 15.2 and av <= 3.2:
                r = "rim" if u >= 14.2 else ("body" if v >= -1.1 else "hi")
            elif 15.2 <= u < 16.4 and av <= 2.6:
                r = "lens_hot" if av < 1.2 else "lens"
            region[y, x] = r

    mask = region != ""
    img = np.zeros((N, N, 4), np.uint8)
    for y in range(N):
        for x in range(N):
            if not mask[y, x]:
                continue
            edge = any(
                not (0 <= y + dy < N and 0 <= x + dx < N and mask[y + dy, x + dx])
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            name = region[y, x]
            if edge and not name.startswith("lens"):
                name = "outline"
            c = np.array(COL[name], float)
            if name in ("body", "knurl", "cap"):
                c *= 1 + (rng.random() - 0.5) * 0.12  # slight wear
            img[y, x, :3] = np.clip(c, 0, 255)
            img[y, x, 3] = 255
    # A couple of worn edges where the paint is scratched off.
    for y, x in zip(*np.where(region == "body")):
        if rng.random() < 0.08:
            img[y, x, :3] = COL["hi"]
    return img


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/textures/item"
    out.mkdir(parents=True, exist_ok=True)
    img = Image.fromarray(flashlight(), "RGBA")
    img.save(out / "flashlight.png", optimize=True)
    prev = root / "docs/previews"
    prev.mkdir(parents=True, exist_ok=True)
    img.resize((256, 256), Image.NEAREST).save(prev / "flashlight_item.png")
    print("item flashlight.png")


if __name__ == "__main__":
    main()
