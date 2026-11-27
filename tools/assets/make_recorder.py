"""Lobby tape recorder (bodycamhorror:tape_recorder): 32x32 textures, block models and blockstate.

Usage: python -I tools/assets/make_recorder.py <repo root>
An old portable cassette deck: dark plastic body, a worn aluminium front with a speaker grille,
a cassette window and piano keys on top, a carry handle. Front faces NORTH in the models.
The "on" front has a dim amber VU meter; the "off" one is dark.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_blocks import N, base, edge_darken, grime, rect, rng, scratches  # noqa: E402

NS = "bodycamhorror:block/"
PLASTIC = (40, 38, 35)
ALU = (128, 124, 112)


def front(on):
    r = rng(950)
    img = base(PLASTIC, r, 0.06, 0.05)
    # Aluminium faceplate over the middle band (the model's front face is 10 px tall: rows 6..26 here).
    rect(img, 1, 7, 31, 25, ALU)
    img[7:25, 1:31] = np.clip(img[7:25, 1:31] * (1 + (r.random((18, 30, 1)) - 0.5) * 0.12), 0, 1)
    # Speaker grille, left: rows of holes.
    for y in range(9, 23, 2):
        for x in range(3, 15, 2):
            img[y, x] = np.array([0.08, 0.08, 0.07])
    # VU meter window, right.
    rect(img, 18, 9, 29, 16, (22, 20, 18))
    if on:
        rect(img, 19, 10, 28, 15, (150, 104, 40))
        img[10:15, 19:28] *= np.linspace(0.75, 1.0, 9)[None, :, None]
        img[11:15, 23] = np.array([0.12, 0.08, 0.05])  # needle
        img[10, 24] = np.array([0.12, 0.08, 0.05])
    else:
        rect(img, 19, 10, 28, 15, (58, 52, 44))
        img[11:15, 21] = np.array([0.1, 0.1, 0.09])
    # Worn bone label strip under the meter, its words long gone.
    rect(img, 18, 18, 29, 21, (168, 160, 140))
    rect(img, 19, 19, 25, 20, (120, 112, 98), 0.6)
    # Little record lamp.
    img[23, 27] = np.array([0.48, 0.09, 0.07]) if on else np.array([0.2, 0.08, 0.07])
    img = scratches(img, r, 8)
    img = grime(img, r, 0.25, streaks=False)
    return edge_darken(img, 1, 0.6)


def tex_top():
    r = rng(951)
    img = base(PLASTIC, r, 0.06, 0.05)
    # Cassette window: smoked plastic with two reels behind it.
    rect(img, 7, 8, 25, 20, (18, 17, 16))
    rect(img, 8, 9, 24, 19, (52, 48, 42))
    for cx in (12, 20):
        rect(img, cx - 2, 12, cx + 2, 16, (150, 142, 124))
        img[13:15, cx - 1:cx + 1] = np.array([0.1, 0.1, 0.09])
    rect(img, 13, 13, 19, 15, (44, 34, 26))  # tape between the reels
    # Piano keys along the front edge (rows at the bottom of the texture = front of the model).
    for i, x in enumerate(range(4, 28, 4)):
        col = (122, 30, 22) if i == 0 else (150, 144, 128)
        rect(img, x, 24, x + 3, 30, col)
        rect(img, x, 29, x + 3, 30, (30, 28, 25))
    img = scratches(img, r, 5)
    dust = r.random((N, N)) < 0.15
    img[dust] = img[dust] * 0.65 + np.array([0.5, 0.48, 0.42]) * 0.35
    return edge_darken(img, 1, 0.55)


def tex_side():
    r = rng(952)
    img = base(PLASTIC, r, 0.07, 0.05)
    rect(img, 0, 15, 32, 17, (60, 58, 52))  # seam between halves
    img = scratches(img, r, 4)
    return edge_darken(img, 1, 0.6)


def tex_handle():
    r = rng(953)
    return edge_darken(base((110, 106, 96), r, 0.08, 0.05), 1, 0.55)


TEXTURES = {
    "recorder_front_on": lambda: front(True),
    "recorder_front_off": lambda: front(False),
    "recorder_top": tex_top,
    "recorder_side": tex_side,
    "recorder_handle": tex_handle,
}


def face(tex, uv, cull=None):
    f = {"texture": tex, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def model(on):
    tex = {"particle": NS + "recorder_side", "front": NS + ("recorder_front_on" if on else "recorder_front_off"),
           "top": NS + "recorder_top", "side": NS + "recorder_side", "handle": NS + "recorder_handle"}
    body = {"from": [1, 0, 5], "to": [15, 5, 11], "faces": {
        "north": face("#front", [0, 3, 16, 13]),
        "south": face("#side", [0, 3, 16, 13]),
        "east": face("#side", [0, 3, 8, 13]),
        "west": face("#side", [0, 3, 8, 13]),
        "up": face("#top", [0, 0, 16, 16]),
        "down": face("#side", [0, 0, 16, 16], "down")}}
    els = [body]
    for x0 in (2, 13):  # handle posts
        els.append({"from": [x0, 5, 7.5], "to": [x0 + 1, 7, 8.5], "faces": {
            k: face("#handle", [0, 0, 1, 2]) for k in ("north", "south", "east", "west", "up")}})
    els.append({"from": [2, 7, 7.5], "to": [14, 8, 8.5], "faces": {
        k: face("#handle", [0, 0, 12, 1]) for k in ("north", "south", "east", "west", "up", "down")}})
    return {"parent": "block/block", "textures": tex, "elements": els,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]}}}


def main():
    root = Path(sys.argv[1]).resolve()
    assets = root / "src/main/resources/assets/bodycamhorror"
    for name, fn in TEXTURES.items():
        img = (np.clip(fn(), 0, 1) * 255).round().astype(np.uint8)
        Image.fromarray(img, "RGB").save(assets / "textures/block" / f"{name}.png", optimize=True)
        print("texture", name)
    for on in (False, True):
        name = "tape_recorder" + ("_on" if on else "")
        (assets / "models/block" / f"{name}.json").write_text(json.dumps(model(on), indent=2) + "\n", encoding="utf-8")
        print("model", name)
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, y in rot.items():
        for playing in ("false", "true"):
            v = {"model": NS + "tape_recorder" + ("_on" if playing == "true" else "")}
            if y:
                v["y"] = y
            variants[f"facing={f},playing={playing}"] = v
    (assets / "blockstates/tape_recorder.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")
    (assets / "models/item/tape_recorder.json").write_text(json.dumps({"parent": NS + "tape_recorder"}, indent=2) + "\n", encoding="utf-8")
    print("blockstate, item model")


if __name__ == "__main__":
    main()
