"""Bandage assets for 0.9 (plan item 4): cloth sounds, the 3D roll texture, the film-strip health bar.

Usage: python -I tools/assets/make_bandage.py <repo root> [sounds] [textures]
Everything is procedural (numpy/scipy/PIL), nothing taken from elsewhere.

sounds/bandage_wrap1..3.ogg  one pull of gauze around the arm: a dry fabric rustle with fine crackle
sounds/bandage_tie.ogg       the end: a short rip of the gauze, then two tugs as the knot tightens
textures/item/bandage_roll.png  32x32 texture of the 3D roll (models/item/bandage_roll.json):
    top-left 16x16 gauze side (worn weave, dirt, an old stain), top-right 16x16 end cap
    (the rolled spiral around a cardboard core), the strip below is the loose tail with blood
textures/gui/health_film.png  128x48: row 0 the empty strip of film (sprocket holes, scratches),
    row 1 the exposed frame band that shows health (light grey, tinted bone -> dried blood in code)
"""
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import signal
from scipy.io import wavfile

SR = 44100
PEAK_DB = -4.0


# ------------------------------------------------------------------ sound helpers

def band(x, lo, hi, order=4):
    return signal.sosfilt(signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def lowpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, btype="low", fs=SR, output="sos"), x)


def crackle(n, rng, density, lo=2000, hi=9000):
    """Sparse clicks of fibres sliding over each other; density = clicks per second (array or scalar)."""
    dens = np.broadcast_to(np.asarray(density, dtype=float), (n,))
    clicks = (rng.random(n) < dens / SR) * rng.standard_normal(n) * 6.0
    return band(clicks, lo, hi, order=2)


def bump(n, rise, fall):
    """Smooth swell: up over `rise` of the length, down over the rest (raised cosine)."""
    t = np.linspace(0, 1, n)
    e = np.where(t < rise, 0.5 - 0.5 * np.cos(np.pi * t / rise),
                 0.5 + 0.5 * np.cos(np.pi * (t - rise) / max(1e-6, fall)))
    e[t > rise + fall] = 0
    return e


def wrap(seed, sec):
    """One pull of gauze: rustle swell with the crackle following the same pull."""
    rng = np.random.default_rng(seed)
    n = int(sec * SR)
    pull = bump(n, 0.35 + rng.uniform(-0.08, 0.08), 0.55)
    # The pull is uneven: the cloth catches and slips a few times.
    wob = 1 + 0.35 * np.sin(2 * np.pi * rng.uniform(9, 14) * np.arange(n) / SR + rng.uniform(0, 6))
    rustle = band(rng.standard_normal(n), 900, 6500) * pull * wob
    fib = crackle(n, rng, 900 * pull + 40)
    body = lowpass(rng.standard_normal(n), 380) * pull * 0.6   # cloth on skin, a soft low brush
    return rustle * 0.55 + fib * 0.8 + body


def tie():
    """Rip of the gauze (dense crackle that speeds up), a beat, then two short tugs."""
    rng = np.random.default_rng(91)
    n = int(0.85 * SR)
    out = np.zeros(n)
    rip_n = int(0.24 * SR)
    t = np.linspace(0, 1, rip_n)
    dens = 1500 + 9000 * t ** 1.5
    rip = crackle(rip_n, rng, dens, 1500, 10000) * (0.4 + 0.6 * t) * np.where(t > 0.94, (1 - t) / 0.06, 1)
    rip += band(rng.standard_normal(rip_n), 2500, 8000) * 0.25 * t
    out[int(0.02 * SR): int(0.02 * SR) + rip_n] += rip
    for at, length, amp in ((0.42, 0.13, 0.9), (0.62, 0.17, 1.0)):
        m = int(length * SR)
        tug = band(rng.standard_normal(m), 500, 4500) * bump(m, 0.25, 0.75) * amp
        tug += lowpass(rng.standard_normal(m), 260) * bump(m, 0.15, 0.6) * amp * 0.8
        s = int(at * SR)
        out[s:s + m] += tug
    return out


def write_ogg(name, x, out_dir, tmp):
    x = np.nan_to_num(x)
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (PEAK_DB / 20)
    wav = Path(tmp) / f"{name}.wav"
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    dur = len(x) / SR
    fade = min(0.04, dur * 0.12)
    af = f"afade=t=in:d=0.006,afade=t=out:st={dur - fade:.4f}:d={fade:.4f}"
    ogg = out_dir / f"{name}.ogg"
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav), "-af", af, "-ac", "1", "-ar", str(SR),
                    "-c:a", "libvorbis", "-q:a", "6", str(ogg)], check=True)
    return ogg


def sounds(root):
    out = root / "src/main/resources/assets/bodycamhorror/sounds"
    with tempfile.TemporaryDirectory() as tmp:
        for i, (seed, sec) in enumerate(((11, 0.42), (12, 0.48), (13, 0.38)), 1):
            print("made", write_ogg(f"bandage_wrap{i}", wrap(seed, sec), out, tmp).name)
        print("made", write_ogg("bandage_tie", tie(), out, tmp).name)


# ------------------------------------------------------------------ textures

