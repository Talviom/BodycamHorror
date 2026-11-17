"""Military player skins (64x64, classic arms), drawn from scratch in the dark project palette.

Usage: python -I tools/assets/make_skins.py <repo root>
Writes src/main/resources/assets/bodycamhorror/textures/entity/skin/{alpha,bravo,neutral}.png
and a flat preview docs/previews/skins_flat.png.

Look: faceless operators. Helmet (hat layer), balaclava and dark goggles, plate carrier with
pouches (jacket layer), gloves, knee pads, boots. Alpha: olive drab with a faded steel-blue
armband; Bravo: tan-grey with a dried-blood armband; Neutral (lobby / no team): charcoal.
Everything is muted and dirty, nothing bright (see the Obsidian note "Визуальный стиль").
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

S = 64
TEAMS = {
    #           uniform           vest/helmet       armband (team mark)
    "alpha":   ((84, 88, 62),    (56, 58, 44),     (74, 88, 104)),
    "bravo":   ((122, 112, 92),  (82, 76, 64),     (110, 32, 28)),
    "neutral": ((58, 58, 56),    (40, 40, 39),     (78, 74, 66)),
}
BLACK = (22, 21, 19)
GLOVE = (30, 29, 27)
BOOT = (34, 30, 26)
LENS = (14, 16, 18)


# Box layout of the classic 64x64 skin: (u, v, w, h, d) for base and overlay.
PARTS = {
    "head": ((0, 0, 8, 8, 8), (32, 0, 8, 8, 8)),
    "body": ((16, 16, 8, 12, 4), (16, 32, 8, 12, 4)),
    "rarm": ((40, 16, 4, 12, 4), (40, 32, 4, 12, 4)),
    "larm": ((32, 48, 4, 12, 4), (48, 48, 4, 12, 4)),
    "rleg": ((0, 16, 4, 12, 4), (0, 32, 4, 12, 4)),
    "lleg": ((16, 48, 4, 12, 4), (0, 48, 4, 12, 4)),
}


def faces(u, v, w, h, d):
    """Rectangles (x0, y0, x1, y1) of the six faces in Minecraft's box UV layout."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


class Skin:
    def __init__(self, seed):
        self.img = np.zeros((S, S, 4), np.uint8)
        self.rng = np.random.default_rng(seed)

    def fill(self, rect, color, grit=0.10, alpha=255):
        x0, y0, x1, y1 = rect
        h, w = y1 - y0, x1 - x0
        c = np.array(color, float)
        n = 1 + (self.rng.random((h, w, 1)) - 0.5) * 2 * grit
        self.img[y0:y1, x0:x1, :3] = np.clip(c * n, 0, 255)
        self.img[y0:y1, x0:x1, 3] = alpha

    def px(self, x, y, color, alpha=255):
        self.img[y, x, :3] = color
        self.img[y, x, 3] = alpha

    def rows(self, rect, r0, r1, color, grit=0.08):
        x0, y0, x1, y1 = rect
        self.fill((x0, y0 + r0, x1, y0 + r1), color, grit)

    def dirt(self, rect, amount=0.08):
        x0, y0, x1, y1 = rect
        for _ in range(int((x1 - x0) * (y1 - y0) * amount)):
            x = self.rng.integers(x0, x1)
            y = self.rng.integers(y0, y1)
            if self.img[y, x, 3]:
                self.img[y, x, :3] = (self.img[y, x, :3] * self.rng.uniform(0.6, 0.85)).astype(np.uint8)


