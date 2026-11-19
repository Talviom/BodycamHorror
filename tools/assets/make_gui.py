"""Generates the VHS-style GUI assets: bitmap font, round banner plate, screen overlays,
fear heart (anatomical), flashlight battery, slots and the inventory screen.

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
# Cyrillic (Russian translation, 0.8.0). Same 5x7 grid. A glyph may have 8 rows: the 8th goes into
# the descender row (Д, Ц, Щ). TOP holds a mark drawn in the top padding row (Ё, Й).
G.update({
    "А": G["A"], "В": G["B"], "Е": G["E"], "К": G["K"], "М": G["M"], "Н": G["H"], "О": G["O"],
    "Р": G["P"], "С": G["C"], "Т": G["T"], "Х": G["X"], "Ё": G["E"],
    "И": ["#   #", "#   #", "#  ##", "# # #", "##  #", "#   #", "#   #"],
    "Б": ["#####", "#    ", "#    ", "#### ", "#   #", "#   #", "#### "],
    "Г": ["#####", "#    ", "#    ", "#    ", "#    ", "#    ", "#    "],
    "Д": [" ### ", " # # ", " # # ", " # # ", " # # ", " # # ", "#####", "#   #"],
    "Ж": ["# # #", "# # #", " ### ", "  #  ", " ### ", "# # #", "# # #"],
    "З": [" ### ", "#   #", "    #", "  ## ", "    #", "#   #", " ### "],
    "Й": ["#   #", "#   #", "#  ##", "# # #", "##  #", "#   #", "#   #"],
    "Л": ["  ###", " #  #", " #  #", " #  #", " #  #", " #  #", "#   #"],
    "П": ["#####", "#   #", "#   #", "#   #", "#   #", "#   #", "#   #"],
    "У": ["#   #", "#   #", "#   #", " ####", "    #", "#   #", " ### "],
    "Ф": ["  #  ", " ### ", "# # #", "# # #", "# # #", " ### ", "  #  "],
    "Ц": ["#  # ", "#  # ", "#  # ", "#  # ", "#  # ", "#  # ", "#####", "    #"],
    "Ч": ["#   #", "#   #", "#   #", " ####", "    #", "    #", "    #"],
    "Ш": ["# # #", "# # #", "# # #", "# # #", "# # #", "# # #", "#####"],
    "Щ": ["# # #", "# # #", "# # #", "# # #", "# # #", "# # #", "#####", "    #"],
    "Ъ": ["##   ", " #   ", " #   ", " ### ", " #  #", " #  #", " ### "],
    "Ы": ["#   #", "#   #", "#   #", "##  #", "# # #", "# # #", "##  #"],
    "Ь": ["#    ", "#    ", "#    ", "#### ", "#   #", "#   #", "#### "],
    "Э": [" ### ", "#   #", "    #", " ####", "    #", "#   #", " ### "],
    "Ю": ["#  # ", "# # #", "# # #", "### #", "# # #", "# # #", "#  # "],
    "Я": [" ####", "#   #", "#   #", " ####", "  # #", " #  #", "#   #"],
    "«": ["     ", "  # #", " # # ", "# #  ", " # # ", "  # #", "     "],
    "»": ["     ", "# #  ", " # # ", "  # #", " # # ", "# #  ", "     "],
    "—": ["     ", "     ", "     ", "#####", "     ", "     ", "     "],
    "·": ["     ", "     ", "     ", "  #  ", "     ", "     ", "     "],
    "□": ["     ", "#####", "#   #", "#   #", "#   #", "#####", "     "],
    "№": ["#  # ", "## # ", "# ## ", "#  # ", "#  ##", "#  # ", "#  ##"],
})
TOP = {"Ё": " # # ", "Й": " ### "}
CYR_UP = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
for ch in "abcdefghijklmnopqrstuvwxyz":
    G[ch] = G[ch.upper()]
for ch in CYR_UP + "Ё":
    G[ch.lower()] = G[ch]
    if ch in TOP:
        TOP[ch.lower()] = TOP[ch]

CELL_W, CELL_H = 6, 9   # 5x7 glyph + 1 px spacing right, 1 px top padding, 1 px descender row
ROWS = [[chr(c) for c in range(r * 16 + 0x20, r * 16 + 0x30)] for r in range(6)]
# New symbols go after the old ones, so the worn typewriter keeps the same random ink on old glyphs.
ROWS.append(["▶", "●", "■", "♥", "«", "»", "—", "·", "□", "№", "Ё", "ё"] + ["\u0000"] * 4)
ROWS.append(list(CYR_UP[:16]))
ROWS.append(list(CYR_UP[16:]))
ROWS.append(list(CYR_UP[:16].lower()))
ROWS.append(list(CYR_UP[16:].lower()))


def font_sheet():
    img = np.zeros((CELL_H * len(ROWS), CELL_W * 16, 4), np.uint8)
    for r, row in enumerate(ROWS):
        for c, ch in enumerate(row):
            g = G.get(ch)
            if not g:
                continue
            for y, line in enumerate(([TOP[ch]] if ch in TOP else []) + g):
                yo = 0 if ch in TOP else 1
                for x, px in enumerate(line):
                    if px == "#":
                        img[r * CELL_H + yo + y, c * CELL_W + x] = (255, 255, 255, 255)
    # Space and unused cells must stay empty; Minecraft measures glyph width from the pixels.
    return img


def font_json():
    chars = []
    for row in ROWS:
        chars.append("".join(ch if ch != "\u007f" else "\u0000" for ch in row))
    return {"providers": [
        {"type": "space", "advances": {" ": 4}},
        {"type": "bitmap", "file": "bodycamhorror:font/vhs.png", "height": 9, "ascent": 7, "chars": chars},
        # Anything the sheet lacks falls back to the vanilla font instead of empty boxes.
        {"type": "reference", "id": "minecraft:default"},
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


# ---------------------------------------------------------------- fear heart (v0.5.5)
# An anatomical heart, not a valentine: dried-blood muscle, grey severed vessels with black
# lumens, a bruised vena cava, rot spots and a drip from the cut pulmonary trunk.
# 4 frames of 48x48: rest, small beat, big beat, panic (vessels swell, dark halo).
# Drawn with a tiny z-buffer of ellipsoids and tubes, lit, then cut down to fixed palettes.
HS = 48  # heart frame size


def _bez(p, n=64):
    """Quadratic/cubic Bezier through control points p -> (n, 2) samples."""
    p = np.asarray(p, float)
    t = np.linspace(0, 1, n)[:, None]
    if len(p) == 3:
        return (1 - t) ** 2 * p[0] + 2 * (1 - t) * t * p[1] + t ** 2 * p[2]
    return ((1 - t) ** 3 * p[0] + 3 * (1 - t) ** 2 * t * p[1]
            + 3 * (1 - t) * t ** 2 * p[2] + t ** 3 * p[3])


class _Scene:
    """Tiny z-buffer: each primitive writes depth, normal, material and part id."""

    def __init__(self):
        py, px = np.mgrid[:HS, :HS] + 0.5
        # design space is ~50 units tall; fit it into the 48 px frame with a margin
        self.xx = (px - 2.6) / 0.88
        self.yy = (py - 4.6) / 0.88
        self.z = np.full((HS, HS), -1e9)
        self.n = np.zeros((HS, HS, 3))
        self.mat = np.full((HS, HS), -1)
        self.part = np.full((HS, HS), -1)
        self.t = np.zeros((HS, HS))       # position along a vessel (0 root .. 1 cut end)
        self.pid = 0

    def _put(self, inside, z, nx, ny, nz, mat, t=None):
        win = inside & (z > self.z)
        ln = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9
        self.z[win] = z[win]
        self.n[win] = np.stack([nx / ln, ny / ln, nz / ln], -1)[win]
        self.mat[win] = mat
        self.part[win] = self.pid
        if t is not None:
            self.t[win] = t[win]
        self.pid += 1

    def blob(self, cx, cy, rx, ry, ang, z0, rz, mat):
        c, s = np.cos(ang), np.sin(ang)
        dx, dy = self.xx - cx, self.yy - cy
        u, v = c * dx + s * dy, -s * dx + c * dy
        d2 = (u / rx) ** 2 + (v / ry) ** 2
        inside = d2 < 1
        h = np.sqrt(np.clip(1 - d2, 0, 1))
        gu, gv = u / rx ** 2, v / ry ** 2
        nx, ny = c * gu - s * gv, s * gu + c * gv
        self._put(inside, z0 + h * rz, nx, ny, h / rz, mat)

    def tube(self, pts, r0, r1, z0, z1, mat):
        cur = _bez(pts, 96)
        tt = np.linspace(0, 1, len(cur))
        P = np.stack([self.xx, self.yy], -1)[:, :, None, :]
        d = np.linalg.norm(P - cur[None, None], axis=-1)
        k = d.argmin(-1)
        dist = np.take_along_axis(d, k[..., None], -1)[..., 0]
        t = tt[k]
        r = r0 + (r1 - r0) * t
        inside = dist < r
        h = np.sqrt(np.clip(1 - (dist / r) ** 2, 0, 1))
        off = P[:, :, 0, :] - cur[k]
        self._put(inside, z0 + (z1 - z0) * t + h * r, off[..., 0] / r, off[..., 1] / r, h, mat, t)
        return cur[-1], cur[-1] - cur[-4], r1

    def cut(self, end, direction, r, z, mat_rim=5, mat_hole=6):
        """Severed vessel end seen at an angle: pale rim and a black lumen."""
        dvec = direction / (np.linalg.norm(direction) + 1e-9)
        ang = np.arctan2(dvec[1], dvec[0]) + np.pi / 2
        c, s = np.cos(ang), np.sin(ang)
        dx, dy = self.xx - end[0], self.yy - end[1]
        u, v = c * dx + s * dy, -s * dx + c * dy
        outer = (u / (r * 1.05)) ** 2 + (v / (r * 0.55)) ** 2 < 1
        inner = (u / (r * 0.62)) ** 2 + ((v + r * 0.06) / (r * 0.32)) ** 2 < 1
        one = np.ones((HS, HS))
        self._put(outer, z + 3 * one, 0 * one, -0.6 * one, one, mat_rim)
        self._put(inner, z + 4 * one, 0 * one, 0 * one, one, mat_hole)


# Palettes, darkest -> lightest. Muscle stays in dried-blood tones; vessels are paler and
# greyer, like old meat; veins go a bruised purple-grey.
PAL = {
    0: [(28, 7, 7), (52, 12, 11), (78, 19, 16), (104, 29, 24), (128, 42, 34), (150, 64, 52)],    # ventricles
    1: [(26, 7, 8), (46, 12, 13), (68, 20, 20), (90, 30, 28), (112, 42, 38), (134, 60, 52)],    # atria (darker)
    2: [(30, 10, 10), (56, 22, 21), (82, 38, 34), (106, 54, 47), (128, 74, 63), (150, 98, 83)],  # arteries
    3: [(22, 14, 20), (38, 24, 32), (56, 36, 44), (74, 50, 58), (92, 66, 72), (112, 86, 90)],   # veins
    4: [(40, 30, 20), (70, 56, 38), (98, 82, 58), (124, 108, 80), (150, 136, 104), (172, 160, 128)],  # fat
    5: [(44, 20, 18), (68, 36, 32), (90, 54, 47), (108, 70, 61), (122, 84, 73), (136, 98, 86)],  # cut rim
    6: [(6, 2, 2)] * 6,                                                                          # lumen
}
OUTLINE = (14, 4, 4)


def heart_frame(squeeze, panic, seed):
    rng_l = np.random.default_rng(seed)
    sc = _Scene()
    k = squeeze                                   # 0 relaxed .. 1 fully contracted
    bs = 1.0 - 0.07 * k                           # ventricles shrink on a beat
    vs = 1.0 + 0.10 * panic                       # vessels swell in panic

    def B(x, y):                                  # squeeze the body toward its centre
        return 25 + (x - 25) * bs, 31 + (y - 31) * bs

    # --- back layer: superior vena cava, aortic arch and its branches, left auricle
    end, d, r = sc.tube([(13, 22), (12, 13), (13, 4)], 3.6 * vs, 3.2 * vs, 2, 4, 3)
    sc.cut(end, d, r, 6)
    end, d, r = sc.tube([(23, 20), (21, 3), (35, 2), (38, 15)], 4.6 * vs, 4.0 * vs, 8, 3, 2)
    for pts, rr, zz in (([(24.5, 6), (24, 3), (23, 0.5)], 1.8, 9),
                        ([(29, 4.5), (29.5, 2), (30.5, 0.2)], 1.6, 8),
                        ([(33.5, 5), (35, 2.5), (36.5, 0.8)], 1.7, 8)):
        e2, d2, r2 = sc.tube(pts, rr * vs, rr * 0.9 * vs, zz, zz, 2)
        sc.cut(e2, d2, r2, zz + 1)
    sc.blob(*B(36, 21), 5.0, 3.4, 0.5, 6, 3, 1)          # left auricle
    # --- body
    sc.blob(*B(15, 25), 7.5, 6.5, 0.3, 4, 5, 1)          # right atrium
    sc.blob(*B(27, 32), 13.0 * bs, 11.0 * bs, 0.75, 6, 9, 0)   # left ventricle
    sc.blob(*B(20, 31), 10.5 * bs, 10.0 * bs, 0.35, 7, 9, 0)   # right ventricle
    sc.blob(*B(31, 41), 5.5 * bs, 4.0 * bs, 0.9, 5, 6, 0)      # apex
    # --- front: pulmonary trunk crossing over the aorta
    end, d, r = sc.tube([(25, 22), (27, 12), (33, 10), (40, 11)], 4.0 * vs, 3.4 * vs, 15, 12, 2)
    sc.cut(end, d, r, 15)
    # --- fat along the atrioventricular groove
    for (fx, fy, frx, fry, fa) in ((21, 22, 4.0, 1.6, -0.25), (28, 21.5, 3.0, 1.3, 0.1)):
        c, s = np.cos(fa), np.sin(fa)
        x, y = B(fx, fy)
        dx, dy = sc.xx - x, sc.yy - y
        m = ((c * dx + s * dy) / frx) ** 2 + ((-s * dx + c * dy) / fry) ** 2 < 1
        m &= sc.mat <= 1
        sc.mat[m] = 4

    # --- shading
    L = np.array([-0.45, -0.65, 0.62]); L /= np.linalg.norm(L)
    n = sc.n
    diff = np.clip((n * L).sum(-1), 0, 1)
    rim = np.clip(n[..., 0] * 0.6 + n[..., 1] * 0.5, 0, 1) ** 2 * 0.35   # cold back light, lower right
    lum = 0.12 + 0.78 * diff + rim
    bayer = np.array([[0, 2], [3, 1]]) / 4.0 - 0.375
    lum = lum + np.tile(bayer, (HS // 2, HS // 2)) * 0.16
    # rot: dark bruises and pitting on the muscle
    noise = rng_l.random((HS, HS))
    bruise = np.zeros((HS, HS))
    for _ in range(5):
        bx, by = rng_l.uniform(14, 36), rng_l.uniform(22, 42)
        br = rng_l.uniform(2.0, 3.8)
        bruise += np.clip(1 - np.hypot(sc.xx - bx, sc.yy - by) / br, 0, 1)
    lum -= np.where(sc.mat <= 1, bruise * 0.35 + (noise < 0.10) * 0.18, (noise < 0.06) * 0.12)
    idx = np.clip((lum * 6).astype(int), 0, 5)

    img = np.zeros((HS, HS, 4), np.uint8)
    on = sc.mat >= 0
    for m, pal in PAL.items():
        sel = sc.mat == m
        pal = np.array(pal, np.uint8)
        img[sel, :3] = pal[idx[sel]]
    img[on, 3] = 255

    # wet glints
    H = L + np.array([0, 0, 1.0]); H /= np.linalg.norm(H)
    spec = (n * H).sum(-1)
    glint = on & (spec > 0.985) & (sc.mat <= 2)
    img[glint, :3] = (196, 168, 150)

    # coronary vessels painted on the surface (only over muscle)
    def paint(pts, col, w=0.55):
        cur = _bez(pts, 80)
        for x, y in cur:
            xi, yi = int(x), int(y)
            for ox, oy in ((0, 0),) if w < 0.8 else ((0, 0), (1, 0)):
                X, Y = xi + ox, yi + oy
                if 0 <= X < HS and 0 <= Y < HS and sc.mat[Y, X] in (0, 1, 4):
                    img[Y, X, :3] = col
    vein = (40, 16, 26)
    art = (120, 62, 52) if not panic else (150, 70, 58)
    paint([(26, 22), (25, 30), (29, 38), (31, 44)], vein)              # anterior interventricular
    paint([(25.5, 27), (21, 31), (19, 37)], vein)
    paint([(27, 33), (33, 33), (37, 37)], vein)
    paint([(18, 24), (13, 30), (13, 36)], art)                          # right coronary
    paint([(29, 24), (34, 27), (37, 31)], art)
    paint([(22, 35), (24, 39), (23, 43)], vein)

    # depth outlines: darken the pixel behind wherever two parts meet with a depth jump
    z = sc.z
    edge = np.zeros((HS, HS), bool)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        zn = np.roll(np.roll(z, dy, 0), dx, 1)
        pn = np.roll(np.roll(sc.part, dy, 0), dx, 1)
        edge |= on & (pn >= 0) & (pn != sc.part) & (zn - z > 1.5)
    img[edge, :3] = (img[edge, :3] * 0.45).astype(np.uint8)
    # silhouette outline
    grow = on.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grow |= np.roll(np.roll(on, dy, 0), dx, 1)
    ol = grow & ~on
    img[ol, :3] = OUTLINE
    img[ol, 3] = 255

    # a drop of blood hanging from the severed aorta branch and one under the apex
    # (the beat pushes it further down)
    drips = ((40, 15, 3 + int(round(k * 3))), (31, 46, 0))
    for (x, y, l) in drips[:1]:
        X, Y = int(round(2.6 + x * 0.88)), int(round(4.6 + y * 0.88))
        for i in range(l):
            if Y + i < HS:
                img[Y + i, X] = (92, 14, 12, 255) if i < l - 1 else (122, 22, 18, 255)
        if Y + l < HS:
            img[Y + l, X] = (60, 8, 8, 255)
    if panic:
        # dark halo, never bright: the frame just gets "heavier"
        d = np.hypot((sc.xx - 25) / 22, (sc.yy - 27) / 22)
        a = (np.clip(1 - d, 0, 1) ** 1.5 * 120).astype(np.uint8)
        out = img[..., 3] == 0
        img[out, 0], img[out, 1], img[out, 2] = 90, 10, 10
        img[out, 3] = a[out]
    return img


def heart_frames():
    rng.random((4, 32, 32))   # the old heart drew from the shared stream: keep later textures unchanged
    frames = [heart_frame(0.0, 0, 11), heart_frame(0.5, 0, 11), heart_frame(1.0, 0, 11), heart_frame(1.0, 1, 11)]
    return np.concatenate(frames, axis=1)


# ---------------------------------------------------------------- flashlight battery (v0.5.5)
# Four worn AA cells that die one by one: a dead cell goes black and leaks rusty acid, the last
# live one gets a dried-blood label. 5 frames of 24x24: 0, 25, 50, 75, 100 %.
BAT = 24


def battery_frame(level):
    r = np.random.default_rng(5)
    bone_d, ash, soot, soot_d, ink = (150, 143, 128), (120, 114, 102), (78, 74, 66), (46, 43, 38), (16, 14, 12)
    blood_d, rust, acid = (78, 14, 12), (96, 62, 38), (112, 104, 60)
    f = np.zeros((BAT, BAT, 4), np.uint8)

    def put(x0, y0, x1, y1, c, a=255):
        f[y0:y1 + 1, x0:x1 + 1, :3] = c
        f[y0:y1 + 1, x0:x1 + 1, 3] = a

    for i in range(4):
        x0 = 2 + i * 5
        put(x0 - 1, 4, x0 + 4, 21, ink)              # outline
        put(x0, 3, x0 + 3, 3, ink)
        f[4, x0 - 1, 3] = f[4, x0 + 4, 3] = 0
        if i < level:
            put(x0 + 1, 4, x0 + 2, 4, ash)            # terminal
            put(x0, 5, x0 + 3, 20, ash)
            put(x0, 5, x0, 20, bone_d)                # highlight
            put(x0 + 3, 5, x0 + 3, 20, soot)          # shadow
            put(x0, 8, x0 + 3, 10, blood_d if level == 1 else soot)   # label band
        else:
            put(x0 + 1, 4, x0 + 2, 4, soot)
            put(x0, 5, x0 + 3, 20, (34, 31, 27))
            put(x0 + 3, 5, x0 + 3, 20, (24, 22, 19))
            for _ in range(int(r.integers(3, 7))):    # leaked crust running down
                y, x = 14 + int(r.integers(0, 7)), x0 + int(r.integers(0, 4))
                f[y, x, :3] = acid if r.random() < 0.6 else rust
            put(x0 + 1, 21, x0 + 1, 21, acid, 200)
            if r.random() < 0.7:
                put(x0 + 2, 22, x0 + 2, 22, acid, 140)
    grit = (f[..., 3] > 0) & (r.random((BAT, BAT)) < 0.10)
    f[grit, :3] = (f[grit, :3] * 0.7).astype(np.uint8)
    return f


def battery_frames():
    return np.concatenate([battery_frame(level) for level in range(5)], axis=1)


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
            if ch in TOP or len(g) > 7:
                dy = 0                                  # no room to drop a key with marks or a tail
            for y, line in enumerate(([TOP[ch]] if ch in TOP else []) + g):
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
