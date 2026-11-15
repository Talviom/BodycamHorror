"""Generates the block textures (32x32 PNG) and JSON models for the lamp, breaker box and generator.

Usage: python -I tools/assets/make_blocks.py <repo root>
Models are authored facing NORTH and fit inside the collision shapes in the Java code:
  lamp      x1..15  y14..16  z5..11
  breaker   x3..13  y2..14   z13..16
  generator x1..15  y0..14   z1..15
UVs are written explicitly in Minecraft's default per-face layout, so the Blender preview
and the game agree.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

N = 32  # texture size in pixels; 1 model unit = 2 px
NS = "bodycamhorror:block/"


# ------------------------------------------------------------------ textures

def rng(seed):
    return np.random.default_rng(seed)


def value_noise(r, scale, size=N):
    """Smooth noise: a small random grid upscaled bilinearly."""
    g = max(2, size // scale)
    small = r.random((g + 1, g + 1))
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((size, size), Image.BILINEAR)
    return np.asarray(img, dtype=np.float32) / 255.0


def base(color, r, var=0.07, grain=0.05):
    c = np.array(color, np.float32) / 255.0
    n = (value_noise(r, 8) - 0.5) * 2 * var + (r.random((N, N)) - 0.5) * 2 * grain
    return np.clip(c[None, None, :] * (1 + n[..., None]), 0, 1)


def grime(img, r, strength=0.35, streaks=True):
    """Dirt that collects lower down, plus vertical drip streaks."""
    y = np.linspace(0, 1, N)[:, None]
    dirt = value_noise(r, 6) * (0.4 + 0.6 * y)
    if streaks:
        cols = r.random(N) < 0.18
        drip = np.zeros((N, N))
        for x in np.where(cols)[0]:
            start = r.integers(0, N // 2)
            drip[start:, x] = np.linspace(0.8, 0.1, N - start)
        dirt = dirt + drip * 0.6
    shade = 1 - strength * np.clip(dirt, 0, 1)
    tint = np.array([0.95, 0.9, 0.82])  # brownish dirt
    return img * shade[..., None] * (tint * (1 - shade[..., None]) + shade[..., None])


def rust(img, r, amount=0.08, seed_scale=5):
    m = value_noise(r, seed_scale) * r.random((N, N)) ** 0.5
    mask = m > np.quantile(m, 1 - amount)
    rc = np.stack([0.45 + 0.2 * r.random((N, N)), 0.22 + 0.1 * r.random((N, N)), 0.1 * np.ones((N, N))], -1)
    img = img.copy()
    img[mask] = img[mask] * 0.35 + rc[mask] * 0.65
    return img


def scratches(img, r, count=6, light=True):
    img = img.copy()
    for _ in range(count):
        x, y = r.integers(0, N, 2)
        dx, dy = r.choice([-1, 1]), r.choice([0, 1, -1])
        for i in range(r.integers(3, 9)):
            xi, yi = (x + i * dx) % N, (y + (i * dy) // 2) % N
            img[yi, xi] = np.clip(img[yi, xi] * (1.35 if light else 0.6), 0, 1)
    return img


def edge_darken(img, width=1, k=0.6):
    img = img.copy()
    img[:width, :] *= k
    img[-width:, :] *= k
    img[:, :width] *= k
    img[:, -width:] *= k
    return img


def rect(img, x0, y0, x1, y1, color, alpha=1.0):
    c = np.array(color, np.float32) / 255.0
    img[y0:y1, x0:x1] = img[y0:y1, x0:x1] * (1 - alpha) + c * alpha
    return img


def rivets(img, pts):
    for x, y in pts:
        img[y, x] = np.clip(img[y, x] * 1.5, 0, 1)
        img[min(y + 1, N - 1), x] *= 0.5
    return img


def circle(img, cx, cy, rad, color, ring=None):
    yy, xx = np.mgrid[:N, :N]
    d = np.sqrt((xx - cx + 0.5) ** 2 + (yy - cy + 0.5) ** 2)
    img[d <= rad] = np.array(color, np.float32) / 255.0
    if ring is not None:
        img[(d > rad - 0.8) & (d <= rad + 0.4)] = np.array(ring, np.float32) / 255.0
    return img


def glow(color, r, core=1.0):
    """Bright face for lights and lit tubes: a hot centre with slight noise."""
    yy, xx = np.mgrid[:N, :N]
    d = np.sqrt((xx - N / 2 + 0.5) ** 2 + (yy - N / 2 + 0.5) ** 2) / (N / 2)
    c = np.array(color, np.float32) / 255.0
    img = c[None, None, :] * (0.75 + 0.25 * (1 - d))[..., None] * core
    img = img + (1 - d)[..., None] * 0.25
    img = img * (1 + (r.random((N, N, 1)) - 0.5) * 0.05)
    return np.clip(img, 0, 1)


def tex_lamp_housing():
    r = rng(101)
    img = base((178, 180, 172), r, var=0.06)
    img = grime(img, r, 0.3, streaks=False)
    img = rust(img, r, 0.04)
    img = scratches(img, r, 4)
    # Panel seams every 8 px across the long side.
    for x in (0, 15, 16, 31):
        img[:, x] *= 0.7
    img = rivets(img, [(3, 3), (28, 3), (3, 28), (28, 28)])
    return edge_darken(img, 1, 0.7)


def tex_lamp_tube_on():
    r = rng(102)
    img = np.ones((N, N, 3), np.float32)
    y = np.abs(np.linspace(-1, 1, N))[:, None]
    tone = np.array([0.93, 0.97, 1.0])
    img = img * (1 - 0.18 * y ** 2)[..., None] * tone
    img = img * (1 + (r.random((N, N, 1)) - 0.5) * 0.03)
    # Darker metal end caps.
    img[:, :2] = np.array([0.55, 0.55, 0.52])
    img[:, -2:] = np.array([0.55, 0.55, 0.52])
    return np.clip(img, 0, 1)


def tex_lamp_tube_off():
    r = rng(103)
    img = base((92, 96, 98), r, var=0.05, grain=0.04)
    y = np.abs(np.linspace(-1, 1, N))[:, None]
    img = img * (1 - 0.25 * y ** 2)[..., None]
    # Dust specks and a blackened end, as on a worn-out tube.
    img = grime(img, r, 0.25, streaks=False)
    img[:, :2] = np.array([0.4, 0.4, 0.38])
    img[:, -2:] = np.array([0.4, 0.4, 0.38])
    img[:, 2:6] *= 0.55
    img[10:22, 14] *= 1.25  # highlight streak
    return np.clip(img, 0, 1)


def tex_breaker_box():
    r = rng(201)
    img = base((92, 104, 92), r, var=0.07)
    img = grime(img, r, 0.4)
    img = rust(img, r, 0.06)
    img = scratches(img, r, 7)
    return edge_darken(img, 1, 0.65)


def tex_breaker_door():
    r = rng(202)
    img = tex_breaker_box()
    # Inset door panel.
    img[2:30, 2] *= 0.6
    img[2:30, 29] *= 1.25
    img[2, 2:30] *= 0.6
    img[29, 2:30] *= 1.25
    # Yellow-and-black warning plate at the top with a lightning bolt.
    # Door rows map to model height y = 13.5 - row / 32 * 11; the lever plate covers rows 13..28.
    img = rect(img, 9, 2, 23, 8, (214, 170, 30))
    for x in range(9, 23):
        if (x // 2) % 2 == 0:
            img[2, x] = img[7, x] = np.array([0.08, 0.08, 0.08])
    bolt = [(16, 3), (15, 4), (14, 5), (15, 5), (16, 5), (17, 5), (16, 6)]
    for x, y in bolt:
        img[y, x] = np.array([0.08, 0.08, 0.08])
    img = rect(img, 9, 2, 23, 8, (60, 50, 20), alpha=0.15)
    # Key lock on the right edge.
    img = rect(img, 24, 15, 26, 18, (40, 40, 40))
    img = rivets(img, [(4, 4), (27, 4), (4, 27), (27, 27)])
    img = grime(img, r, 0.2)
    return np.clip(img, 0, 1)


def tex_handle():
    r = rng(203)
    img = base((150, 30, 26), r, var=0.08)
    img = scratches(img, r, 5)
    img[:, :4] *= 0.75
    return edge_darken(img, 1, 0.6)


def tex_metal_dark():
    r = rng(301)
    img = base((58, 60, 62), r, var=0.08, grain=0.06)
    img = rust(img, r, 0.1)
    img = scratches(img, r, 8)
    return edge_darken(img, 1, 0.7)


def tex_light(color, seed):
    return glow(color, rng(seed))


def tex_light_off():
    r = rng(310)
    img = glow((70, 60, 55), r, core=0.6)
    return img * 0.6


def tex_gen_body():
    r = rng(401)
    img = base((196, 150, 38), r, var=0.08)  # worn yellow industrial paint
    # Chipped paint: dark metal shows through.
    chip = value_noise(r, 4) * r.random((N, N))
    img[chip > np.quantile(chip, 0.9)] = np.array([0.22, 0.2, 0.18])
    img = grime(img, r, 0.45)
    img = rust(img, r, 0.05)
    img = scratches(img, r, 8)
    # Oil stain.
    yy, xx = np.mgrid[:N, :N]
    stain = np.exp(-(((xx - 22) / 5) ** 2 + ((yy - 24) / 4) ** 2))
    img *= (1 - 0.5 * stain)[..., None]
    return edge_darken(np.clip(img, 0, 1), 1, 0.6)


def tex_gen_tank():
    img = tex_gen_body()
    # Pressed seams and a filler label.
    img[8, :] *= 0.65
    img[23, :] *= 0.65
    img = rect(img, 4, 12, 13, 19, (30, 30, 30))
    img = rect(img, 5, 14, 12, 15, (200, 200, 190))
    img = rect(img, 5, 16, 10, 17, (200, 200, 190))
    return img


def tex_gen_vent():
    r = rng(402)
    img = tex_metal_dark()
    for y in range(3, 29, 3):
        img = rect(img, 3, y, 29, y + 1, (12, 12, 12))
        img[y + 1, 3:29] = np.clip(img[y + 1, 3:29] * 1.4, 0, 1)
    return img


def tex_gen_panel(on):
    r = rng(403)
    img = base((32, 34, 36), r, var=0.05)
    img = scratches(img, r, 5)
    img = edge_darken(img, 2, 0.55)
    # Two dials.
    for cx in (9, 22):
        img = circle(img, cx, 11, 5, (205, 200, 180) if on else (120, 118, 108), ring=(15, 15, 15))
        ang = -0.6 if on else 2.3
        for i in range(5):
            x = int(round(cx + np.cos(ang) * i * 0.9 - 0.5))
            y = int(round(11 - np.sin(ang) * i * 0.9 - 0.5))
            img[y, x] = np.array([0.7, 0.1, 0.08])
    # Toggle switches and a label strip.
    for x in (6, 12, 18):
        img = rect(img, x, 20, x + 3, 25, (70, 70, 72))
        img = rect(img, x + 1, 19 if on else 23, x + 2, 22 if on else 26, (190, 190, 190))
    img = rect(img, 5, 27, 27, 29, (150, 140, 110))
    if on:
        img = circle(img, 26, 22, 2, (120, 255, 120))
    else:
        img = circle(img, 26, 22, 2, (40, 55, 40))
    return np.clip(img, 0, 1)


def tex_exhaust():
    r = rng(404)
    img = base((70, 52, 40), r, var=0.12)
    img = rust(img, r, 0.3)
    img = grime(img, r, 0.5)
    # Soot.
    img[:6] *= 0.4
    return edge_darken(img, 1, 0.6)


TEXTURES = {
    "lamp_housing": tex_lamp_housing,
    "lamp_tube_on": tex_lamp_tube_on,
    "lamp_tube_off": tex_lamp_tube_off,
    "breaker_box": tex_breaker_box,
    "breaker_door": tex_breaker_door,
    "breaker_handle": tex_handle,
    "metal_dark": tex_metal_dark,
    "light_green": lambda: tex_light((90, 255, 110), 311),
    "light_red": lambda: tex_light((255, 60, 45), 312),
    "light_off": tex_light_off,
    "generator_body": tex_gen_body,
    "generator_tank": tex_gen_tank,
    "generator_vent": tex_gen_vent,
    "generator_panel_on": lambda: tex_gen_panel(True),
    "generator_panel_off": lambda: tex_gen_panel(False),
    "generator_exhaust": tex_exhaust,
}


# ------------------------------------------------------------------ models

FACES = ("north", "south", "east", "west", "up", "down")


def default_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]


def el(name, f, t, tex, full=(), skip=(), cull=None, emissive=False, **face_tex):
    """One cuboid. `tex` is a texture key; face_tex can override it per face.
    Faces listed in `full` use the whole texture (0..16) instead of the default UVs."""
    faces = {}
    for face in FACES:
        if face in skip:
            continue
        uv = [0, 0, 16, 16] if face in full else default_uv(face, f, t)
        uv = [round(v, 3) for v in uv]
        entry = {"uv": uv, "texture": "#" + face_tex.get(face, tex)}
        if cull and face in cull:
            entry["cullface"] = face
        faces[face] = entry
    e = {"name": name, "from": list(f), "to": list(t), "faces": faces}
    if emissive:
        # Forge 1.20.1: full block and sky light on this element, so it looks lit even in the dark.
        e["forge_data"] = {"block_light": 15, "sky_light": 15}
        e["shade"] = False
    return e


def display(gui_rot, gui_tr, scale=0.625):
    return {
        "gui": {"rotation": gui_rot, "translation": gui_tr, "scale": [scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.75] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
    }


def centre_translation(rot_deg, scale, centre):
    """GUI translation (px) that puts the model's visual centre in the middle of the slot.
    Minecraft applies translate, then rotate XYZ, then scale, around the block centre (8,8,8)."""
    ax, ay, az = np.radians(rot_deg)
    Rx = np.array([[1, 0, 0], [0, np.cos(ax), -np.sin(ax)], [0, np.sin(ax), np.cos(ax)]])
    Ry = np.array([[np.cos(ay), 0, np.sin(ay)], [0, 1, 0], [-np.sin(ay), 0, np.cos(ay)]])
    Rz = np.array([[np.cos(az), -np.sin(az), 0], [np.sin(az), np.cos(az), 0], [0, 0, 1]])
    off = np.array(centre, float) - 8.0
    v = Rx @ Ry @ Rz @ off * scale
    return [round(float(-v[0]), 2), round(float(-v[1]), 2), 0.0]


def model(textures, elements, disp, ao=True):
    m = {"credit": "Bodycam Horror, generated by tools/assets/make_blocks.py",
         "parent": "minecraft:block/block",
         "ambientocclusion": ao,
         "render_type": "minecraft:solid",
         "textures": {k: NS + v for k, v in textures.items()},
         "elements": elements,
         "display": disp}
    return m


def lamp(lit):
    tube = "tube"
    els = [
        el("housing", (1, 15, 5), (15, 16, 11), "housing", cull=("up",)),
        el("end cap west", (1, 14, 5.5), (2, 15, 10.5), "housing"),
        el("end cap east", (14, 14, 5.5), (15, 15, 10.5), "housing"),
        el("reflector", (2, 14.75, 7.5), (14, 15, 8.5), "housing", skip=("up",)),
        el("tube north", (2, 14, 6), (14, 15, 7.5), tube, skip=("up", "east", "west"), emissive=lit),
        el("tube south", (2, 14, 8.5), (14, 15, 10), tube, skip=("up", "east", "west"), emissive=lit),
        el("mount", (7, 15.5, 5), (9, 16, 11), "housing", skip=("up",)),
    ]
    tex = {"particle": "lamp_housing", "housing": "lamp_housing", tube: "lamp_tube_on" if lit else "lamp_tube_off"}
    rot = [-30, 225, 0]
    return model(tex, els, display(rot, centre_translation(rot, 0.625, (8, 15, 8))), ao=False)


def breaker(on):
    els = [
        el("back box", (3, 2, 14.5), (13, 14, 16), "box", cull=("south",)),
        el("door", (3.5, 2.5, 13.75), (12.5, 13.5, 14.5), "box", full=("north",), north="door"),
        el("hinge top", (3, 11, 13.5), (4, 12.5, 14.5), "metal"),
        el("hinge bottom", (3, 3.5, 13.5), (4, 5, 14.5), "metal"),
        el("lever plate", (6.5, 4, 13.25), (9.5, 9, 13.75), "metal"),
        el("light housing", (10, 10.5, 13.25), (12, 12.5, 13.75), "metal"),
        el("light", (10.5, 11, 13), (11.5, 12, 13.25), "light", full=FACES, emissive=True),
        el("cable gland", (7, 13.5, 14.75), (9, 14, 15.75), "metal"),
    ]
    if on:
        els += [el("lever arm", (7.5, 6.5, 13), (8.5, 10, 13.25), "handle"),
                el("lever grip", (7, 9.5, 13), (9, 10.75, 13.25), "handle")]
    else:
        els += [el("lever arm", (7.5, 3, 13), (8.5, 6.5, 13.25), "handle"),
                el("lever grip", (7, 2.25, 13), (9, 3.5, 13.25), "handle")]
    tex = {"particle": "breaker_box", "box": "breaker_box", "door": "breaker_door", "metal": "metal_dark",
           "handle": "breaker_handle", "light": "light_green" if on else "light_red"}
    rot = [30, 225, 0]
    return model(tex, els, display(rot, centre_translation(rot, 0.625, (8, 8, 14.5))))


def generator(on):
    els = [
        el("skid", (1, 0, 1), (15, 1.5, 15), "metal", cull=("down",)),
        el("post nw", (1, 1.5, 1), (2, 13, 2), "metal"),
        el("post ne", (14, 1.5, 1), (15, 13, 2), "metal"),
        el("post sw", (1, 1.5, 14), (2, 13, 15), "metal"),
        el("post se", (14, 1.5, 14), (15, 13, 15), "metal"),
        el("rail north", (1, 13, 1), (15, 14, 2), "metal"),
        el("rail south", (1, 13, 14), (15, 14, 15), "metal"),
        el("engine block", (3, 1.5, 3), (13, 10, 13), "body", east="vent", west="vent", south="vent"),
        el("fuel tank", (2.5, 10, 3.5), (13.5, 13, 12.5), "tank", full=("up",)),
        el("tank cap", (9, 13, 6), (11, 14, 8), "metal"),
        el("control panel", (4, 3, 2), (12, 9, 3), "metal", full=("north",), north="panel"),
        el("indicator", (10.5, 9.25, 2.25), (11.5, 10, 3), "light", full=FACES, emissive=on),
        el("muffler", (13, 5.5, 5), (14.5, 8.5, 11), "exhaust"),
        el("exhaust pipe", (13.25, 8.5, 9), (14.25, 13.5, 10), "exhaust"),
        el("recoil starter", (1.75, 4, 6), (3, 8, 10), "body"),
        el("starter handle", (1.25, 5.5, 7.5), (1.75, 6.5, 8.5), "metal"),
    ]
    tex = {"particle": "generator_body", "body": "generator_body", "tank": "generator_tank",
           "vent": "generator_vent", "metal": "metal_dark", "exhaust": "generator_exhaust",
           "panel": "generator_panel_on" if on else "generator_panel_off",
           "light": "light_green" if on else "light_off"}
    rot = [30, 225, 0]
    return model(tex, els, display(rot, centre_translation(rot, 0.625, (8, 7, 8))))


MODELS = {
    "lamp": lambda: lamp(True), "lamp_off": lambda: lamp(False),
    "breaker": lambda: breaker(True), "breaker_off": lambda: breaker(False),
    "generator": lambda: generator(True), "generator_off": lambda: generator(False),
}

BOUNDS = {"lamp": ((1, 14, 5), (15, 16, 11)), "breaker": ((3, 2, 13), (13, 14, 16)),
          "generator": ((1, 0, 1), (15, 14, 15))}


def check(name, m, tex_dir):
    bmin, bmax = BOUNDS[name.replace("_off", "")]
    assert len(m["elements"]) <= 25, f"{name}: too many elements"
    for e in m["elements"]:
        for i in range(3):
            assert bmin[i] - 1e-6 <= e["from"][i] < e["to"][i] <= bmax[i] + 1e-6, f"{name}/{e['name']} out of bounds"
        for face in e["faces"].values():
            key = face["texture"][1:]
            assert key in m["textures"], f"{name}: unknown texture #{key}"
    for v in m["textures"].values():
        assert (tex_dir / (v.split("/")[-1] + ".png")).exists(), f"{name}: missing texture {v}"


def main():
    root = Path(sys.argv[1]).resolve()
    assets = root / "src/main/resources/assets/bodycamhorror"
    tex_dir = assets / "textures/block"
    tex_dir.mkdir(parents=True, exist_ok=True)
    for name, fn in TEXTURES.items():
        img = (np.clip(fn(), 0, 1) * 255).round().astype(np.uint8)
        Image.fromarray(img, "RGB").save(tex_dir / f"{name}.png", optimize=True)
        print("texture", name)
    mdir = assets / "models/block"
    for name, fn in MODELS.items():
        m = fn()
        check(name, m, tex_dir)
        (mdir / f"{name}.json").write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
        print(f"model {name}: {len(m['elements'])} elements")


if __name__ == "__main__":
    main()
