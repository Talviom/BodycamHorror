"""BodycamHorror 0.9.x HUD: blood vial (health) and horizontal flashlight battery with a torch icon.

Writes only these three textures (the rest of the GUI stays in make_gui.py):
  textures/gui/vial.png         60x88: 3 layers of 20x88 - glass back, blood (cropped by health), glass front
  textures/gui/battery_cell.png 120x18: 3 layers of 40x18 - shell back, grey charge (tinted in code), shell front
  textures/gui/torch.png        72x18: 3 frames of 24x18 - off, on (beam), dead (slashed)
All art is drawn at 2x and shown at half size in GUI pixels (vial 10x44, battery 20x9, torch 12x9).
Palette: bone C4BCAA, ash 787266, soot 4E4A42, dried blood 7A1612. Own work, no third-party material.

Usage: python tools/assets/make_hud.py <repo root>
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

INK = (14, 12, 10)
BONE = (196, 188, 170)
BONE_D = (150, 143, 128)
ASH = (120, 114, 102)
SOOT = (78, 74, 66)
SOOT_D = (40, 37, 33)
BLOOD = (122, 22, 18)
BLOOD_D = (70, 12, 10)
BLOOD_DD = (38, 7, 6)
RUST = (96, 62, 38)
RUST_D = (64, 40, 26)

VW, VH = 20, 88
BW, BH = 40, 18
TW, TH = 24, 18


def layer(w, h):
    return np.zeros((h, w, 4), np.uint8)


def put(img, x, y, c, a=255):
    h, w = img.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        img[y, x, :3] = c
        img[y, x, 3] = a


def blend(img, x, y, c, a):
    """Draw c with alpha a over what is already there (keeps the pixel opaque if it was)."""
    h, w = img.shape[:2]
    if not (0 <= x < w and 0 <= y < h):
        return
    old_a = img[y, x, 3] / 255.0
    k = a / 255.0
    if old_a == 0:
        put(img, x, y, c, a)
        return
    img[y, x, :3] = (np.array(c) * k + img[y, x, :3] * (1 - k)).astype(np.uint8)
    img[y, x, 3] = int(255 * (k + old_a * (1 - k)))


# ---------------------------------------------------------------- vial
def vial_masks():
    yy, xx = np.mgrid[0:VH, 0:VW]
    cx, cy = 10.0, 78.0

    def tube(x0, x1, r):
        straight = (yy >= 6) & (yy <= cy) & (xx >= x0) & (xx <= x1)
        round_ = (yy > cy) & ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r)
        return straight | round_

    outer = tube(2, 17, 8.2)
    inner = tube(4, 15, 6.2) & (yy >= 7)
    lip = (yy >= 1) & (yy <= 5) & (xx >= 0) & (xx <= 19)
    return outer, inner, lip


def vial_back(r):
    f = layer(VW, VH)
    outer, inner, lip = vial_masks()
    wall = outer & ~inner
    for y in range(VH):
        for x in range(VW):
            if inner[y, x]:
                put(f, x, y, (20, 18, 16), 175)         # empty glass, almost black
            elif wall[y, x]:
                put(f, x, y, SOOT, 230)
    # one-pixel ink outline around the tube and the lip
    shape = outer | lip
    for y in range(VH):
        for x in range(VW):
            if not shape[y, x]:
                continue
            edge = any(not (0 <= x + dx < VW and 0 <= y + dy < VH) or not shape[y + dy, x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                put(f, x, y, INK)
            elif lip[y, x]:
                put(f, x, y, BONE_D if y == 2 else ASH)
    # dried grime inside the bottom of the glass
    for _ in range(14):
        x, y = int(r.integers(4, 16)), int(r.integers(70, 86))
        if inner[y, x]:
            blend(f, x, y, SOOT_D, 120)
    return f


def vial_blood(r):
    f = layer(VW, VH)
    _, inner, _ = vial_masks()
    noise = r.random((VH, VW))
    for y in range(VH):
        for x in range(VW):
            if not inner[y, x]:
                continue
            edge = min(x - 4, 15 - x) / 5.5          # darker at the walls
            k = 0.55 + 0.45 * max(0.0, min(1.0, edge))
            deep = max(0.0, (y - 60) / 28.0)        # sediment towards the bottom
            k *= 1.0 - 0.45 * deep
            k *= 0.88 + 0.12 * noise[y, x]
            c = tuple(int(BLOOD_DD[i] + (BLOOD[i] - BLOOD_DD[i]) * k) for i in range(3))
            put(f, x, y, c)
    # clots and a few paler flecks
    for _ in range(9):
        cx, cy = int(r.integers(5, 14)), int(r.integers(12, 84))
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if inner[min(cy + dy, VH - 1), cx + dx]:
                old = f[cy + dy, cx + dx, :3].astype(int)
                put(f, cx + dx, cy + dy, tuple(int(v * 0.6) for v in old))
    for _ in range(6):
        x, y = int(r.integers(6, 14)), int(r.integers(10, 70))
        if inner[y, x]:
            put(f, x, y, (150, 52, 42))
    return f


def crack(f, r, x, y, steps, mask):
    for _ in range(steps):
        if 0 <= x < VW and 0 <= y < VH and mask[y, x]:
            blend(f, x, y, BONE, 120)
        x += int(r.choice([-1, 0, 1, 1]))
        y += 1 if r.random() < 0.7 else 0


def vial_front(r):
    f = layer(VW, VH)
    outer, inner, _ = vial_masks()
    for y in range(9, 76):                             # long highlight down the left of the glass
        blend(f, 5, y, BONE, 80)
        blend(f, 6, y, BONE, 25)
    for y in range(14, 34):
        blend(f, 14, y, BONE, 55)
    for y in (22, 34, 46, 58, 70):                     # graduations: it reads as a lab tube
        for x in range(11, 15):
            blend(f, x, y, ASH, 150)
        blend(f, 15, y, ASH, 150)
    for y in (28, 40, 52, 64):
        for x in range(13, 15):
            blend(f, x, y, ASH, 110)
    crack(f, r, 7, 16, 14, outer)
    crack(f, r, 9, 72, 6, outer)
    for _ in range(18):                                # dust on the glass
        x, y = int(r.integers(2, 18)), int(r.integers(6, 86))
        if outer[y, x]:
            blend(f, x, y, ASH, 70)
    return f


def vial_sheet():
    r = np.random.default_rng(91)
    return np.concatenate([vial_back(r), vial_blood(r), vial_front(r)], axis=1)


# ---------------------------------------------------------------- battery (horizontal cell)
def battery_back(r):
    f = layer(BW, BH)
    for y in range(2, 16):
        for x in range(1, 35):
            corner = (x in (1, 34)) and (y in (2, 15))
            if corner:
                continue
            border = y in (2, 15) or x in (1, 34)
            if border:
                put(f, x, y, INK)
            elif y in (3, 14) or x in (2, 3, 33):
                put(f, x, y, SOOT)                     # metal shell
            else:
                put(f, x, y, (18, 16, 14), 220)        # dark window
    for y in range(6, 12):                             # + terminal
        for x in range(35, 39):
            put(f, x, y, INK if y in (6, 11) or x == 38 else ASH)
    put(f, 36, 7, BONE_D)
    for x in (2, 3):                                   # - end, worn darker
        for y in range(4, 14):
            put(f, x, y, SOOT_D)
    return f


def battery_fill(r):
    f = layer(BW, BH)
    for y in range(4, 14):
        v = 235 - (y - 4) * 9                          # lighter at the top of the cell
        for x in range(4, 33):
            g = int(v * (0.86 + 0.14 * r.random()))
            put(f, x, y, (g, g, g))
    for x in range(8, 33, 7):                          # faint seams between "cells" of charge
        for y in range(4, 14):
            put(f, x, y, (int(f[y, x, 0] * 0.6),) * 3)
    return f


def battery_front(r):
    f = layer(BW, BH)
    for x in range(5, 32):                             # glare along the top
        blend(f, x, 5, BONE, 60)
    for _ in range(16):                                # rust on the shell
        x = int(r.choice([int(r.integers(2, 8)), int(r.integers(26, 34))]))
        y = int(r.choice([3, 14, int(r.integers(3, 15))]))
        blend(f, x, y, RUST if r.random() < 0.6 else RUST_D, 220)
    for y in range(3, 15):                             # rust crust at the + end
        if r.random() < 0.5:
            blend(f, 33, y, RUST, 200)
    x, y = 12, 4                                        # hairline crack across the window
    for _ in range(9):
        blend(f, x, y, BONE, 110)
        x += 1
        y += 1 if r.random() < 0.6 else 0
    blend(f, 24, 11, BONE, 90)
    blend(f, 25, 12, BONE, 90)
    blend(f, 26, 12, BONE, 70)
    return f


def battery_sheet():
    r = np.random.default_rng(37)
    return np.concatenate([battery_back(r), battery_fill(r), battery_front(r)], axis=1)


# ---------------------------------------------------------------- torch icon
def torch_frame(mode):
    f = layer(TW, TH)
    body = [(x, y) for x in range(1, 11) for y in range(6, 12)]
    head = [(x, y) for x in range(11, 17) for y in range(3, 15) if not (x == 11 and y in (3, 14))]
    shape = set(body) | set(head)
    for (x, y) in shape:
        edge = any((x + dx, y + dy) not in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        put(f, x, y, INK if edge else ASH)
    for x in range(2, 10):
        put(f, x, 7, BONE_D)                           # highlight on the barrel
    for x in range(12, 16):
        put(f, x, 4, BONE_D)
    put(f, 5, 8, SOOT_D)                               # switch
    put(f, 6, 8, SOOT_D)
    for y in range(4, 14):                             # lens
        put(f, 16, y, BONE if mode == 1 else SOOT)
    if mode == 1:                                      # beam
        for i in range(5):
            blend(f, 18 + i, 8, BONE, 170 - i * 25)
            blend(f, 18 + i, 9, BONE, 170 - i * 25)
            blend(f, 18 + i, 5 - i // 2, BONE, 130 - i * 22)
            blend(f, 18 + i, 12 + i // 2, BONE, 130 - i * 22)
    if mode == 2:                                      # dead: slashed
        for i in range(16):
            x, y = 2 + i, 16 - i
            put(f, x, y, ASH)
            put(f, x + 1, y, INK)
    return f


def torch_sheet():
    return np.concatenate([torch_frame(m) for m in range(3)], axis=1)


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(path)
    print("wrote", path)


def main():
    root = Path(sys.argv[1]).resolve()
    gui = root / "src/main/resources/assets/bodycamhorror/textures/gui"
    save(vial_sheet(), gui / "vial.png")
    save(battery_sheet(), gui / "battery_cell.png")
    save(torch_sheet(), gui / "torch.png")


if __name__ == "__main__":
    main()
