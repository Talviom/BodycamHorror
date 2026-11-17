"""Assets for the round objective "carry out the tape" (own pixel art and synthesis, dark style).

Usage: python -I tools/assets/make_tape_assets.py <repo root>
Writes:
  textures/item/tape.png          16x16 worn black VHS cassette, faded bone label strip (no readable text)
  textures/block/exit_marker.png  16x16 floor plate: worn concrete/metal, faded painted arrow, stencil marks
  sounds/tape_hiss.ogg            ~2 s quiet tape hiss, slight warble, faint distant murmur (loopable-ish)
  sounds/tape_pickup.ogg          ~1 s plastic cassette clack + short static burst
  sounds/tape_out.ogg             ~2.5 s VCR insert clunk, motor whirr, swelling static that cuts off
Previews: docs/previews/tape_assets.png
"""
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import make_sounds as ms  # noqa: E402  (shared synthesis helpers and the ffmpeg writer)

rng = np.random.default_rng(4242)


# ---------------------------------------------------------------- textures

def tape_item():
    """Diagonal-free front view of a cassette, like vanilla flat items; worn and dark."""
    N = 16
    img = np.zeros((N, N, 4), np.uint8)
    body = (24, 23, 22)
    edge = (10, 10, 10)
    # Cassette body 14x9 at (1,4)
    img[4:13, 1:15, :3] = body
    img[4:13, 1:15, 3] = 255
    img[4, 1:15, :3] = (52, 50, 47)                 # top bevel highlight
    img[12, 1:15, :3] = edge
    img[4:13, 1, :3] = edge
    img[4:13, 14, :3] = edge
    # Faded bone label strip on the top part (no text, just smudges)
    img[5:8, 3:13, :3] = (150, 143, 126)
    for x in range(3, 13):
        if rng.random() < 0.35:
            img[6, x, :3] = (118, 112, 99)            # illegible handwriting smudge
    img[5:8, 3:13, :3] = (img[5:8, 3:13, :3] * (0.82 + 0.18 * rng.random((3, 10, 1)))).astype(np.uint8)
    # Window with two reels
    img[8:11, 4:12, :3] = (12, 12, 12)
    for cx in (5, 10):
        img[9, cx, :3] = (70, 66, 60)
        img[8:11, cx, :3] = np.maximum(img[8:11, cx, :3], (40, 38, 35))
    img[9, 6:10, :3] = (34, 26, 22)                   # visible tape between the reels
    # Wear: scratches and a chipped corner
    for _ in range(6):
        x, y = rng.integers(2, 14), rng.integers(9, 12)
        img[y, x, :3] = (46, 44, 41)
    img[12, 14, 3] = 0
    img[4, 1, 3] = 0
    return img


def exit_marker():
    """Top of a floor plate: dark worn concrete, faded painted arrow (pointing up = toward the exit)."""
    N = 16
    base = np.array([58, 56, 52], float)
    img = np.zeros((N, N, 4), np.uint8)
    n = (rng.random((N, N, 1)) - 0.5) * 0.22
    img[..., :3] = np.clip(base * (1 + n), 0, 255)
    img[..., 3] = 255
    # Metal plate edge and bolts
    img[0, :, :3] = img[-1, :, :3] = img[:, 0, :3] = img[:, -1, :3] = (38, 37, 34)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img[y, x, :3] = (88, 85, 78)
    # Faded painted arrow in worn bone paint (pointing toward the top of the texture)
    paint = np.array([150, 142, 124], float)
    arrow = [(7, 3), (8, 3), (6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5)]
    arrow += [(x, y) for y in range(6, 12) for x in (7, 8)]
    for x, y in arrow:
        if rng.random() > 0.22:                       # worn paint
            img[y, x, :3] = np.clip(paint * rng.uniform(0.75, 1.0), 0, 255)
    # Stencil-like dashes at the bottom (hint of a word, unreadable)
    for x in (3, 4, 6, 9, 11, 12):
        if rng.random() > 0.3:
            img[13, x, :3] = (112, 106, 94)
    # Grime streaks
    for _ in range(18):
        x, y = rng.integers(1, 15), rng.integers(1, 15)
        img[y, x, :3] = (img[y, x, :3] * 0.7).astype(np.uint8)
    return img


# ---------------------------------------------------------------- sounds

SR = ms.SR


