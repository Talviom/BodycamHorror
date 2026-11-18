"""Монстр «Жилец» (THE TENANT): модель GeckoLib и текстура 64x64.

    python tools/assets/make_tenant.py [repo root]

Пишет:
  src/main/resources/assets/bodycamhorror/geo/tenant.geo.json
  src/main/resources/assets/bodycamhorror/textures/entity/tenant.png
Превью: D:\\Blender\\blender.exe -b --factory-startup --python tools/assets/preview_tenant.py -- <repo root>

Модель из параллелепипедов, единица = пиксель (16 = 1 блок), формат Bedrock geometry 1.12.0 (GeckoLib, Blockbench).
Перед = север (-z), как у всех мобов. Рост около 2,6 блока, сутулится, руки до колен, лица нет.
Палитра проекта: сажа 4E4A42 и темнее, кость C4BCAA, запёкшаяся кровь 7A1612 только на пальцах.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image

TEX = 64

# name, parent, pivot, rotation (deg), cubes [(origin, size, uv)]
# origin = минимальный угол куба в координатах модели, size = (w, h, d), uv = угол развёртки на текстуре
BONES = [
    ("root", None, (0, 0, 0), (0, 0, 0), []),
    ("leg_right", "root", (-2, 20, 0), (0, 0, 0), [((-3.5, 0, -1.5), (3, 20, 3), (0, 26))]),
    ("leg_left", "root", (2, 20, 0), (0, 0, 0), [((0.5, 0, -1.5), (3, 20, 3), (12, 26))]),
    ("hips", "root", (0, 20, 0), (0, 0, 0), [((-3.5, 20, -1.5), (7, 3, 3), (0, 18))]),
    ("rag", "hips", (0, 23, 1.5), (8, 0, 0), [((-3, 15, 1.5), (6, 8, 1), (24, 50))]),
    ("body", "hips", (0, 23, 0), (22, 0, 0), [((-3, 23, -1.5), (6, 14, 3), (0, 0))]),
    ("neck", "body", (0, 37, 0), (12, 0, 0), [((-1, 37, -1), (2, 4, 2), (46, 0))]),
    ("head", "neck", (0, 41, 0), (10, 0, -20), [((-3, 41, -3.5), (6, 8, 6), (22, 0))]),
    ("arm_right", "body", (-4, 35.5, 0), (-18, 0, 4), [((-5, 23, -1), (2, 13, 2), (24, 26))]),
    ("forearm_right", "arm_right", (-4, 23.5, 0), (-20, 0, 0), [((-5, 10.5, -1), (2, 13, 2), (40, 26))]),
    ("hand_right", "forearm_right", (-4, 11, 0), (0, 0, 0), [((-5.5, 7, -1), (3, 4, 2), (24, 42))]),
    ("finger_right_a", "hand_right", (-5, 7, 0), (6, 0, 4), [((-5.5, 1, -1), (1, 6, 1), (44, 42))]),
    ("finger_right_b", "hand_right", (-4, 7, 0), (8, 0, 0), [((-4.5, 1, -1), (1, 6, 1), (48, 42))]),
    ("finger_right_c", "hand_right", (-3, 7, 0), (6, 0, -4), [((-3.5, 1, -1), (1, 6, 1), (52, 42))]),
    ("arm_left", "body", (4, 35.5, 0), (-10, 0, -4), [((3, 23, -1), (2, 13, 2), (32, 26))]),
    ("forearm_left", "arm_left", (4, 23.5, 0), (-30, 0, 0), [((3, 10.5, -1), (2, 13, 2), (48, 26))]),
    ("hand_left", "forearm_left", (4, 11, 0), (0, 0, 0), [((2.5, 7, -1), (3, 4, 2), (34, 42))]),
    ("finger_left_a", "hand_left", (3, 7, 0), (10, 0, 4), [((2.5, 1, -1), (1, 6, 1), (56, 42))]),
    ("finger_left_b", "hand_left", (4, 7, 0), (4, 0, 0), [((3.5, 1, -1), (1, 6, 1), (60, 42))]),
    ("finger_left_c", "hand_left", (5, 7, 0), (8, 0, -4), [((4.5, 1, -1), (1, 6, 1), (44, 50))]),
]

SOOT = (62, 59, 53)
DEEP = (34, 32, 29)
BLACK = (18, 17, 15)
BONE = (196, 188, 170)
BONE_DARK = (150, 142, 126)
ASH = (120, 114, 102)
BLOOD = (122, 22, 18)


def geo():
    bones = []
    for name, parent, pivot, rot, cubes in BONES:
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if any(rot):
            b["rotation"] = list(rot)
        if cubes:
            b["cubes"] = [{"origin": list(o), "size": list(s), "uv": list(uv)} for o, s, uv in cubes]
        bones.append(b)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.tenant",
                "texture_width": TEX,
                "texture_height": TEX,
                "visible_bounds_width": 3,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }


def faces(uv, size):
    """Box UV, как в Blockbench: прямоугольники граней (x, y, w, h) на текстуре."""
    u, v = uv
    w, h, d = (int(round(x)) for x in size)
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def shade(c, k):
    return tuple(max(0, min(255, int(x * k))) for x in c)


def paint(img, rect, base, rnd, noise=0.12, dark_bottom=0.0):
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            k = 1 + rnd.uniform(-noise, noise)
            if dark_bottom and h > 1:
                k *= 1 - dark_bottom * (j / (h - 1))
            img.putpixel((x + i, y + j), shade(base, k) + (255,))


def texture(seed=461):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    for name, _, _, _, cubes in BONES:
        for _, size, uv in cubes:
            for face, rect in faces(uv, size).items():
                if name == "head":
                    paint(img, rect, BONE if face != "down" else BONE_DARK, rnd, 0.06)
                elif name.startswith(("hand", "finger")):
                    paint(img, rect, BONE_DARK, rnd, 0.08)
                elif name == "neck":
                    paint(img, rect, ASH, rnd, 0.1)
                elif name.startswith(("arm", "forearm")):
                    paint(img, rect, SOOT, rnd, 0.14, 0.25)
                elif name == "rag":
                    paint(img, rect, DEEP, rnd, 0.2, 0.3)
                elif name.startswith("leg"):
                    paint(img, rect, DEEP, rnd, 0.14, 0.45)
                else:
                    paint(img, rect, SOOT, rnd, 0.16, 0.2)

    # Лицо: гладкая кожа без глаз и рта, ничего не нарисовано (сделано на кости и шуме).
    # Затылок и макушка темнее: голова будто вымазана сажей сзади.
    for face in ("south", "up"):
        x, y, w, h = faces((22, 0), (6, 8, 6))[face]
        for j in range(h):
            for i in range(w):
                if rnd.random() < 0.55:
                    img.putpixel((x + i, y + j), shade(SOOT, rnd.uniform(0.7, 1.0)) + (255,))

    # Пальцы: последние 2 пикселя в запёкшейся крови.
    for name, _, _, _, cubes in BONES:
        if not name.startswith("finger"):
            continue
        for _, size, uv in cubes:
            for face in ("north", "south", "east", "west"):
                x, y, w, h = faces(uv, size)[face]
                for j in range(h - 2, h):
                    for i in range(w):
                        img.putpixel((x + i, y + j), shade(BLOOD, rnd.uniform(0.6, 0.95)) + (255,))
            x, y, w, h = faces(uv, size)["down"]
            img.putpixel((x, y), shade(BLOOD, 0.6) + (255,))

    # Тряпьё на теле: тёмные вертикальные прорехи и рёбра под тканью.
    bx, by, bw, bh = faces((0, 0), (6, 14, 3))["north"]
    for j in range(3, 10, 2):
        for i in range(1, bw - 1):
            img.putpixel((bx + i, by + j), shade(SOOT, 0.72) + (255,))
    for _ in range(14):
        x, y, w, h = faces((0, 0), (6, 14, 3))[rnd.choice(["north", "south", "east", "west"])]
        img.putpixel((x + rnd.randrange(w), y + rnd.randrange(h)), BLACK + (255,))
    # Рваный край тряпья: внизу часть пикселей прозрачная.
    rx, ry, rw, rh = faces((24, 50), (6, 8, 1))["north"]
    for i in range(rw):
        for j in range(rh - rnd.randint(0, 3), rh):
            img.putpixel((rx + i, ry + j), (0, 0, 0, 0))
    rx, ry, rw, rh = faces((24, 50), (6, 8, 1))["south"]
    for i in range(rw):
        for j in range(rh - rnd.randint(0, 3), rh):
            img.putpixel((rx + i, ry + j), (0, 0, 0, 0))
    return img


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    assets = root / "src/main/resources/assets/bodycamhorror"
    (assets / "geo/entity").mkdir(parents=True, exist_ok=True)
    (assets / "textures/entity").mkdir(parents=True, exist_ok=True)
    (assets / "geo/entity/tenant.geo.json").write_text(json.dumps(geo(), indent=2), encoding="utf-8")
    texture().save(assets / "textures/entity/tenant.png")
    # Проверка: развёртки не налезают друг на друга и не выходят за текстуру.
    used = {}
    for name, _, _, _, cubes in BONES:
        for _, size, uv in cubes:
            for face, (x, y, w, h) in faces(uv, size).items():
                assert x + w <= TEX and y + h <= TEX, (name, face)
                for i in range(w):
                    for j in range(h):
                        owner = used.setdefault((x + i, y + j), name)
                        assert owner == name, f"{name}.{face} overlaps {owner} at {(x + i, y + j)}"
    print("ok:", assets / "geo/entity/tenant.geo.json")


if __name__ == "__main__":
    main()
