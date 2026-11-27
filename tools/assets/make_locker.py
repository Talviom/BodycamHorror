"""Lobby locker (bodycamhorror:locker): 32x32 textures, block models and blockstate.

Usage: python -I tools/assets/make_locker.py <repo root>
A tall painted-metal locker, two blocks high (half=lower/upper), front faces NORTH in the models.
Open models show the dark inside and the door swung out on the viewer's left (hinge at x=16).
Worn olive-grey paint, grime low down, rust at the bottom edge: the dark found-footage palette.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_blocks import N, base, edge_darken, grime, rect, rng, rust, scratches  # noqa: E402

NS = "bodycamhorror:block/"
PAINT = (98, 103, 90)  # faded olive-grey enamel
DARK = (34, 33, 30)


def door_frame(img):
    """Door panel edges: a dark gap all round and a lighter pressed rim inside it."""
    img[:, :1] *= 0.35
    img[:, -1:] *= 0.35
    img[:, 1:2] *= 1.12
    img[:, -2:-1] *= 0.8
    return img


def tex_front_lower():
    r = rng(901)
    img = base(PAINT, r, 0.06, 0.05)
    img = door_frame(img)
    img[-1:, :] *= 0.35
    # Latch: a recessed cup with a lever, on the free edge (texture right = model x 0..3).
    rect(img, 24, 2, 29, 13, DARK)
    rect(img, 25, 3, 28, 12, (58, 58, 52))
    rect(img, 26, 4, 27, 11, (128, 124, 110))
    img[11, 26] *= 0.6
    # Padlock eye below the latch.
    rect(img, 25, 14, 28, 16, (70, 66, 58))
    img[14:16, 26] *= 0.4
    img = scratches(img, r, 9)
    img = grime(img, r, 0.45)
    img = rust(img, r, 0.05)
    # Kick marks and rust along the bottom.
    rect(img, 3, 27, 14, 30, (74, 60, 46), 0.45)
    rect(img, 1, 29, 31, 31, (82, 52, 34), 0.5)
    return img


def tex_front_upper():
    r = rng(902)
    img = base(PAINT, r, 0.06, 0.05)
    img = door_frame(img)
    img[:1, :] *= 0.35
    # Four vent slots near the top, each with a lit lip underneath.
    for y in (4, 7, 10, 13):
        rect(img, 7, y, 25, y + 2, (16, 15, 14))
        rect(img, 7, y + 2, 25, y + 3, (132, 134, 120), 0.5)
    # Number holder: a small bone-coloured card, the number long faded.
    rect(img, 11, 18, 21, 23, (60, 58, 52))
    rect(img, 12, 19, 20, 22, (176, 168, 148))
    rect(img, 13, 20, 16, 21, (120, 112, 98), 0.6)
    img = scratches(img, r, 7)
    img = grime(img, r, 0.25)
    img = rust(img, r, 0.03)
    return img


def tex_side():
    r = rng(903)
    img = base(PAINT, r, 0.07, 0.05)
    img[:, 15:16] *= 0.75  # sheet seam
    img = scratches(img, r, 5)
    img = grime(img, r, 0.4)
    img = rust(img, r, 0.04)
    return edge_darken(img, 1, 0.7)


def tex_top():
    r = rng(904)
    img = base((84, 86, 76), r, 0.05, 0.05)
    # Dust settles on top.
    dust = r.random((N, N)) < 0.22
    img[dust] = img[dust] * 0.6 + np.array([0.55, 0.52, 0.46]) * 0.4
    return edge_darken(img, 1, 0.6)


def tex_inside():
    r = rng(905)
    img = base(DARK, r, 0.1, 0.06)
    y = np.linspace(0.7, 1.1, N)[:, None, None]
    img = img * y
    # Shelf lip near the top.
    rect(img, 1, 6, 31, 8, (70, 70, 62))
    rect(img, 1, 8, 31, 9, (12, 12, 11))
    return edge_darken(img, 2, 0.5)


def tex_door_back():
    r = rng(906)
    img = base((88, 92, 80), r, 0.05, 0.05)
    # Bare inside of the door: a stiffening bar and a pale rectangle where a sticker was torn off.
    rect(img, 13, 0, 19, 32, (74, 78, 68))
    rect(img, 4, 8, 11, 15, (118, 116, 100), 0.4)
    img = scratches(img, r, 6)
    img = grime(img, r, 0.3)
    return edge_darken(img, 1, 0.6)


def tex_handle():
    r = rng(907)
    img = base((132, 128, 114), r, 0.08, 0.05)
    return edge_darken(img, 1, 0.55)


TEXTURES = {
    "locker_front_lower": tex_front_lower,
    "locker_front_upper": tex_front_upper,
    "locker_side": tex_side,
    "locker_top": tex_top,
    "locker_inside": tex_inside,
    "locker_door_back": tex_door_back,
    "locker_handle": tex_handle,
}


# ------------------------------------------------------------------ models

def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv is not None:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def body(front, upper, front_tex):
    """Whole cabinet; the front face is either the door or the dark inside."""
    faces = {
        "north": face(front_tex, [0, 0, 16, 16]),
        "south": face("#side", [0, 0, 16, 16], "south"),
        "east": face("#side", [0, 0, 16, 16], "east"),
        "west": face("#side", [0, 0, 16, 16], "west"),
    }
    if upper:
        faces["up"] = face("#top", [0, 0, 16, 16], "up")
    else:
        faces["down"] = face("#top", [0, 0, 16, 16], "down")
    return {"from": [0, 0, front], "to": [16, 16, 16], "faces": faces}


def box(frm, to, tex="#side"):
    sx, sy, sz = (to[i] - frm[i] for i in range(3))
    return {"from": frm, "to": to, "faces": {
        "north": face(tex, [0, 0, sx, sy]), "south": face(tex, [0, 0, sx, sy]),
        "east": face(tex, [0, 0, sz, sy]), "west": face(tex, [0, 0, sz, sy]),
        "up": face(tex, [0, 0, sx, sz]), "down": face(tex, [0, 0, sx, sz])}}


def model(upper, open_):
    half = "upper" if upper else "lower"
    tex = {"particle": NS + "locker_side", "side": NS + "locker_side", "top": NS + "locker_top",
           "front": NS + f"locker_front_{half}", "inside": NS + "locker_inside",
           "back": NS + "locker_door_back", "handle": NS + "locker_handle"}
    els = []
    if not open_:
        els.append(body(0, upper, "#front"))
        if not upper:
            els.append(box([2.5, 9, -0.6], [3.5, 14, 0], "#handle"))
    else:
        # Recessed inside behind a thin frame; the door stands open, hinged on the viewer's left.
        els.append(body(1, upper, "#inside"))
        els.append(box([0, 0, 0], [1, 16, 1]))
        els.append(box([15, 0, 0], [16, 16, 1]))
        els.append(box([1, 15, 0] if upper else [1, 0, 0], [15, 16, 1] if upper else [15, 1, 1]))
        door = {"from": [15, 0, -15], "to": [16, 16, 0], "faces": {
            "east": face("#front", [0, 0, 16, 16]),
            "west": face("#back", [0, 0, 16, 16]),
            "north": face("#side", [0, 0, 1, 16]),
            "south": face("#side", [0, 0, 1, 16]),
            "up": face("#side", [0, 0, 1, 15]),
            "down": face("#side", [0, 0, 1, 15]),
        }}
        els.append(door)
        if not upper:
            els.append(box([16, 9, -14], [16.6, 14, -13], "#handle"))
    return {"parent": "block/block", "textures": tex, "elements": els}


def blockstate():
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rot.items():
        for half in ("lower", "upper"):
            for open_ in ("false", "true"):
                name = f"locker_{half}" + ("_open" if open_ == "true" else "")
                v = {"model": NS + name}
                if y:
                    v["y"] = y
                variants[f"facing={facing},half={half},open={open_}"] = v
    return {"variants": variants}


def main():
    root = Path(sys.argv[1]).resolve()
    assets = root / "src/main/resources/assets/bodycamhorror"
    tex_dir = assets / "textures/block"
    for name, fn in TEXTURES.items():
        img = (np.clip(fn(), 0, 1) * 255).round().astype(np.uint8)
        Image.fromarray(img, "RGB").save(tex_dir / f"{name}.png", optimize=True)
        print("texture", name)
    mdir = assets / "models/block"
    for upper in (False, True):
        for open_ in (False, True):
            name = "locker_" + ("upper" if upper else "lower") + ("_open" if open_ else "")
            (mdir / f"{name}.json").write_text(json.dumps(model(upper, open_), indent=2) + "\n", encoding="utf-8")
            print("model", name)
    (assets / "blockstates/locker.json").write_text(json.dumps(blockstate(), indent=2) + "\n", encoding="utf-8")
    (assets / "models/item/locker.json").write_text(json.dumps({"parent": NS + "locker_lower"}, indent=2) + "\n", encoding="utf-8")
    print("blockstate, item model")


if __name__ == "__main__":
    main()
