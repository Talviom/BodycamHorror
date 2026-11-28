"""«ОБРАЗЕЦ №7» (0.9.8, цель раунда вместо кассеты): колба в стальном каркасе с ручкой, в кровяной мути
скрюченное существо с шипами, одним глазом и когтистой лапой у стекла. Свои текстуры 32x32 и модель.

Usage: python -I tools/assets/make_specimen.py <repo root>
Блок оставлен с id tape_block (а предмет tape), чтобы не ломать точки и команды; меняются только вид и звук.
Модель в границах формы TapeBlock: x4..12 y0..14 z4..12, смотрит на север (глаз и лапа спереди).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

root = Path(sys.argv[1]).resolve()
assets = root / "src/main/resources/assets/bodycamhorror"
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_blocks as mb  # noqa: E402

N = mb.N


def save(name, img, alpha=None):
    rgb = (np.clip(img, 0, 1) * 255).round().astype(np.uint8)
    if alpha is None:
        Image.fromarray(rgb, "RGB").save(assets / "textures/block" / f"{name}.png")
    else:
        a = (np.clip(alpha, 0, 1) * 255).round().astype(np.uint8)
        Image.fromarray(np.dstack([rgb, a]), "RGBA").save(assets / "textures/block" / f"{name}.png")


def steel():
    r = mb.rng(801)
    img = mb.base((52, 53, 52), r, var=0.1, grain=0.07)
    img = mb.rust(img, r, 0.07)
    img = img * 0.85
    img = mb.scratches(img, r, 8)
    img = mb.grime(img, r, 0.35)
    return mb.edge_darken(img, 1, 0.6)


def cap():
    r = mb.rng(802)
    img = steel()
    # Этикетка цвета кости с выцветшими строками («ОБРАЗЕЦ №7») и красной полосой карантина.
    img = mb.rect(img, 4, 10, 28, 22, (150, 140, 118))
    img = mb.rect(img, 4, 10, 28, 12, (92, 22, 18))
    for row, (x0, x1) in ((15, (7, 25)), (18, (7, 16))):
        for x in range(x0, x1):
            if r.random() < 0.85:
                img[row, x] = np.array([0.2, 0.18, 0.16])
    return mb.grime(img, r, 0.25, streaks=False)


def glass(liquid, level=0.78):
    """Сторона колбы: снизу мутная жидкость до level, сверху грязное стекло, потёки и пузыри."""
    r = mb.rng(803)
    y = np.linspace(0, 1, N)[:, None] * np.ones((1, N))
    liq = (1 - y) < level  # строки снизу
    c = np.array(liquid, np.float32) / 255
    img = np.ones((N, N, 3)) * 0.55
    img[liq] = c * (0.75 + 0.35 * mb.value_noise(r, 6)[liq][:, None])
    alpha = np.where(liq, 0.55, 0.18)
    # Мениск и грязная полоса по уровню.
    edge = int(round((1 - level) * N))
    img[edge:edge + 1] = c * 1.6
    alpha[edge:edge + 1] = 0.95
    # Потёки и пятна на стекле сверху.
    for x in r.choice(N, 6, replace=False):
        top = r.integers(0, edge)
        img[top:edge, x] = img[top:edge, x] * 0.6 + c * 0.4
        alpha[top:edge, x] = 0.5
    # Пузырьки.
    for _ in range(7):
        bx, by = r.integers(2, N - 2), r.integers(edge + 2, N - 2)
        img[by, bx] = np.clip(c * 2.2, 0, 1)
        alpha[by, bx] = 0.9
    # Блик стекла слева.
    img[:, 3] = np.clip(img[:, 3] + 0.25, 0, 1)
    alpha[:, 3] = np.maximum(alpha[:, 3], 0.45)
    return img, alpha


def flesh(base, vein):
    r = mb.rng(804)
    img = mb.base(base, r, var=0.2, grain=0.08)
    v = mb.value_noise(r, 3)
    veins = np.abs(v - 0.5) < 0.04
    img[veins] = np.array(vein, np.float32) / 255
    img = img * (0.8 + 0.2 * mb.value_noise(r, 4))[..., None]
    return np.clip(img, 0, 1)


VARIANTS = {
    # C: кровяная муть, внутри скрюченное существо с шипами, одним глазом и когтистой лапой у стекла.
    "c": dict(liquid=(64, 16, 12), flesh=((150, 104, 88), (52, 14, 14))),
}


def spine_tex():
    r = mb.rng(805)
    img = mb.base((150, 136, 104), r, var=0.12, grain=0.06)
    y = np.linspace(0, 1, N)[:, None]
    return np.clip(img * (0.35 + 0.65 * y)[..., None], 0, 1)  # к кончику темнеет


def eye_tex():
    img = np.ones((N, N, 3)) * np.array([0.78, 0.74, 0.62])
    yy, xx = np.mgrid[:N, :N]
    d = np.sqrt((xx - 15.5) ** 2 + (yy - 15.5) ** 2)
    img[d < 9] = np.array([0.45, 0.30, 0.20])
    img[d < 5] = np.array([0.03, 0.02, 0.02])
    img[(d > 13)] *= 0.55  # красноватые прожилки по краю
    img[(d > 13)] += np.array([0.15, 0.0, 0.0])
    return np.clip(img, 0, 1)


def rot(e, axis, angle, origin):
    e["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
    return e


save("specimen_spine", spine_tex())
save("specimen_eye", eye_tex())

save("specimen_steel", steel())
save("specimen_cap", cap())
for k, v in VARIANTS.items():
    g, a = glass(v["liquid"])
    save(f"specimen_glass_{k}", g, a)
    save(f"specimen_flesh_{k}", flesh(*v["flesh"]))

    els = [
        mb.el("base", (4, 0, 4), (12, 1, 12), "steel"),
        mb.el("cap", (4, 10, 4), (12, 11.5, 12), "steel", north="cap"),
        mb.el("rod nw", (4, 1, 4), (5, 10, 5), "steel"),
        mb.el("rod ne", (11, 1, 4), (12, 10, 5), "steel"),
        mb.el("rod sw", (4, 1, 11), (5, 10, 12), "steel"),
        mb.el("rod se", (11, 1, 11), (12, 10, 12), "steel"),
        mb.el("handle post w", (6, 11.5, 7.5), (7, 13, 8.5), "steel"),
        mb.el("handle post e", (9, 11.5, 7.5), (10, 13, 8.5), "steel"),
        mb.el("handle bar", (6, 13, 7.5), (10, 14, 8.5), "steel"),
        # Скрюченное тело: брюхо, горб, свёрнутая к стеклу голова.
        mb.el("belly", (6.5, 1.25, 6.75), (9.5, 4.25, 9.5), "flesh"),
        rot(mb.el("hunch", (7, 3.75, 7), (9.75, 6.5, 9.75), "flesh"), "z", -22.5, (8, 4, 8)),
        rot(mb.el("head", (6, 5.5, 6.25), (8.25, 7.75, 8.5), "flesh"), "y", 22.5, (7, 6.5, 7.5)),
        # Глаз смотрит наружу, зрачок чуть впереди.
        mb.el("eye", (6.25, 5.85, 6.0), (7.75, 7.35, 6.3), "eye", full=("north",)),
        # Когтистая лапа прижата к переднему стеклу.
        mb.el("arm", (8.75, 1.75, 5.5), (9.5, 4.5, 6.75), "flesh"),
        rot(mb.el("claw 1", (8.6, 4.25, 5.3), (8.9, 5.75, 5.6), "spine"), "z", 22.5, (8.75, 4.25, 5.45)),
        rot(mb.el("claw 2", (9.2, 4.25, 5.3), (9.5, 5.75, 5.6), "spine"), "z", -22.5, (9.35, 4.25, 5.45)),
        # Шипы из горба и спины, разведены в стороны.
        rot(mb.el("spine 1", (8, 6, 8), (8.5, 9.25, 8.5), "spine"), "z", -22.5, (8.25, 6, 8.25)),
        rot(mb.el("spine 2", (8.75, 5.5, 8.5), (9.25, 8.5, 9), "spine"), "z", -45, (9, 5.5, 8.75)),
        rot(mb.el("spine 3", (7.25, 5.75, 9), (7.75, 9, 9.5), "spine"), "x", 22.5, (7.5, 5.75, 9.25)),
        rot(mb.el("spine 4", (8.25, 5, 9.25), (8.75, 8, 9.75), "spine"), "x", 45, (8.5, 5, 9.5)),
        rot(mb.el("spine 5", (6.25, 3.5, 8), (6.75, 6.25, 8.5), "spine"), "z", 45, (6.5, 3.5, 8.25)),
        rot(mb.el("spine 6", (9.25, 2.5, 7.5), (9.75, 5, 8), "spine"), "z", -45, (9.5, 2.5, 7.75)),
        mb.el("glass", (5, 1, 5), (11, 10, 11), "glass", full=("north", "south", "east", "west")),
    ]
    tex = {"particle": "specimen_steel", "steel": "specimen_steel", "cap": "specimen_cap",
           "glass": f"specimen_glass_{k}", "flesh": f"specimen_flesh_{k}",
           "spine": "specimen_spine", "eye": "specimen_eye"}
    rotg = [30, 225, 0]
    m = mb.model(tex, els, mb.display(rotg, mb.centre_translation(rotg, 0.625, (8, 7, 8))), ao=True)
    m["render_type"] = "minecraft:translucent"
    m["credit"] = "Bodycam Horror, generated by tools/assets/make_specimen.py"
    (assets / "models/block/tape_block.json").write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
    (assets / "models/item/tape.json").write_text(
        json.dumps({"parent": "bodycamhorror:block/tape_block"}, indent=2) + "\n", encoding="utf-8")
print("specimen textures, block and item model")
