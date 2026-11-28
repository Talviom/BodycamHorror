"""Lobby gramophone (block id stays bodycamhorror:tape_recorder): 32x32 textures, block models and blockstate.

Usage: python -I tools/assets/make_gramophone.py <repo root>
An old horn gramophone (просьба пользователя 2026-11-28: не магнитофон, а живой граммофон с трубой):
a dark walnut case with a dull brass trim, wine felt on the turntable, a brass tone arm, a crank on the
east side and a big tarnished brass horn rising from the back over the record and opening to the front.
The front (horn mouth) faces NORTH in the models. The record itself is not in the model: it is drawn and
spun by client/GramophoneRenderer (texture gramophone_record.png, RGBA, round).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_blocks import N, base, edge_darken, grime, rect, rng, scratches  # noqa: E402

NS = "bodycamhorror:block/"
WALNUT = (62, 40, 26)
BRASS = (124, 98, 56)  # tarnished, not shiny


def tex_wood():
    r = rng(960)
    img = base(WALNUT, r, 0.05, 0.05)
    # Grain: slow horizontal waves of darker and lighter streaks.
    yy, xx = np.mgrid[0:N, 0:N]
    wave = np.sin(yy * 0.9 + np.sin(xx * 0.21 + r.uniform(0, 6)) * 2.2)
    img = img * (1 + 0.09 * wave)[..., None]
    # Brass trim along the top edge and the corners.
    rect(img, 0, 0, 32, 2, BRASS)
    rect(img, 0, 0, 2, 32, BRASS, 0.5)
    rect(img, 30, 0, 32, 32, BRASS, 0.5)
    img = scratches(img, r, 6)
    img = grime(img, r, 0.3, streaks=False)
    return edge_darken(img, 1, 0.6)


def tex_wood_top():
    r = rng(961)
    img = base(WALNUT, r, 0.05, 0.05)
    yy, xx = np.mgrid[0:N, 0:N]
    img = img * (1 + 0.08 * np.sin(xx * 0.8 + np.sin(yy * 0.2) * 2.0))[..., None]
    dust = r.random((N, N)) < 0.12
    img[dust] = img[dust] * 0.7 + np.array([0.45, 0.42, 0.36]) * 0.3
    return edge_darken(img, 1, 0.55)


def tex_brass():
    r = rng(962)
    img = base(BRASS, r, 0.08, 0.06)
    # Patina in the dents, darker bands like hammered seams.
    for y in range(3, N, 7):
        img[y:y + 1] *= 0.78
    green = r.random((N, N)) < 0.08
    img[green] = img[green] * 0.6 + np.array([0.28, 0.34, 0.26]) * 0.4
    img = grime(img, r, 0.35, streaks=False)
    return edge_darken(img, 1, 0.7)


def tex_horn_inside():
    """Mouth of the horn seen from the front: dark rings going into the throat."""
    r = rng(963)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 15.5, yy - 15.5) / 16
    img = base(BRASS, r, 0.06, 0.05) * (0.15 + 0.75 * np.clip(d, 0, 1) ** 1.6)[..., None]
    img = img * (1 + 0.12 * np.sin(d * 28))[..., None]
    # Bright-ish rolled rim right at the edge.
    rim = (xx < 2) | (xx > 29) | (yy < 2) | (yy > 29)
    img[rim] = np.array(BRASS) / 255 * 1.05
    return edge_darken(img, 1, 0.6)


def tex_felt():
    r = rng(964)
    img = base((70, 26, 22), r, 0.1, 0.06)
    return edge_darken(img, 2, 0.5)


def tex_record():
    """RGBA: worn black shellac disc with grooves, a faded bone label with a dried-blood ring, spindle hole."""
    r = rng(965)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 15.5, yy - 15.5)
    img = np.zeros((N, N, 4))
    col = np.full((N, N, 3), 0.07)
    col *= (1 + 0.35 * (np.sin(d * 3.1) > 0.4))[..., None]  # grooves
    sheen = np.clip(1 - np.abs(np.arctan2(yy - 15.5, xx - 15.5) - 0.8) * 1.5, 0, 1)
    col += 0.06 * sheen[..., None] * (d > 7)[..., None]
    label = d < 6
    col[label] = np.array([168, 158, 136]) / 255 * (0.85 + 0.15 * r.random((label.sum(), 1)))
    ring = (d >= 4.3) & (d < 5.2)
    col[ring] = np.array([122, 22, 18]) / 255
    col[d < 1.2] = 0.03
    img[..., :3] = col
    img[..., 3] = (d < 15.8).astype(float)
    return img


TEXTURES = {
    "gramophone_wood": tex_wood,
    "gramophone_wood_top": tex_wood_top,
    "gramophone_brass": tex_brass,
    "gramophone_horn_inside": tex_horn_inside,
    "gramophone_felt": tex_felt,
}


def face(tex, uv, cull=None):
    f = {"texture": tex, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, tex, rotation=None, faces=("north", "south", "east", "west", "up", "down"), special=None):
    sx, sy, sz = (to[i] - frm[i] for i in range(3))
    uv = {"north": [0, 0, sx, sy], "south": [0, 0, sx, sy], "east": [0, 0, sz, sy], "west": [0, 0, sz, sy],
          "up": [0, 0, sx, sz], "down": [0, 0, sx, sz]}
    fs = {k: face(tex, [min(16, v) for v in uv[k]]) for k in faces}
    if special:
        fs.update(special)
    e = {"from": frm, "to": to, "faces": fs}
    if rotation:
        e["rotation"] = rotation
    return e


def model():
    tex = {"particle": NS + "gramophone_wood", "wood": NS + "gramophone_wood", "top": NS + "gramophone_wood_top",
           "brass": NS + "gramophone_brass", "mouth": NS + "gramophone_horn_inside", "felt": NS + "gramophone_felt"}
    els = [
        # Case on four short feet.
        box([2, 0.5, 2], [14, 5, 14], "#wood", special={"up": face("#top", [0, 0, 16, 16]),
                                                         "down": face("#top", [0, 0, 16, 16], "down")}),
        *[box([x, 0, z], [x + 1, 0.5, z + 1], "#brass") for x in (2.5, 12.5) for z in (2.5, 12.5)],
        # Turntable felt under the record (the record is drawn by GramophoneRenderer at y 5.3).
        box([3.5, 5, 3.5], [12.5, 5.2, 12.5], "#felt", faces=("north", "south", "east", "west", "up")),
        # Spindle.
        box([7.75, 5.2, 7.75], [8.25, 5.9, 8.25], "#brass"),
        # Tone arm: from the post at the back over the record, sound box at the end.
        box([7.5, 5, 12], [8.5, 7, 13], "#brass"),
        box([7.65, 6.3, 8.5], [8.35, 6.9, 12.2], "#brass"),
        box([7.1, 5.5, 7.6], [8.9, 6.9, 8.6], "#brass"),
        # Crank on the east side.
        box([14, 2.4, 7.6], [15.4, 2.9, 8.1], "#brass"),
        box([15.2, 2.4, 7.6], [15.7, 4.4, 8.1], "#brass"),
        # Horn tube rising from the tone arm post.
        box([7.4, 7, 12.1], [8.6, 12.5, 13.1], "#brass"),
    ]
    # Horn: stepped flare along -z, tilted up by 22.5 degrees around the top of the tube.
    rot = {"origin": [8, 12.5, 12.6], "axis": "x", "angle": 22.5}
    flare = [  # (half size, z from, z to): a smooth-ish cone of nine steps
        (0.6, 10.6, 12.6), (0.85, 9.4, 10.6), (1.15, 8.3, 9.4), (1.5, 7.2, 8.3), (1.9, 6.2, 7.2),
        (2.4, 5.2, 6.2), (2.95, 4.3, 5.2), (3.55, 3.4, 4.3), (4.2, 2.6, 3.4)]
    cy = 12.5
    for i, (h, z0, z1) in enumerate(flare):
        last = i == len(flare) - 1
        special = {"north": face("#mouth", [0, 0, 16, 16])} if last else None
        els.append(box([8 - h, cy - h, z0], [8 + h, cy + h, z1], "#brass", rotation=rot, special=special))
    return {"parent": "block/block", "textures": tex, "elements": els,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.55, 0.55, 0.55]},
                        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
                        "fixed": {"scale": [0.6, 0.6, 0.6]}}}


def main():
    root = Path(sys.argv[1]).resolve()
    assets = root / "src/main/resources/assets/bodycamhorror"
    for name, fn in TEXTURES.items():
        img = (np.clip(fn(), 0, 1) * 255).round().astype(np.uint8)
        Image.fromarray(img, "RGB").save(assets / "textures/block" / f"{name}.png", optimize=True)
        print("texture", name)
    rec = (np.clip(tex_record(), 0, 1) * 255).round().astype(np.uint8)
    Image.fromarray(rec, "RGBA").save(assets / "textures/block/gramophone_record.png", optimize=True)
    print("texture gramophone_record")
    m = json.dumps(model(), indent=2) + "\n"
    for name in ("tape_recorder", "tape_recorder_on"):  # the record spins in the renderer, the model is the same
        (assets / "models/block" / f"{name}.json").write_text(m, encoding="utf-8")
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
