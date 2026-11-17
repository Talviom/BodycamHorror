"""Generates the VHS-style GUI assets: bitmap font, round banner plate, screen overlays,
fear heart, flashlight battery, slots and the inventory screen.

Usage: python -I tools/assets/make_gui.py <repo root>
Everything is drawn here from scratch, including the 5x7 pixel font, so no third-party fonts are used.
Previews: docs/previews/gui_*.png
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# ---------------------------------------------------------------- 5x7 font
# Each glyph is 7 rows of 5 chars ('#' = pixel). Lowercase letters reuse the uppercase shapes.
G = {
    "A": [" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"],
    "B": ["#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "],
    "C": [" ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### "],
    "D": ["#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "],
    "E": ["#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"],
    "F": ["#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "],
    "G": [" ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ####"],
    "H": ["#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"],
    "I": [" ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "],
    "J": ["  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  "],
    "K": ["#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"],
    "L": ["#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"],
    "M": ["#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #"],
    "N": ["#   #", "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #"],
    "O": [" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "],
    "P": ["#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "],
    "Q": [" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"],
    "R": ["#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"],
    "S": [" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "],
    "T": ["#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "],
    "U": ["#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "],
    "V": ["#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  "],
    "W": ["#   #", "#   #", "#   #", "# # #", "# # #", "# # #", " # # "],
    "X": ["#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"],
    "Y": ["#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "],
    "Z": ["#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"],
    "0": [" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "],
    "1": ["  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "],
    "2": [" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"],
    "3": ["#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "],
    "4": ["   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "],
    "5": ["#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "],
    "6": ["  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "],
    "7": ["#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "],
    "8": [" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "],
    "9": [" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "],
    ".": ["     ", "     ", "     ", "     ", "     ", " ##  ", " ##  "],
    ",": ["     ", "     ", "     ", "     ", " ##  ", "  #  ", " #   "],
    ":": ["     ", " ##  ", " ##  ", "     ", " ##  ", " ##  ", "     "],
    ";": ["     ", " ##  ", " ##  ", "     ", " ##  ", "  #  ", " #   "],
    "!": ["  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "     ", "  #  "],
    "?": [" ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  "],
    "'": ["  #  ", "  #  ", " #   ", "     ", "     ", "     ", "     "],
    '"': [" # # ", " # # ", "     ", "     ", "     ", "     ", "     "],
    "-": ["     ", "     ", "     ", "#####", "     ", "     ", "     "],
    "+": ["     ", "  #  ", "  #  ", "#####", "  #  ", "  #  ", "     "],
    "=": ["     ", "     ", "#####", "     ", "#####", "     ", "     "],
    "/": ["    #", "    #", "   # ", "  #  ", " #   ", "#    ", "#    "],
    "(": ["   # ", "  #  ", " #   ", " #   ", " #   ", "  #  ", "   # "],
    ")": [" #   ", "  #  ", "   # ", "   # ", "   # ", "  #  ", " #   "],
    "[": [" ### ", " #   ", " #   ", " #   ", " #   ", " #   ", " ### "],
    "]": [" ### ", "   # ", "   # ", "   # ", "   # ", "   # ", " ### "],
    "<": ["   # ", "  #  ", " #   ", "#    ", " #   ", "  #  ", "   # "],
    ">": [" #   ", "  #  ", "   # ", "    #", "   # ", "  #  ", " #   "],
    "#": [" # # ", " # # ", "#####", " # # ", "#####", " # # ", " # # "],
    "%": ["##   ", "##  #", "   # ", "  #  ", " #   ", "#  ##", "   ##"],
    "&": [" ##  ", "#  # ", "# #  ", " #   ", "# # #", "#  # ", " ## #"],
    "*": ["     ", "  #  ", "# # #", " ### ", "# # #", "  #  ", "     "],
    "_": ["     ", "     ", "     ", "     ", "     ", "     ", "#####"],
    "|": ["  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "],
    "@": [" ### ", "#   #", "# ###", "# # #", "# ###", "#    ", " ####"],
    "$": ["  #  ", " ####", "# #  ", " ### ", "  # #", "#### ", "  #  "],
    "^": ["  #  ", " # # ", "#   #", "     ", "     ", "     ", "     "],
    "~": ["     ", "     ", " #   ", "# # #", "   # ", "     ", "     "],
    "`": [" #   ", "  #  ", "     ", "     ", "     ", "     ", "     "],
    "\\": ["#    ", "#    ", " #   ", "  #  ", "   # ", "    #", "    #"],
    "{": ["   ##", "  #  ", "  #  ", " #   ", "  #  ", "  #  ", "   ##"],
    "}": ["##   ", "  #  ", "  #  ", "   # ", "  #  ", "  #  ", "##   "],
    # Extra camcorder symbols.
    "▶": ["#    ", "##   ", "###  ", "#### ", "###  ", "##   ", "#    "],  # play
    "●": ["     ", " ### ", "#####", "#####", "#####", " ### ", "     "],  # rec dot
    "■": ["     ", "#####", "#####", "#####", "#####", "#####", "     "],  # stop
    "♥": ["     ", " # # ", "#####", "#####", " ### ", "  #  ", "     "],  # heart
}
for ch in "abcdefghijklmnopqrstuvwxyz":
    G[ch] = G[ch.upper()]

CELL_W, CELL_H = 6, 9   # 5x7 glyph + 1 px spacing right, 1 px top padding, 1 px descender row
ROWS = [[chr(c) for c in range(r * 16 + 0x20, r * 16 + 0x30)] for r in range(6)]
ROWS.append(["▶", "●", "■", "♥"] + ["\u0000"] * 12)


def font_sheet():
    img = np.zeros((CELL_H * len(ROWS), CELL_W * 16, 4), np.uint8)
    for r, row in enumerate(ROWS):
        for c, ch in enumerate(row):
            g = G.get(ch)
            if not g:
                continue
            for y, line in enumerate(g):
                for x, px in enumerate(line):
                    if px == "#":
                        img[r * CELL_H + 1 + y, c * CELL_W + x] = (255, 255, 255, 255)
    # Space and unused cells must stay empty; Minecraft measures glyph width from the pixels.
    return img


def font_json():
    chars = []
    for row in ROWS:
        chars.append("".join(ch if ch != "\u007f" else "\u0000" for ch in row))
    return {"providers": [
        {"type": "space", "advances": {" ": 4}},
        {"type": "bitmap", "file": "bodycamhorror:font/vhs.png", "height": 9, "ascent": 7, "chars": chars},
    ]}


def text_pixels(text, color=(235, 235, 225), scale=1):
    """Render a string with the font itself (for baked texture labels and previews)."""
    w = sum((5 if ch != " " else 3) + 1 for ch in text) * scale
    img = np.zeros((9 * scale, max(1, w), 4), np.uint8)
    x = 0
    for ch in text:
        if ch == " ":
            x += 4 * scale
            continue
        g = G.get(ch, G["?"])
        for gy, line in enumerate(g):
            for gx, px in enumerate(line):
                if px == "#":
                    img[(1 + gy) * scale:(2 + gy) * scale, x + gx * scale:x + (gx + 1) * scale] = (*color, 255)
        x += 6 * scale
    return img


def paste(dst, src, x, y):
    h, w = src.shape[:2]
    h = min(h, dst.shape[0] - y)
    w = min(w, dst.shape[1] - x)
    a = src[:h, :w, 3:4] / 255.0
    dst[y:y + h, x:x + w, :3] = dst[y:y + h, x:x + w, :3] * (1 - a) + src[:h, :w, :3] * a
    dst[y:y + h, x:x + w, 3] = np.maximum(dst[y:y + h, x:x + w, 3], src[:h, :w, 3])


# ---------------------------------------------------------------- VHS helpers
rng = np.random.default_rng(1987)


def scanline_mask(h, w, strength=0.25):
    m = np.ones((h, w))
    m[1::2] = 1 - strength
    return m


def chroma_bleed(img, shift=1):
    """Red to the left, cyan to the right, like a worn tape."""
    out = img.copy().astype(float)
    out[:, :-shift, 0] = np.maximum(out[:, :-shift, 0], img[:, shift:, 0] * 0.8)
    out[:, shift:, 2] = np.maximum(out[:, shift:, 2], img[:, :-shift, 2] * 0.8)
    a = img[..., 3].astype(float)
    a_shift = np.maximum(np.pad(a, ((0, 0), (shift, 0)))[:, :-shift], np.pad(a, ((0, 0), (0, shift)))[:, shift:])
    out[..., 3] = np.maximum(a, a_shift * 0.6)
    return np.clip(out, 0, 255).astype(np.uint8)


def banner_plate():
    w, h = 256, 64
    img = np.zeros((h, w, 4), float)
    x = np.linspace(-1, 1, w)[None, :]
    y = np.linspace(-1, 1, h)[:, None]
    fade = np.clip(1.15 - np.abs(x) ** 3, 0, 1) * np.clip(1.25 - np.abs(y) ** 6, 0, 1)
    img[..., :3] = 10
    img[..., 3] = 200 * fade
    # Grain and scanlines.
    img[..., :3] += rng.normal(0, 7, (h, w))[..., None]
    img[..., 3] *= scanline_mask(h, w, 0.18)
    # Thin rules top and bottom.
    for yy in (6, h - 7):
        img[yy, :, :3] = 200
        img[yy, :, 3] = 150 * fade[yy]
    # Tracking glitch: one band shifted and brightened.
    band = slice(44, 47)
    img[band] = np.roll(img[band], 5, axis=1)
    img[band, :, :3] += 35
    # Small labels baked in: PLAY top-left, SP bottom-right.
    plate = np.clip(img, 0, 255).astype(np.uint8)
    paste(plate, text_pixels("▶ PLAY", (230, 230, 220)), 22, 9)
    paste(plate, text_pixels("SP", (180, 180, 170)), 220, 46)
    paste(plate, text_pixels("●", (220, 40, 30)), 222, 9)
    return chroma_bleed(plate, 1)


def scanlines():
    img = np.zeros((4, 256, 4), np.uint8)
    img[1::2, :, 3] = 60
    return img


def noise_tex():
    n = rng.random((128, 128))
    img = np.zeros((128, 128, 4), np.uint8)
    img[..., :3] = (n * 255).astype(np.uint8)[..., None]
    img[..., 3] = (np.clip(n - 0.55, 0, 1) / 0.45 * 70).astype(np.uint8)
    return img


def heart_frames():
    frames = []
    for i, (scale, glow) in enumerate([(0.76, 0.0), (0.84, 0.0), (0.93, 0.25), (0.97, 0.6)]):
        f = np.zeros((32, 32, 4), float)
        yy, xx = np.mgrid[:32, :32]
        u = (xx - 15.5) / (13 * scale)
        v = -(yy - 14.5) / (13 * scale)
        # Classic implicit heart curve.
        val = (u ** 2 + v ** 2 - 1) ** 3 - u ** 2 * v ** 3
        inside = val <= 0
        # Shading: darker toward the bottom-right, highlight top-left.
        shade = np.clip(0.55 + 0.35 * (-u - v) / 2, 0.25, 1.0)
        f[inside, 0] = 150 + 90 * shade[inside]
        f[inside, 1] = 18 + 20 * shade[inside]
        f[inside, 2] = 20 + 18 * shade[inside]
        f[inside, 3] = 255
        # Outline.
        edge = inside & ~(np.roll(inside, 1, 0) & np.roll(inside, -1, 0) & np.roll(inside, 1, 1) & np.roll(inside, -1, 1))
        f[edge, :3] = (40, 6, 8)
        # Specular pixel cluster.
        hl = inside & ((u + 0.45) ** 2 + (v - 0.35) ** 2 < 0.04)
        f[hl, :3] = (255, 150, 140)
        # Grit.
        g = rng.random((32, 32)) < 0.08
        f[inside & g, :3] *= 0.75
        if glow:
            d = np.sqrt(((xx - 15.5) / 15) ** 2 + ((yy - 15.5) / 15) ** 2)
            ga = np.clip(1 - d, 0, 1) * glow * 160
            out = ~inside
            f[out, 0] = 200
            f[out, 1] = 20
            f[out, 2] = 20
            f[out, 3] = ga[out]
        frames.append(np.clip(f, 0, 255).astype(np.uint8))
    return np.concatenate(frames, axis=1)


def battery_frames():
    frames = []
    for level in (0, 1, 2, 3, 4):  # empty, 25, 50, 75, 100 %
        f = np.zeros((16, 16, 4), np.uint8)
        col = (230, 230, 220, 255) if level > 1 else (230, 60, 45, 255)
        # Body outline x1..14, y4..11, terminal at x15.
        f[4, 1:15] = col
        f[11, 1:15] = col
        f[4:12, 1] = col
        f[4:12, 14] = col
        f[6:10, 15] = col
        for i in range(level):
            x0 = 3 + i * 3
            f[6:10, x0:x0 + 2] = col
        frames.append(f)
    return np.concatenate(frames, axis=1)


def slot(selected):
    s = 24
    f = np.zeros((s, s, 4), np.uint8)
    f[2:22, 2:22] = (12, 12, 14, 150)
    edge = (230, 230, 220, 230) if selected else (160, 160, 150, 160)
    # Viewfinder corners instead of a full box.
    L = 6
    for (y, x, dy, dx) in ((2, 2, 1, 1), (2, 21, 1, -1), (21, 2, -1, 1), (21, 21, -1, -1)):
        for i in range(L):
            f[y, x + dx * i] = edge
            f[y + dy * i, x] = edge
    if selected:
        f[1, 2:22] = f[22, 2:22] = (230, 230, 220, 90)
        f[2:22, 1] = f[2:22, 22] = (230, 230, 220, 90)
    return chroma_bleed(f, 1)


def inventory():
    W, H = 256, 256
    PW, PH = 252, 150
    img = np.zeros((H, W, 4), float)
    img[:PH, :PW, :3] = 14
    img[:PH, :PW, 3] = 225
    img[:PH, :PW, :3] += rng.normal(0, 6, (PH, PW))[..., None]
    img[:PH, :PW, 3] *= scanline_mask(PH, PW, 0.12)
    # Border.
    for y0, y1, x0, x1 in ((0, 1, 0, PW), (PH - 1, PH, 0, PW), (0, PH, 0, 1), (0, PH, PW - 1, PW)):
        img[y0:y1, x0:x1, :3] = 170
        img[y0:y1, x0:x1, 3] = 230
    inv = np.clip(img, 0, 255).astype(np.uint8)
    # REC, slot numbers and the hint line are drawn by the screen code, so they are not baked in here.
    paste(inv, text_pixels("INVENTORY"), 8, 4)
    paste(inv, text_pixels("CAM-01  SP", (150, 150, 140)), 8, PH - 12)
    # Player model box.
    box = (8, 16, 60, 120)
    inv[box[1], box[0]:box[2]] = inv[box[3], box[0]:box[2]] = (120, 120, 112, 220)
    inv[box[1]:box[3], box[0]] = inv[box[1]:box[3], box[2]] = (120, 120, 112, 220)
    # Slot grid: 10 x 2, 18 px pitch, origin (70, 40); ends at x=250, so the panel is 252 wide.
    for r in range(2):
        for c in range(10):
            x, y = 70 + c * 18, 40 + r * 18
            inv[y:y + 18, x:x + 18] = (8, 8, 10, 235)
            inv[y, x:x + 18] = inv[y + 17, x:x + 18] = (110, 110, 104, 255)
            inv[y:y + 18, x] = inv[y:y + 18, x + 17] = (110, 110, 104, 255)
    return chroma_bleed(inv, 1)


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(path, optimize=True)


# ---------------------------------------------------------------- horror restyle (v0.5.1)
# Found-footage look: no neon, no colour fringes; dirty, dark, worn. All captions are drawn
# by the code, so the textures carry no text at all.

def horror_font_sheet():
    """The same glyph shapes, printed by a tired typewriter: uneven ink (alpha), eroded
    pixels and a baseline that jumps by one pixel now and then."""
    r = np.random.default_rng(1954)
    img = np.zeros((CELL_H * len(ROWS), CELL_W * 16, 4), np.uint8)
    for row_i, row in enumerate(ROWS):
        for c, ch in enumerate(row):
            g = G.get(ch)
            if not g:
                continue
            dy = 1 if r.random() < 0.22 else 0          # misaligned key
            ink = r.uniform(0.72, 1.0)                  # this key's ink strength
            for y, line in enumerate(g):
                for x, px in enumerate(line):
                    if px != "#":
                        continue
                    if r.random() < 0.10:               # dropped ink
                        a = int(255 * ink * 0.30)
                    else:
                        a = int(255 * ink * r.uniform(0.80, 1.0))
                    yy = row_i * CELL_H + min(CELL_H - 1, y + dy)
                    img[yy, c * CELL_W + x] = (255, 255, 255, max(a, 40))
    return img


def horror_font_json():
    data = font_json()
    data["providers"][1]["file"] = "bodycamhorror:font/horror.png"
    return data


def vignette_tex():
    s = 256
    yy, xx = np.mgrid[:s, :s]
    d = np.sqrt(((xx - s / 2 + 0.5) / (s / 2)) ** 2 + ((yy - s / 2 + 0.5) / (s / 2)) ** 2) / np.sqrt(2)
    a = np.clip((d - 0.28) / 0.72, 0, 1) ** 1.6
    # Uneven edge: blotchy darkness, like an old lens.
    blot = np.asarray(Image.fromarray((rng.random((9, 9)) * 255).astype(np.uint8)).resize((s, s), Image.BICUBIC)) / 255
    a = np.clip(a * (0.85 + 0.3 * blot), 0, 1)
    img = np.zeros((s, s, 4), np.uint8)
    img[..., 3] = (a * 255).astype(np.uint8)
    return img


def horror_noise_tex():
    """Monochrome grain plus sparse dust and hair, tileable (the noise has no structure that seams)."""
    s = 128
    img = np.zeros((s, s, 4), np.uint8)
    g = rng.random((s, s))
    dark = g < 0.5
    img[..., :3] = np.where(dark, 0, 200)[..., None]
    img[..., 3] = (np.abs(g - 0.5) * 2 * 26).astype(np.uint8)  # faint grain
    for _ in range(40):                                     # dust specks
        y, x = rng.integers(0, s, 2)
        img[y, x] = (8, 8, 8, 170) if rng.random() < 0.75 else (190, 184, 170, 110)
    for _ in range(3):                                      # hairs
        x = int(rng.integers(0, s))
        y0 = int(rng.integers(0, s))
        for k in range(int(rng.integers(10, 30))):
            x = (x + int(rng.integers(-1, 2))) % s
            img[(y0 + k) % s, x] = (6, 6, 6, 150)
    return img


def horror_slot(selected):
    s = 24
    f = np.zeros((s, s, 4), float)
    f[3:21, 3:21, :3] = (10, 9, 8)
    f[3:21, 3:21, 3] = 225
    r = np.random.default_rng(77 if selected else 76)
    edge = (150, 142, 124) if selected else (70, 66, 58)
    for i in range(3, 21):
        for (y, x) in ((3, i), (20, i), (i, 3), (i, 20)):
            if r.random() > 0.3:                             # worn outline
                f[y, x, :3] = edge
                f[y, x, 3] = 230
    f[4, 4:20, :3] = (3, 3, 2)                              # inner shadow: recessed
    f[4:20, 4, :3] = (3, 3, 2)
    if selected:                                            # dim bone glow, no neon
        f[2, 3:21, :3] = f[21, 3:21, :3] = (120, 112, 98)
        f[2, 3:21, 3] = f[21, 3:21, 3] = 70
        f[3:21, 2, :3] = f[3:21, 21, :3] = (120, 112, 98)
        f[3:21, 2, 3] = f[3:21, 21, 3] = 70
    return np.clip(f, 0, 255).astype(np.uint8)


def horror_inventory():
    W, H, PW, PH = 256, 256, 252, 150
    img = np.zeros((H, W, 4), float)
    yy, xx = np.mgrid[:PH, :PW]
    edge = np.minimum.reduce([xx, yy, PW - 1 - xx, PH - 1 - yy]).astype(float)
    inside = edge + rng.random((PH, PW)) * 2.5 > 1.5          # ragged border
    stain = np.asarray(Image.fromarray((rng.random((8, 13)) * 255).astype(np.uint8)).resize((PW, PH), Image.BICUBIC)) / 255
    col = np.array([24, 22, 18], float)[None, None, :] * (0.75 + 0.5 * stain)[..., None]
    col += rng.normal(0, 4, (PH, PW, 1))
    rim = np.clip(1 - edge / 14, 0, 1)                      # soaked, darker rim
    col *= (1 - 0.6 * rim)[..., None]
    img[:PH, :PW, :3] = col
    img[:PH, :PW, 3] = np.where(inside, 238, 0)
    # Water stain rings.
    for _ in range(3):
        cy, cx, rad = rng.integers(10, PH - 10), rng.integers(10, PW - 10), rng.integers(12, 30)
        d = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
        ring = (np.abs(d - rad) < 1.0) & inside
        img[:PH, :PW][ring, :3] *= 0.6
    inv = np.clip(img, 0, 255).astype(np.uint8)
    # "Photo" area x8..60 y16..120: faded paper with a height chart (the model is drawn on top).
    px0, py0, px1, py1 = 8, 16, 60, 120
    inv[py0:py1, px0:px1, :3] = (46, 43, 37)
    inv[py0:py1, px0:px1, 3] = 245
    for k in range(py0 + 4, py1, 6):
        inv[k, px0:px1, :3] = (62, 58, 50) if (k - py0) % 24 else (78, 73, 63)
    inv[py0, px0:px1, :3] = inv[py1 - 1, px0:px1, :3] = (88, 82, 70)
    inv[py0:py1, px0, :3] = inv[py0:py1, px1 - 1, :3] = (88, 82, 70)
    sp = rng.random((py1 - py0, px1 - px0)) < 0.05           # photo dust
    inv[py0:py1, px0:px1][sp, :3] = (20, 18, 15)
    # Recessed slots: 10 x 2 from (70, 40), pitch 18.
    for rr in range(2):
        for c in range(10):
            x, y = 70 + c * 18, 40 + rr * 18
            inv[y:y + 18, x:x + 18] = (9, 8, 7, 240)
            worn = rng.random(72) > 0.3
            k = 0
            for i in range(18):
                for (yy2, xx2) in ((y, x + i), (y + 17, x + i), (y + i, x), (y + i, x + 17)):
                    if worn[k % 72]:
                        inv[yy2, xx2] = (62, 58, 51, 255)
                    k += 1
            inv[y + 1, x + 1:x + 17, :3] = (3, 3, 2)
            inv[y + 1:y + 17, x + 1, :3] = (3, 3, 2)
    return inv


def main():
    root = Path(sys.argv[1]).resolve()
    a = root / "src/main/resources/assets/bodycamhorror"
    (a / "font").mkdir(parents=True, exist_ok=True)
    # Small OSD text keeps the clean 5x7 font; titles use the worn typewriter variant.
    save(font_sheet(), a / "textures/font/vhs.png")
    (a / "font/vhs.json").write_text(json.dumps(font_json(), indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    save(horror_font_sheet(), a / "textures/font/horror.png")
    (a / "font/horror.json").write_text(json.dumps(horror_font_json(), indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    gui = a / "textures/gui"
    plate = gui / "banner_plate.png"
    if plate.exists():
        plate.unlink()                       # the banner has no plate any more
    save(scanlines(), gui / "scanlines.png")
    save(vignette_tex(), gui / "vignette.png")
    save(horror_noise_tex(), gui / "noise.png")
    save(heart_frames(), gui / "heart.png")
    save(battery_frames(), gui / "battery.png")
    save(horror_slot(False), gui / "slot.png")
    save(horror_slot(True), gui / "slot_selected.png")
    save(horror_inventory(), gui / "inventory.png")
    print("gui assets written")


if __name__ == "__main__":
    main()
