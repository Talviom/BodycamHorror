"""Our own dark versions of Minecraft's vanilla widget textures, so the settings, world list and
other vanilla screens match the found-footage style instead of bright grey buttons.

Usage: python -I tools/assets/make_widgets.py <repo root>

Writes (drawn here from scratch, nothing copied from Minecraft):
  overrides/assets/minecraft/textures/gui/widgets.png   buttons, hotbar, hotbar selection, offhand slots,
                                               language icon and difficulty lock icons (1.20.1 layout)
  overrides/assets/minecraft/textures/gui/slider.png    slider track and handle
  overrides/assets/minecraft/textures/gui/checkbox.png  checkbox
Preview: docs/previews/widgets_preview.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

RNG = np.random.default_rng(1998)

# Palette (project): bone C4BCAA, ash 787266, soot 4E4A42, dried blood 7A1612.
FILL = {"normal": (30, 28, 25), "hover": (46, 43, 38), "disabled": (17, 16, 14)}
EDGE = {"normal": (78, 74, 66), "hover": (126, 120, 107), "disabled": (42, 40, 36)}
TOP = {"normal": (40, 37, 33), "hover": (60, 56, 50), "disabled": (22, 21, 19)}
BOTTOM = {"normal": (19, 18, 16), "hover": (28, 26, 23), "disabled": (12, 11, 10)}
ALPHA = {"normal": 238, "hover": 245, "disabled": 215}


def put(img, x, y, rgb, a=255):
    img[y, x] = (*rgb, a)


def box(img, x0, y0, w, h, state, grain=3):
    """A worn dark box: noisy fill, soft top light and bottom shadow, a border with tired ink."""
    f = np.array(FILL[state], float)
    for y in range(h):
        for x in range(w):
            n = RNG.normal(0, grain)
            put(img, x0 + x, y0 + y, tuple(int(max(0, min(255, c + n))) for c in f), ALPHA[state])
    for x in range(1, w - 1):
        put(img, x0 + x, y0 + 1, TOP[state], ALPHA[state])
        put(img, x0 + x, y0 + h - 2, BOTTOM[state], ALPHA[state])
    edge = np.array(EDGE[state], float)
    for x in range(w):
        for y in (0, h - 1):
            k = 0.75 if RNG.random() < 0.12 else 1.0
            put(img, x0 + x, y0 + y, tuple(int(c * k) for c in edge), 255)
    for y in range(h):
        for x in (0, w - 1):
            k = 0.75 if RNG.random() < 0.12 else 1.0
            put(img, x0 + x, y0 + y, tuple(int(c * k) for c in edge), 255)
    # corners slightly rounded off
    for cx, cy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        img[y0 + cy, x0 + cx, 3] = 90


def glyph(img, x0, y0, rows, rgb, a=255):
    for y, line in enumerate(rows):
        for x, ch in enumerate(line):
            if ch == "#":
                put(img, x0 + x, y0 + y, rgb, a)
            elif ch == "+":
                put(img, x0 + x, y0 + y, tuple(int(c * 0.6) for c in rgb), a)


LOCK_CLOSED = [
    "  ####  ",
    " #    # ",
    " #    # ",
    "########",
    "#++++++#",
    "#+++#++#",
    "#+++#++#",
    "#++++++#",
    "########",
]
LOCK_OPEN = [
    "     ####",
    "    #    #",
    "    #    #",
    "########  ",
    "#++++++#  ",
    "#+++#++#  ",
    "#+++#++#  ",
    "#++++++#  ",
    "########  ",
]
GLOBE = [
    "  ####  ",
    " #+##+# ",
    "#+#++#+#",
    "########",
    "#+#++#+#",
    "#+#++#+#",
    " #+##+# ",
    "  ####  ",
]


def widgets():
    img = np.zeros((256, 256, 4), np.uint8)
    # Buttons 200x20: disabled y=46, normal y=66, hover y=86 (drawn nine-sliced, slice 4).
    for state, y in (("disabled", 46), ("normal", 66), ("hover", 86)):
        box(img, 0, y, 200, 20, state)

    # Hotbar 182x22 at (0,0): nine dark slots.
    for y in range(22):
        for x in range(182):
            put(img, x, y, (14, 13, 12), 170)
    for i in range(9):
        x0 = 1 + i * 20
        for t in range(20):
            for (x, y) in ((x0 + t, 1), (x0 + t, 20), (x0, 1 + t), (x0 + 19, 1 + t)):
                if y <= 20:
                    put(img, x, y, EDGE["normal"], 200)
    for x in range(182):
        put(img, x, 0, (8, 8, 7), 220)
        put(img, x, 21, (8, 8, 7), 220)
    # Selected slot frame 24x24 at (0,22).
    for t in range(24):
        for (x, y) in ((t, 22), (t, 22 + 22), (0, 22 + t), (23, 22 + t)):
            if y < 22 + 24:
                put(img, x, y, (170, 162, 146), 255)
        for (x, y) in ((t, 23), (t, 22 + 21), (1, 22 + t), (22, 22 + t)):
            if 0 < t < 23 and y < 22 + 24:
                put(img, x, y, (96, 91, 81), 230)
    # Offhand slots 29x24 at (24,22) and (53,22): a slot box inside.
    for ox, sx in ((24, 0), (53, 7)):
        box(img, ox + sx, 22, 22, 22, "normal", grain=2)

    # Language icon 20x20: normal (0,106), hover (0,126).
    for y, state in ((106, "normal"), (126, "hover")):
        box(img, 0, y, 20, 20, state)
        glyph(img, 6, y + 6, GLOBE, (150, 143, 128) if state == "normal" else (196, 188, 170))

    # Difficulty lock 20x20: locked x=0, unlocked x=20; normal y=146, hover y=166, disabled y=186.
    for x0, rows in ((0, LOCK_CLOSED), (20, LOCK_OPEN)):
        for y0, state in ((146, "normal"), (166, "hover"), (186, "disabled")):
            box(img, x0, y0, 20, 20, state)
            ink = {"normal": (150, 143, 128), "hover": (196, 188, 170), "disabled": (70, 66, 60)}[state]
            glyph(img, x0 + 5, y0 + 5, rows, ink)
    return Image.fromarray(img, "RGBA")


def slider():
    """1.20.1 slider.png: track y=0 (normal) and y=20 (focused), handle y=40 (normal) and y=60 (hover).
    Drawn with 256x256 texture coordinates, so the image is 256x256."""
    img = np.zeros((256, 256, 4), np.uint8)
    box(img, 0, 0, 200, 20, "disabled")
    box(img, 0, 20, 200, 20, "normal")
    # Handle: only the 4 left and 4 right columns are used (8 px wide, nine-sliced).
    for y0, fill, edge in ((40, (110, 104, 93), (150, 143, 128)), (60, (140, 133, 119), (196, 188, 170))):
        for y in range(20):
            for x in range(200):
                n = RNG.normal(0, 3)
                put(img, x, y0 + y, tuple(int(max(0, min(255, c + n))) for c in fill), 255)
        for x in range(200):
            put(img, x, y0, edge)
            put(img, x, y0 + 19, tuple(int(c * 0.6) for c in edge))
        for y in range(20):
            put(img, 0, y0 + y, edge)
            put(img, 199, y0 + y, tuple(int(c * 0.6) for c in edge))
    return Image.fromarray(img, "RGBA")


X_MARK = [
    "#     #",
    " #   # ",
    "  # #  ",
    "   #   ",
    "  # #  ",
    " #   # ",
    "#     #",
]


def checkbox():
    """checkbox.png 64x64: unchecked (0,0), unchecked focused (20,0), checked (0,20), checked focused (20,20)."""
    img = np.zeros((64, 64, 4), np.uint8)
    for x0, state in ((0, "normal"), (20, "hover")):
        for y0, checked in ((0, False), (20, True)):
            box(img, x0, y0, 20, 20, state)
            if checked:
                glyph(img, x0 + 6, y0 + 6, X_MARK, (196, 188, 170) if state == "hover" else (170, 162, 146))
    return Image.fromarray(img, "RGBA")


def preview(root, w_img, s_img, c_img):
    """Rough look of an options screen: film grain background, buttons, a slider and a checkbox."""
    bg = Image.open(root / "src/main/resources/overrides/assets/minecraft/textures/gui/options_background.png").convert("RGB")
    W, H = 427, 240
    canvas = Image.new("RGB", (W, H))
    for x in range(0, W, 32):
        for y in range(0, H, 32):
            canvas.paste(bg.resize((32, 32)), (x, y))
    canvas = Image.fromarray((np.asarray(canvas) * 0.25).astype(np.uint8)).convert("RGBA")

    def nine(src, u, v, w, h):
        tile = src.crop((u, v, u + 200, v + 20))
        out = Image.new("RGBA", (w, h))
        out.paste(tile.crop((0, 0, w - 4, 20)), (0, 0))
        out.paste(tile.crop((196, 0, 200, 20)), (w - 4, 0))
        return out

    for i, (u, v) in enumerate(((0, 66), (0, 86), (0, 66), (0, 46))):
        x = 60 + (i % 2) * 160
        y = 60 + (i // 2) * 26
        canvas.alpha_composite(nine(w_img, u, v, 150, 20), (x, y))
    canvas.alpha_composite(nine(s_img, 0, 0, 150, 20), (60, 120))
    canvas.alpha_composite(s_img.crop((0, 40, 4, 60)), (120, 120))
    canvas.alpha_composite(s_img.crop((196, 40, 200, 60)), (124, 120))
    canvas.alpha_composite(c_img.crop((0, 20, 20, 40)), (220, 120))
    canvas.alpha_composite(w_img.crop((0, 0, 182, 22)), ((W - 182) // 2, 200))
    canvas.alpha_composite(w_img.crop((0, 22, 24, 46)), ((W - 182) // 2 - 1 + 40, 199))
    canvas.alpha_composite(w_img.crop((0, 146, 40, 166)), (300, 120))
    canvas.convert("RGB").resize((W * 3, H * 3), Image.NEAREST).save(root / "docs/previews/widgets_preview.png")


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out = root / "src/main/resources/overrides/assets/minecraft/textures/gui"
    out.mkdir(parents=True, exist_ok=True)
    w_img, s_img, c_img = widgets(), slider(), checkbox()
    w_img.save(out / "widgets.png", optimize=True)
    s_img.save(out / "slider.png", optimize=True)
    c_img.save(out / "checkbox.png", optimize=True)
    preview(root, w_img, s_img, c_img)
    print("wrote widgets.png, slider.png, checkbox.png and docs/previews/widgets_preview.png")


if __name__ == "__main__":
    main()
