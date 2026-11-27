"""Полоска слотов «бирки улик» (интерфейс, 0.9.x): картонная бирка с люверсом и бечёвка.

Usage: python -I tools/assets/make_tags.py <repo root>
Пишет в textures/gui:
  evidence_tag.png   256x84: 4 бирки 64x84 (разная грязь, надрывы, штамп, рукописная пометка; на 2-й скоба,
                     на 3-й полоска скотча, на 4-й бурое пятно), чтобы соседние не повторялись.
  evidence_twine.png 64x8: кусок крученой бечёвки (код рисует провисающую бечёвку по столбцам).
  evidence_parts.png 64x48: ржавый гвоздь с витками бечёвки (0,0 16x16), паутина (16,0 32x32),
                     узел с двумя нитками и свинцовой пломбой (48,0 16x48).
Рисуется вдвое мельче (бирка 32x42 пикселя интерфейса). Своя работа, без чужих материалов.
Палитра: картон из кости C4BCAA, притушенный и тёплый; чернила сажа; пятна запёкшаяся кровь 7A1612 (редко).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

TW, TH = 64, 84
INK = (34, 29, 24)
BONE = np.array((196, 188, 170), float)
CARD = np.array((150, 136, 110), float)      # тёплый старый картон (кость, уведённая в охру и притушенная)
ASH = (120, 114, 102)
SOOT = (78, 74, 66)
BLOOD = (122, 22, 18)


def tag(seed):
    r = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:TH, 0:TW]
    # Форма: прямоугольник со срезанными верхними углами, края неровные (надорванный картон).
    cut = 12
    inside = (xx >= 1) & (xx <= TW - 2) & (yy >= 1) & (yy <= TH - 2)
    inside &= ~((yy < cut) & (xx + yy < cut))
    inside &= ~((yy < cut) & ((TW - 1 - xx) + yy < cut))
    edge_noise = r.random((TH, TW))
    border = inside & ~(np.roll(inside, 1, 0) & np.roll(inside, -1, 0) & np.roll(inside, 1, 1) & np.roll(inside, -1, 1))
    inside &= ~(border & (edge_noise < 0.35))                 # выщербины по краю
    # Надрыв на одном боку.
    ty = int(r.integers(30, 70))
    side = int(r.integers(0, 2))
    for k in range(int(r.integers(3, 6))):
        x = k if side == 0 else TW - 1 - k
        inside[ty + k // 2:ty + k // 2 + 1, x] = False

    f = np.zeros((TH, TW, 4), float)
    # Картон: волокно (горизонтальные полосы), пятна, края темнее (засаленные), складка по диагонали.
    fibre = np.repeat(r.random((TH, 1)), TW, 1) * 0.06 + r.random((TH, TW)) * 0.10
    dist = np.minimum.reduce([xx, TW - 1 - xx, yy, TH - 1 - yy]).astype(float)
    grime = np.clip(1 - dist / 9, 0, 1) ** 1.5 * 0.45
    col = CARD[None, None, :] * (0.92 + fibre[..., None] - grime[..., None])
    crease = np.abs((xx - TW * 0.15) * 0.9 - (yy - TH * 0.55)) < 0.8
    col[crease] *= 0.82
    # Кольцо от кружки или воды.
    cx, cy, rad = r.uniform(18, 46), r.uniform(48, 70), r.uniform(9, 14)
    ring = np.abs(np.hypot(xx - cx, yy - cy) - rad) < 1.0
    col[ring] *= 0.85
    # Люверс сверху по центру: картонная подкладка темнее, металлическое кольцо, дырка насквозь.
    d = np.hypot(xx - TW / 2 + 0.5, yy - 9)
    col[d < 7] *= 0.88
    f[..., :3] = col
    f[..., 3] = np.where(inside, 255, 0)
    ring_m = (d >= 3) & (d < 5)
    f[ring_m, :3] = ASH
    f[(d >= 3) & (d < 3.8) & (yy < 9), :3] = (160, 152, 136)   # блик на кольце
    f[d < 3, 3] = 0
    # Типографские линейки для записей (бледные), шапка «вещдок» пишется кодом.
    for y in (62, 70):
        f[y, 8:TW - 8, :3] *= 0.8
    # Редкое пятно запёкшейся крови у одного из трёх.
    if seed == 4:
        bx, by = r.uniform(40, 54), r.uniform(70, 78)
        b = (np.hypot((xx - bx) * 1.2, yy - by) + r.random((TH, TW)) * 2.5) < r.uniform(4, 6)
        f[b & inside, :3] = f[b & inside, :3] * 0.45 + np.array(BLOOD) * 0.3   # бурое, засохшее
    grit = inside & (r.random((TH, TW)) < 0.022)
    f[grit, :3] *= 0.7
    img = Image.fromarray(np.clip(f, 0, 255).astype(np.uint8), "RGBA")
    details(img, seed, r)
    return np.array(img)


def details(img, seed, r):
    """Штамп, рукописная пометка по линейке, скоба или скотч. Всё бледное, чтобы не спорить с предметом."""
    d = ImageDraw.Draw(img)
    px, py = 9, 60
    while px < 52:                                   # торопливая рукописная пометка чернилами
        nx, ny = px + int(r.integers(2, 4)), py + int(r.integers(-2, 2))
        d.line([(px, py), (nx, max(58, min(61, ny)))], fill=(52, 44, 38, 170))
        px, py = nx, max(58, min(61, ny))
    stamp = Image.new("RGBA", (30, 30), (0, 0, 0, 0))
    sd = ImageDraw.Draw(stamp)
    col = (96, 40, 34, 90) if seed == 1 else (70, 64, 56, 105)   # один штамп бурый, как выцветшие чернила
    sd.ellipse([2, 2, 27, 27], outline=col, width=2)
    sd.ellipse([7, 7, 22, 22], outline=col, width=1)
    sd.line([(4, 15), (25, 15)], fill=col, width=1)
    stamp = stamp.rotate({1: 18, 2: -25, 3: 40, 4: -10}[seed], resample=Image.NEAREST)
    img.alpha_composite(stamp, (30, 38))
    if seed == 2:                                    # канцелярская скоба
        d.line([(40, 13), (50, 11)], fill=(150, 148, 140, 255))
        d.line([(40, 14), (50, 12)], fill=(80, 78, 74, 255))
    if seed == 3:                                    # полоска пожелтевшего скотча
        tape = Image.new("RGBA", (26, 9), (170, 150, 96, 110)).rotate(-20, expand=True)
        img.alpha_composite(tape, (36, 14))


def twine():
    r = np.random.default_rng(77)
    f = np.zeros((8, 64, 4), np.uint8)
    for x in range(64):
        # Крученая бечёвка: две пряди со сдвигом, через пиксель светлее и темнее.
        for k, y in enumerate((3, 4)):
            light = ((x + k * 2) // 2) % 2 == 0
            c = (110, 98, 78) if light else (70, 62, 50)
            f[y, x, :3] = c
            f[y, x, 3] = 255
        if r.random() < 0.12:
            f[2 if r.random() < 0.5 else 5, x, :3] = (90, 80, 64)
            f[2 if r.random() < 0.5 else 5, x, 3] = 150
    return f


def parts():
    img = Image.new("RGBA", (64, 48), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    tw1, tw2 = (110, 98, 78, 255), (70, 62, 50, 255)
    # Гвоздь в (0,0) 16x16, центр (8,7): ржавая шляпка, витки бечёвки, подтёк ржавчины вниз.
    cx, cy = 8, 6
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=(46, 32, 22, 255))
    d.ellipse([cx - 5, cy - 5, cx + 4, cy + 4], fill=(92, 62, 40, 255))
    d.ellipse([cx - 3, cy - 4, cx, cy - 1], fill=(124, 92, 62, 255))
    for k in (-3, -1, 1, 3):
        d.line([(cx - 7, cy + k - 1), (cx + 7, cy + k + 1)], fill=tw1 if k % 4 else tw2)
    for k in range(1, 4):
        d.point((cx + (k % 2), cy + 6 + k), fill=(84, 52, 30, 170 - k * 40))
    # Паутина в (16,0) 32x32, крепится в правом верхнем углу (16+24, 6).
    wx, wy = 16 + 24, 6
    pts = [(wx + 6, wy - 5), (wx + 6, wy + 16), (wx - 4, wy + 22), (wx - 20, wy + 10), (wx - 18, wy - 2)]
    for q in pts:
        d.line([(wx, wy), q], fill=(150, 146, 136, 70))
    for k in (5, 9, 13, 17):
        ring = [(wx + (q[0] - wx) * k / 22, wy + (q[1] - wy) * k / 22) for q in pts]
        for a, b in zip(ring, ring[1:]):
            d.line([a, b], fill=(150, 146, 136, 50))
    # Узел, две нитки и свинцовая пломба в (48,0) 16x48, узел вверху по центру (56, 2).
    kx = 56
    d.rectangle([kx - 2, 0, kx + 2, 4], fill=(84, 74, 58, 255))
    for k in range(22):
        d.point((kx - 1 - k * 3 // 22, 4 + k), fill=tw1 if k % 2 else tw2)
        d.point((kx + 1 + k * 3 // 22, 4 + k), fill=tw2 if k % 2 else tw1)
    sx, sy = kx, 36
    d.ellipse([sx - 7, sy - 7, sx + 7, sy + 7], fill=(52, 52, 50, 255))
    d.ellipse([sx - 6, sy - 6, sx + 6, sy + 6], fill=(86, 84, 80, 255))
    d.ellipse([sx - 4, sy - 4, sx + 4, sy + 4], outline=(58, 56, 52, 255))
    d.line([(sx - 2, sy - 1), (sx - 1, sy + 1), (sx + 2, sy - 1)], fill=(58, 56, 52, 255))
    d.arc([sx - 6, sy - 6, sx + 6, sy + 6], 200, 290, fill=(126, 124, 118, 255))
    return img


def main():
    root = Path(sys.argv[1]).resolve()
    gui = root / "src/main/resources/assets/bodycamhorror/textures/gui"
    Image.fromarray(np.concatenate([tag(s) for s in (1, 2, 3, 4)], axis=1), "RGBA").save(gui / "evidence_tag.png")
    parts().save(gui / "evidence_parts.png")
    Image.fromarray(twine(), "RGBA").save(gui / "evidence_twine.png")
    print("evidence tags written")


if __name__ == "__main__":
    main()
