"""Background for the main menu: a dark corridor seen through a chest-mounted bodycam.

Usage: python -I tools/assets/make_menu.py <repo root>

Everything is rendered here from scratch with a tiny ray caster (no third-party images):
a long hallway with floor tiles, wall panels and side doors, one weak ceiling lamp halfway,
and an open door at the far end with light behind it. Three versions are written:
  corridor_0.png  empty hallway
  corridor_1.png  a figure standing in the lit doorway at the end
  corridor_2.png  the same figure closer, just past the lamp
The menu switches between them only while the lights are out, so the figure "moves" in the dark.
Images are grayscale; the game tints them bone-coloured and adds grain, vignette and flicker.
Also writes our own dark film-grain tiles over Minecraft's dirt background of vanilla screens
(overrides/assets/minecraft/textures/gui/options_background.png, light_dirt_background.png; the built-in pack on top).
Preview: docs/previews/menu_bg.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

OUT_W, OUT_H = 960, 540
SS = 2  # supersampling
W, H = OUT_W * SS, OUT_H * SS

# Corridor (metres). Camera at the origin looking down +z, slightly to the left of centre.
HALF_W = 1.15
FLOOR_Y = -1.45
CEIL_Y = 1.20
LENGTH = 16.0
CAM_X = 0.18
DOOR_X = 0.28      # far doorway centre
DOOR_HALF = 0.46
DOOR_TOP = FLOOR_Y + 2.05
LAMP_Z = 8.0

RNG = np.random.default_rng(1998)
TAB = RNG.random((256, 256))


def vnoise(a, b):
    ia = np.floor(a).astype(np.int64)
    ib = np.floor(b).astype(np.int64)
    fa = a - ia
    fb = b - ib
    fa = fa * fa * (3 - 2 * fa)
    fb = fb * fb * (3 - 2 * fb)

    def g(i, j):
        return TAB[i & 255, j & 255]

    return (g(ia, ib) * (1 - fa) * (1 - fb) + g(ia + 1, ib) * fa * (1 - fb)
            + g(ia, ib + 1) * (1 - fa) * fb + g(ia + 1, ib + 1) * fa * fb)


def fbm(a, b, octaves=3):
    s = 0.0
    amp = 0.5
    for _ in range(octaves):
        s = s + amp * vnoise(a, b)
        a = a * 2.03 + 17.1
        b = b * 2.03 + 5.3
        amp *= 0.5
    return s / 0.875


def rays():
    aspect = W / H
    ty = np.tan(np.radians(64) / 2)
    xs = ((np.arange(W) + 0.5) / W * 2 - 1) * ty * aspect
    ys = (1 - (np.arange(H) + 0.5) / H * 2) * ty
    x, y = np.meshgrid(xs, ys)
    # Wide bodycam lens: a little barrel distortion.
    r2 = x * x + y * y
    k = 1 + 0.10 * r2
    x = x * k
    y = y * k
    # Chest camera: pitched down a bit and rolled a little.
    p = np.radians(-5.5)
    dy = y * np.cos(p) + np.sin(p)
    dz = -y * np.sin(p) + np.cos(p)
    roll = np.radians(1.6)
    dx = x * np.cos(roll) - dy * np.sin(roll)
    dy = x * np.sin(roll) + dy * np.cos(roll)
    return dx, dy, dz


def trace(dx, dy, dz):
    inf = 1e9
    with np.errstate(divide="ignore", invalid="ignore"):
        t = np.stack([
            np.where(dx < -1e-6, (-HALF_W - CAM_X) / dx, inf),
            np.where(dx > 1e-6, (HALF_W - CAM_X) / dx, inf),
            np.where(dy < -1e-6, FLOOR_Y / dy, inf),
            np.where(dy > 1e-6, CEIL_Y / dy, inf),
            np.where(dz > 1e-6, LENGTH / dz, inf),
        ])
    surf = t.argmin(0)
    tt = t.min(0)
    return surf, tt, CAM_X + tt * dx, tt * dy, tt * dz


def albedo(surf, px, py, pz):
    a = np.zeros_like(px)
    grime = fbm(px * 1.3 + pz * 0.9, pz * 1.1 + py * 1.7)

    # floor: worn tiles with dark seams and stains
    f = surf == 2
    tx = np.floor(px[f] / 0.6)
    tz = np.floor(pz[f] / 0.6)
    tile = 0.85 + 0.25 * TAB[(tx.astype(np.int64) * 7) & 255, (tz.astype(np.int64) * 13) & 255]
    seam = (np.mod(px[f], 0.6) < 0.025) | (np.mod(pz[f], 0.6) < 0.025)
    stain = fbm(px[f] * 0.7, pz[f] * 0.5)
    a[f] = 0.30 * tile * np.where(seam, 0.45, 1.0) * (0.55 + 0.6 * stain)

    # walls: dark lower panels, lighter plaster above, vertical seams, two side doors
    for side in (0, 1):
        m = surf == side
        y = py[m]
        z = pz[m]
        h = y - FLOOR_Y
        v = np.where(h < 1.0, 0.20, 0.34)
        v = np.where(np.abs(h - 1.0) < 0.03, 0.10, v)
        v = np.where(h < 0.12, 0.12, v)
        v = np.where(np.mod(z, 2.4) < 0.03, v * 0.55, v)
        door_z = 4.4 if side == 0 else 10.2
        in_door = (z > door_z) & (z < door_z + 1.0) & (h < 2.05)
        frame = in_door & ((z < door_z + 0.07) | (z > door_z + 0.93) | (h > 1.98))
        leaf = 0.03 if side == 1 else 0.16  # the right door stands open into black
        v = np.where(in_door, leaf, v)
        v = np.where(frame, 0.09, v)
        a[m] = v * (0.6 + 0.55 * grime[m])

    # ceiling: tiles and dead light fittings
    c = surf == 3
    seam = (np.mod(px[c], 0.6) < 0.03) | (np.mod(pz[c], 0.6) < 0.03)
    a[c] = 0.22 * np.where(seam, 0.5, 1.0) * (0.6 + 0.5 * grime[c])

    # far wall
    e = surf == 4
    a[e] = 0.30 * (0.6 + 0.5 * grime[e])
    return a


def shade(surf, px, py, pz, tt, a):
    normals = {0: (1, 0, 0), 1: (-1, 0, 0), 2: (0, 1, 0), 3: (0, -1, 0), 4: (0, 0, -1)}
    nx = np.zeros_like(px)
    ny = np.zeros_like(px)
    nz = np.zeros_like(px)
    for k, (x, y, z) in normals.items():
        m = surf == k
        nx[m], ny[m], nz[m] = x, y, z

    def light(lx, ly, lz, power, cone):
        vx, vy, vz = lx - px, ly - py, lz - pz
        d2 = vx * vx + vy * vy + vz * vz + 1e-4
        d = np.sqrt(d2)
        lam = np.clip((nx * vx + ny * vy + nz * vz) / d, 0, 1)
        return power * lam * cone(vx / d, vy / d, vz / d) / (1 + d2 * 0.35)

    # Light pouring out of the far doorway, only towards the camera.
    door = light(DOOR_X, FLOOR_Y + 1.1, LENGTH - 0.15, 9.0, lambda x, y, z: np.clip(-z, 0, 1) ** 0.6)
    # One weak lamp halfway, pointing down.
    lamp = light(0.0, CEIL_Y - 0.08, LAMP_Z, 4.2, lambda x, y, z: np.clip(-y, 0, 1) ** 1.5)
    lit = a * (door + lamp + 0.01)

    # Emitters: the doorway and the lamp itself.
    e = surf == 4
    in_door = e & (np.abs(px - DOOR_X) < DOOR_HALF) & (py < DOOR_TOP)
    hgt = (py - FLOOR_Y) / (DOOR_TOP - FLOOR_Y)
    glow = 1.45 * (0.75 + 0.35 * (1 - hgt)) * (0.85 + 0.25 * fbm(px * 4, py * 4))
    lit = np.where(in_door, glow, lit)
    c = surf == 3
    fitting = c & (np.abs(px) < 0.32) & (np.abs(pz - LAMP_Z) < 0.55)
    lit = np.where(fitting, 0.75, lit)
    dead = c & (np.abs(px) < 0.32) & ((np.abs(pz - 3.4) < 0.55) | (np.abs(pz - 12.6) < 0.55))
    lit = np.where(dead, 0.004, lit)

    # Dust in the air: distant things sink into a faint haze.
    haze = 1 - np.exp(-tt * 0.07)
    return lit * (1 - haze) + 0.018 * haze


def figure_mask(dx, dy, dz, tt, fx, fz, scale_arms=1.0):
    """Tall thin figure with a cocked head and arms a little too long, as a mask in the image."""
    with np.errstate(divide="ignore", invalid="ignore"):
        t = np.where(dz > 1e-6, fz / dz, 1e9)
    u = CAM_X + t * dx - fx
    v = t * dy - FLOOR_Y
    vis = t < tt  # not hidden behind a wall
    ang = np.radians(14)
    hu = (u - 0.02) * np.cos(ang) - (v - 1.71) * np.sin(ang)
    hv = (u - 0.02) * np.sin(ang) + (v - 1.71) * np.cos(ang)
    head = (hu / 0.105) ** 2 + (hv / 0.135) ** 2 < 1
    neck = (np.abs(u - 0.01) < 0.045) & (v > 1.44) & (v < 1.62)
    k = np.clip((v - 0.92) / (1.46 - 0.92), 0, 1)
    torso = (v > 0.9) & (v < 1.47) & (np.abs(u) < 0.165 + 0.07 * k)
    shoulder = ((np.abs(u) - 0.20) / 0.06) ** 2 + ((v - 1.43) / 0.05) ** 2 < 1
    arms = np.zeros_like(u, dtype=bool)
    for sgn in (-1, 1):
        top = np.array([sgn * 0.225, 1.42])
        bot = np.array([sgn * 0.27, 1.42 - 0.88 * scale_arms])
        s = np.clip((1.42 - v) / (top[1] - bot[1]), 0, 1)
        cu = top[0] + (bot[0] - top[0]) * s
        arms |= (v < 1.44) & (v > bot[1]) & (np.abs(u - cu) < 0.045 - 0.015 * s)
    legs = (v < 0.93) & (v > 0) & ((np.abs(u - 0.085) < 0.065) | (np.abs(u + 0.085) < 0.065))
    return vis & (head | neck | torso | shoulder | arms | legs)


def tonemap(x):
    x = x * 2.0
    y = np.power(np.clip(x / (1 + x), 0, 1), 1 / 1.9)
    # deeper blacks: the frame should be mostly dark, not foggy grey
    return np.clip((y - 0.07) / 0.93, 0, 1)


def render(figure):
    dx, dy, dz = rays()
    surf, tt, px, py, pz = trace(dx, dy, dz)
    a = albedo(surf, px, py, pz)
    img = shade(surf, px, py, pz, tt, a)

    if figure:
        fz, fx, dark = (LENGTH - 0.25, DOOR_X + 0.02, 0.004) if figure == 1 else (LAMP_Z + 2.6, -0.05, 0.004)
        m = figure_mask(dx, dy, dz, tt, fx, fz, scale_arms=1.0 if figure == 1 else 1.05)
        haze = 1 - np.exp(-fz * 0.07)
        # never lighter than what is behind it: it only blocks light
        img = np.where(m, np.minimum(img, dark * (1 - haze) + 0.018 * haze), img)

    # Bloom around the doorway and the lamp.
    lum = Image.fromarray(np.clip(np.maximum(img - 0.5, 0) * 120, 0, 255).astype(np.uint8))
    bloom = np.asarray(lum.filter(ImageFilter.GaussianBlur(28 * SS)), dtype=np.float64) / 120
    img = img + bloom * 0.9

    v = tonemap(img)
    small = Image.fromarray(np.clip(v * 255, 0, 255).astype(np.uint8)).resize((OUT_W, OUT_H), Image.LANCZOS)
    arr = np.asarray(small, dtype=np.float64)
    # Slight softness of a cheap lens, then dither so the dark gradients do not band.
    arr = np.asarray(Image.fromarray(arr.astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6)), dtype=np.float64)
    arr = arr + np.random.default_rng(figure + 3).uniform(-0.8, 0.8, arr.shape)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "L")


def preview(img, path):
    """Rough idea of the in-game look: bone tint, dimmed, grain and dark edges."""
    a = np.asarray(img, dtype=np.float64) / 255 * 0.8
    tint = np.array([0xC4, 0xBC, 0xAA]) / 0xC4
    rgb = a[..., None] * tint[None, None, :]
    yy, xx = np.mgrid[0:OUT_H, 0:OUT_W]
    r = np.sqrt(((xx - OUT_W / 2) / (OUT_W / 2)) ** 2 + ((yy - OUT_H / 2) / (OUT_H / 2)) ** 2)
    rgb *= np.clip(1.15 - 0.55 * r ** 2, 0.15, 1)[..., None]
    rgb += np.random.default_rng(5).normal(0, 0.025, rgb.shape)
    Image.fromarray(np.clip(rgb * 255, 0, 255).astype(np.uint8)).save(path)


def film_tile(base, size=64, seed=7):
    """Tileable dark film grain that replaces Minecraft's dirt behind vanilla screens.
    The game draws it tinted to 25% (screens) or 12% (lists), so the base is mid grey."""
    r = np.random.default_rng(seed)
    fine = r.normal(0, 1, (size, size))
    low = r.normal(0, 1, (size, size))
    for _ in range(6):  # wrap-around blur keeps it tileable
        low = (low + np.roll(low, 1, 0) + np.roll(low, -1, 0) + np.roll(low, 1, 1) + np.roll(low, -1, 1)) / 5
    low = low / (np.abs(low).max() + 1e-6)
    v = base + fine * base * 0.16 + low * base * 0.12
    # a few dust specks
    for _ in range(5):
        x, y = r.integers(0, size, 2)
        v[y, x] = base * (0.35 if r.random() < 0.5 else 1.5)
    rgb = np.stack([v, v * 0.96, v * 0.87], -1)
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB")


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    vanilla_gui = root / "src/main/resources/overrides/assets/minecraft/textures/gui"
    vanilla_gui.mkdir(parents=True, exist_ok=True)
    film_tile(110).save(vanilla_gui / "options_background.png", optimize=True)
    film_tile(42, seed=8).save(vanilla_gui / "light_dirt_background.png", optimize=True)
    print("wrote", vanilla_gui / "options_background.png", "and light_dirt_background.png")
    out = root / "src/main/resources/assets/bodycamhorror/textures/gui/menu"
    out.mkdir(parents=True, exist_ok=True)
    imgs = []
    for i in range(3):
        img = render(i)
        img.save(out / f"corridor_{i}.png", optimize=True)
        imgs.append(img)
        print("wrote", out / f"corridor_{i}.png")
    prev = root / "docs/previews"
    prev.mkdir(parents=True, exist_ok=True)
    preview(imgs[0], prev / "menu_bg.png")
    preview(imgs[1], prev / "menu_bg_far.png")
    preview(imgs[2], prev / "menu_bg_near.png")


if __name__ == "__main__":
    main()