def camo(sk, rect, base):
    """Subtle two-tone woodland blotches on the uniform, very low contrast."""
    x0, y0, x1, y1 = rect
    sk.fill(rect, base, 0.09)
    dark = tuple(int(c * 0.78) for c in base)
    for _ in range(max(1, (x1 - x0) * (y1 - y0) // 10)):
        cx, cy = sk.rng.integers(x0, x1), sk.rng.integers(y0, y1)
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            x, y = cx + dx, cy + dy
            if x0 <= x < x1 and y0 <= y < y1:
                sk.img[y, x, :3] = np.array(dark) * sk.rng.uniform(0.92, 1.05)


def make(team, seed):
    uni, gear, band = TEAMS[team]
    sk = Skin(seed)

    # ---- head (base): balaclava everywhere, goggles across the eyes
    hb = faces(*PARTS["head"][0])
    for f in hb.values():
        sk.fill(f, BLACK, 0.12)
    fx0, fy0, _, _ = hb["front"]
    sk.fill((fx0 + 1, fy0 + 3, fx0 + 7, fy0 + 5), LENS, 0.05)          # goggle lenses
    sk.px(fx0 + 2, fy0 + 3, (70, 74, 78)); sk.px(fx0 + 5, fy0 + 3, (70, 74, 78))  # faint glints
    sk.fill((fx0, fy0 + 3, fx0 + 1, fy0 + 5), gear, 0.06)               # strap
    sk.fill((fx0 + 7, fy0 + 3, fx0 + 8, fy0 + 5), gear, 0.06)
    for side in ("left", "right", "back"):
        x0, y0, x1, _ = hb[side]
        sk.fill((x0, y0 + 3, x1, y0 + 4), gear, 0.06)                   # goggle strap around

    # ---- head (overlay): helmet shell on top and upper sides, with NVG mount and team stripe
    ho = faces(*PARTS["head"][1])
    sk.fill(ho["top"], gear, 0.12)
    for side in ("front", "left", "right", "back"):
        x0, y0, x1, y1 = ho[side]
        sk.fill((x0, y0, x1, y0 + 3), gear, 0.12)
    x0, y0, _, _ = ho["front"]
    sk.fill((x0 + 3, y0 + 1, x0 + 5, y0 + 3), (46, 46, 44), 0.05)        # NVG mount
    for side in ("left", "right"):
        x0, y0, x1, _ = ho[side]
        sk.fill((x0 + 2, y0 + 1, x0 + 6, y0 + 2), band, 0.06)           # team stripe on helmet sides
    sk.dirt(ho["top"], 0.15)

    # ---- body: shirt, vest (overlay), pouches, belt
    bb = faces(*PARTS["body"][0])
    for f in bb.values():
        camo(sk, f, uni)
    bo = faces(*PARTS["body"][1])
    for side in ("front", "back", "left", "right"):
        x0, y0, x1, y1 = bo[side]
        sk.fill((x0, y0 + 1, x1, y0 + 8), gear, 0.10)                    # plate carrier
        sk.fill((x0, y0 + 9, x1, y0 + 10), (40, 36, 30), 0.08)          # belt
    x0, y0, _, _ = bo["front"]
    for i in range(3):                                                   # mag pouches
        sk.fill((x0 + 1 + i * 2, y0 + 5, x0 + 2 + i * 2, y0 + 8), tuple(int(c * 0.82) for c in gear), 0.08)
    sk.fill((x0 + 2, y0 + 2, x0 + 6, y0 + 3), (66, 64, 58), 0.05)        # name tape (blank)
    sk.px(x0 + 6, y0 + 2, (120, 40, 34))                                 # tiny bodycam REC light
    sk.fill((x0 + 5, y0 + 1, x0 + 7, y0 + 3), (28, 28, 28), 0.04)        # bodycam on the chest
    for f in bo.values():
        sk.dirt(f, 0.10)

    # ---- arms: sleeves, armband (team), gloves
    for arm in ("rarm", "larm"):
        ab = faces(*PARTS[arm][0])
        for name, f in ab.items():
            camo(sk, f, uni)
            if name not in ("top", "bottom"):
                sk.rows(f, 9, 12, GLOVE, 0.06)                           # gloves
                sk.rows(f, 2, 3, band, 0.05)                             # armband
        sk.fill(ab["bottom"], GLOVE, 0.06)

    # ---- legs: trousers, knee pads, boots
    for leg in ("rleg", "lleg"):
        lb = faces(*PARTS[leg][0])
        for name, f in lb.items():
            camo(sk, f, uni)
            if name not in ("top", "bottom"):
                sk.rows(f, 9, 12, BOOT, 0.08)
        sk.fill(lb["bottom"], BOOT, 0.06)
        x0, y0, x1, _ = lb["front"]
        sk.fill((x0, y0 + 4, x1, y0 + 6), (44, 42, 38), 0.06)            # knee pad
        for f in lb.values():
            sk.dirt(f, 0.06)
    return sk.img


def flat_preview(skins, out):
    """Front and back views side by side, nearest-neighbour upscaled, on a dark background."""
    scale = 8
    W = 16 * scale
    canvas = Image.new("RGBA", (len(skins) * (2 * W + 40) + 20, 32 * scale + 40), (20, 20, 20, 255))
    x = 20
    for img in skins:
        im = Image.fromarray(img, "RGBA")
        for view in ("front", "back"):
            v = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

            def put(part, face, dx, dy, layer=0):
                r = faces(*PARTS[part][layer])[face]
                v.alpha_composite(im.crop(r), (dx, dy))

            if view == "front":
                for layer in (0, 1):
                    put("head", "front", 4, 0, layer)
                    put("body", "front", 4, 8, layer)
                    put("rarm", "front", 0, 8, layer)
                    put("larm", "front", 12, 8, layer)
                    put("rleg", "front", 4, 20, layer)
                    put("lleg", "front", 8, 20, layer)
            else:
                for layer in (0, 1):
                    put("head", "back", 4, 0, layer)
                    put("body", "back", 4, 8, layer)
                    put("larm", "back", 0, 8, layer)
                    put("rarm", "back", 12, 8, layer)
                    put("lleg", "back", 4, 20, layer)
                    put("rleg", "back", 8, 20, layer)
            canvas.alpha_composite(v.resize((16 * scale, 32 * scale), Image.NEAREST), (x, 20))
            x += W + 10
        x += 20
    canvas.convert("RGB").save(out)


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/textures/entity/skin"
    out.mkdir(parents=True, exist_ok=True)
    imgs = []
    for i, team in enumerate(TEAMS):
        img = make(team, 700 + i)
        Image.fromarray(img, "RGBA").save(out / f"{team}.png", optimize=True)
        imgs.append(img)
        print("skin", team)
    prev = root / "docs/previews"
    prev.mkdir(parents=True, exist_ok=True)
    flat_preview(imgs, prev / "skins_flat.png")


if __name__ == "__main__":
    main()
