"""Полоска слотов «бирки улик» (интерфейс, 0.9.x): картонная бирка с люверсом и бечёвка.

Usage: python -I tools/assets/make_tags.py <repo root>
Пишет textures/gui/evidence_tag.png: 3 бирки 64x84 в ряд (разная грязь и надрывы, чтобы соседние не повторялись)
и textures/gui/evidence_twine.png: 64x8, кусок бечёвки, тянется по горизонтали.
Рисуется вдвое мельче (бирка 32x42 пикселя интерфейса). Своя работа, без чужих материалов.
Палитра: картон из кости C4BCAA, притушенный и тёплый; чернила сажа; пятна запёкшаяся кровь 7A1612 (редко).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

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
    if seed % 3 == 2:
        bx, by = r.uniform(40, 54), r.uniform(70, 78)
        b = (np.hypot((xx - bx) * 1.2, yy - by) + r.random((TH, TW)) * 2.5) < r.uniform(4, 6)
        f[b & inside, :3] = f[b & inside, :3] * 0.45 + np.array(BLOOD) * 0.3   # бурое, засохшее
    grit = inside & (r.random((TH, TW)) < 0.022)
    f[grit, :3] *= 0.7
    return np.clip(f, 0, 255).astype(np.uint8)


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


def main():
    root = Path(sys.argv[1]).resolve()
    gui = root / "src/main/resources/assets/bodycamhorror/textures/gui"
    Image.fromarray(np.concatenate([tag(s) for s in (1, 2, 3)], axis=1), "RGBA").save(gui / "evidence_tag.png")
    Image.fromarray(twine(), "RGBA").save(gui / "evidence_twine.png")
    print("evidence tags written")


if __name__ == "__main__":
    main()