GAUZE = np.array([196, 189, 168])   # old off-white gauze, close to the HUD bone colour
GAUZE_DARK = np.array([150, 143, 124])
DIRT = np.array([104, 96, 80])
OLD_BLOOD = np.array([96, 30, 24])
FRESH_BLOOD = np.array([122, 22, 18])  # dried blood of the palette (7A1612)
CORE = np.array([92, 78, 58])          # cardboard core
HOLE = np.array([34, 30, 26])


def roll_texture(rng):
    img = np.zeros((32, 32, 4), dtype=float)
    # Side of the roll: weave lines every 2 px, uneven brightness, a smear of dirt and an old brown stain.
    for y in range(16):
        for x in range(16):
            c = GAUZE.astype(float)
            if x % 2 == 0 or y % 3 == 0:
                c = c * 0.93
            c = c * rng.uniform(0.94, 1.04)
            d = np.hypot(x - 11.5, y - 4.5)
            if d < 3.2:
                c = c * 0.55 + OLD_BLOOD * 0.45 * (1 - d / 3.2) + c * 0.45 * (d / 3.2)
            if y > 12 and rng.random() < 0.35:
                c = c * 0.8 + DIRT * 0.2
            img[y, x, :3] = c
            img[y, x, 3] = 255
    # End cap: rings of the rolled layers around a cardboard core with a dark hole.
    for y in range(16):
        for x in range(16):
            d = np.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 1.7:
                c = HOLE
            elif d < 3.0:
                c = CORE * rng.uniform(0.9, 1.05)
            else:
                ring = (d - 3.0) % 2.0
                c = (GAUZE_DARK if ring < 0.7 else GAUZE) * rng.uniform(0.97, 1.02)
                if d > 7.2:
                    c = c * 0.82
            img[y, 16 + x, :3] = c
            img[y, 16 + x, 3] = 255
    # Loose tail: gauze strip 32x4, soaked at the end.
    for y in range(16, 20):
        for x in range(32):
            c = GAUZE * rng.uniform(0.92, 1.03)
            if x % 2 == 0:
                c = c * 0.94
            soak = np.clip((x - 20) / 10, 0, 1) * rng.uniform(0.7, 1.0)
            c = c * (1 - soak) + FRESH_BLOOD * soak
            img[y, x, :3] = c
            img[y, x, 3] = 255
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def health_film(rng):
    """128x48: row 0 the empty strip, row 1 the exposed band (y 7..16 of the row)."""
    w, h = 128, 24
    img = np.zeros((h * 2, w, 4), dtype=float)
    soot = np.array([28, 26, 23])
    rim = np.array([78, 72, 62])
    # Row 0: film base, near-black, translucent, with sprocket holes along both edges.
    for y in range(h):
        for x in range(w):
            c = soot * rng.uniform(0.85, 1.15)
            a = 205
            edge = y < 6 or y >= h - 6
            if edge:
                c = c * 1.25
                hx = (x - 2) % 8
                hy_top, hy_bot = 1 <= y <= 4, h - 5 <= y <= h - 2
                if 1 <= hx <= 4 and (hy_top or hy_bot):
                    c = np.array([8, 8, 7])
                    a = 90  # the holes let the picture through
                    if hx in (1, 4) or y in (1, 4, h - 5, h - 2):
                        c, a = rim * 0.7, 170
            else:
                # Inside of the band: darker, with frame lines every 32 px (separate frames of the tape).
                c = c * 0.75
                if x % 32 == 0:
                    c = rim * 0.8
            if x == 0 or x == w - 1:
                c, a = rim * 0.6, 160
            img[y, x, :3] = c
            img[y, x, 3] = a
    # A few long scratches across the whole strip and dust specks.
    for _ in range(3):
        y = int(rng.integers(2, h - 2))
        x0 = int(rng.integers(0, w // 2))
        for x in range(x0, min(w, x0 + int(rng.integers(30, 90)))):
            img[y, x, :3] = img[y, x, :3] * 0.5 + rim * 0.5
    # Row 1: the band of exposed frames (y 7..16), light so it can be tinted in code.
    base = np.array([236, 230, 214])
    for y in range(7, 17):
        for x in range(w):
            c = base * rng.uniform(0.86, 1.0)
            # Burnt, uneven top and bottom of the exposure.
            if y in (7, 16):
                c = c * 0.72
            if x % 32 == 0:
                c = c * 0.55
            # Vertical scratches of a worn tape.
            if x % 23 == 5 or x % 41 == 17:
                c = c * 0.8
            img[h + y, x, :3] = c
            img[h + y, x, 3] = 235 if y not in (7, 16) else 170
    for _ in range(40):
        x, y = int(rng.integers(0, w)), int(rng.integers(7, 17))
        img[h + y, x, :3] *= 0.45
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def textures(root):
    rng = np.random.default_rng(404)
    tex = root / "src/main/resources/assets/bodycamhorror/textures"
    roll_texture(rng).save(tex / "item/bandage_roll.png")
    health_film(rng).save(tex / "gui/health_film.png")
    print("made bandage_roll.png, health_film.png")


def main():
    root = Path(sys.argv[1]).resolve()
    what = set(sys.argv[2:]) or {"sounds", "textures"}
    if "sounds" in what:
        sounds(root)
    if "textures" in what:
        textures(root)


if __name__ == "__main__":
    main()