def tape_hiss():
    r = np.random.default_rng(71)
    sec = 2.0
    n = int(sec * SR)
    tt = ms.t_axis(sec)
    hiss = ms.band(r.standard_normal(n), 1800, 9000, order=2) * 0.5
    warble = 1 + 0.15 * np.sin(2 * np.pi * 0.9 * tt) + 0.05 * np.sin(2 * np.pi * 7.0 * tt)
    hiss *= warble
    # Faint distant murmur: band-limited noise through slowly moving low formants.
    mur = ms.band(r.standard_normal(n), 250, 900, order=2)
    mur *= (0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * tt + 1.0)) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * tt))
    hum = 0.08 * np.sin(2 * np.pi * 60 * tt)
    x = hiss + 0.35 * mur + hum
    # Make it loop-friendly: equal-power crossfade of the last 0.25 s into the start.
    k = int(0.25 * SR)
    w = np.linspace(0, 1, k)
    x[:k] = x[:k] * np.sqrt(w) + x[-k:] * np.sqrt(1 - w)
    return x[:-k]


def tape_pickup():
    r = np.random.default_rng(72)
    sec = 1.0
    n = int(sec * SR)
    x = np.zeros(n)
    # Plastic clack: two quick bright knocks (case shell), then a softer rattle of the reels.
    for at, base, amp in ((0.02, 1450, 1.0), (0.07, 1900, 0.6)):
        x += ms.damped_modes(n, [(base, 0.018, 1.0), (base * 2.3, 0.01, 0.5), (base * 0.55, 0.03, 0.4)], r,
                             start=int(at * SR)) * amp
    for at in (0.12, 0.15, 0.19):
        ms.place(x, ms.band(r.standard_normal(int(0.012 * SR)), 2500, 7000) * 0.25, at * SR)
    # Short static burst
    st = int(0.28 * SR)
    L = int(0.35 * SR)
    burst = ms.band(r.standard_normal(L), 600, 7000) * np.hanning(L) * 0.45
    ms.place(x, burst, st)
    return x


def tape_out():
    r = np.random.default_rng(73)
    sec = 2.5
    n = int(sec * SR)
    tt = ms.t_axis(sec)
    x = np.zeros(n)
    # VCR insert: plastic slide, then a heavy mechanical clunk.
    slide = ms.band(r.standard_normal(int(0.25 * SR)), 800, 4000) * np.linspace(0.1, 0.5, int(0.25 * SR))
    ms.place(x, slide, 0.0)
    ms.place(x, ms.clunk(int(0.5 * SR), r, heavy=0.8), 0.25)
    # Motor whirr rising.
    k = np.clip((tt - 0.45) / 0.8, 0, 1)
    f = 90 + 120 * k
    motor = (np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.4 * np.sin(4 * np.pi * np.cumsum(f) / SR)) * k * 0.25
    x += ms.lowpass(motor, 1200) * (tt > 0.45)
    # Static swelling from 1.1 s and cutting off hard at 2.35 s.
    sw = np.clip((tt - 1.1) / 1.2, 0, 1) ** 1.5
    stat = ms.band(r.standard_normal(n), 400, 8000) * sw * 0.9
    x += stat
    cut = int(2.35 * SR)
    x[cut:] *= np.linspace(1, 0, n - cut) ** 6
    return x


def main():
    root = Path(sys.argv[1]).resolve()
    a = root / "src/main/resources/assets/bodycamhorror"
    (a / "textures/item").mkdir(parents=True, exist_ok=True)
    (a / "textures/block").mkdir(parents=True, exist_ok=True)
    t = tape_item()
    e = exit_marker()
    Image.fromarray(t, "RGBA").save(a / "textures/item/tape.png", optimize=True)
    Image.fromarray(e, "RGBA").save(a / "textures/block/exit_marker.png", optimize=True)
    sounds = a / "sounds"
    with tempfile.TemporaryDirectory() as tmp:
        # The hiss is a quiet ambient emitter: dense noise gets a lower peak than the one-shots.
        for name, fn, peak in (("tape_hiss", tape_hiss, -10.0), ("tape_pickup", tape_pickup, -3.0),
                               ("tape_out", tape_out, -3.0)):
            ms.PEAK_DB = peak
            print("made", ms.write(name, fn(), sounds, tmp).name)
        ms.PEAK_DB = -3.0
    prev = root / "docs/previews"
    prev.mkdir(parents=True, exist_ok=True)
    canvas = Image.new("RGBA", (16 * 16 + 40, 16 * 16 + 20), (24, 23, 22, 255))
    canvas.alpha_composite(Image.fromarray(t).resize((128, 128), Image.NEAREST), (10, 10))
    canvas.alpha_composite(Image.fromarray(e).resize((128, 128), Image.NEAREST), (158, 10))
    canvas.convert("RGB").save(prev / "tape_assets.png")
    print("textures tape.png, exit_marker.png")


if __name__ == "__main__":
    main()
