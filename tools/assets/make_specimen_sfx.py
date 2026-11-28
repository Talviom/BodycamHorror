"""Звуки «ОБРАЗЦА №7» (0.9.8): существо в колбе стучит по толстому стеклу. Свои, синтез numpy/scipy.

Usage: python -I tools/assets/make_specimen_sfx.py <repo root>
  specimen_knock1..3  один-три глухих удара когтем по стеклу сквозь жидкость: звон стекла приглушён, снизу тупой толчок
  specimen_frenzy     ~4 с: существо бьётся (частые неровные удары, скрежет когтя, бульканье)
  specimen_pickup     колбу снимают: лязг стальной ручки, плеск густой жидкости
  specimen_out        колбу выносят: тяжёлый засов, глухой удар изнутри и тишина
Без звона «по-мультяшному»: всё низко, глухо, как в подвале.
"""
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_dread as d  # noqa: E402

SR = d.SR


def knock(rng, strength=1.0):
    """Один удар: глухой толчок жидкости + приглушённый звон толстого стекла."""
    n = int(0.45 * SR)
    thud = d.lowpass(rng.standard_normal(n), 220) * d.env(n, 0.002, 0.05) * 3.0
    ring = d.modes(n, [(1180, 0.07, 0.6), (1930, 0.05, 0.45), (2870, 0.03, 0.3), (640, 0.12, 0.4)], rng)
    tick = d.highpass(rng.standard_normal(n), 2500) * d.env(n, 0.0005, 0.004) * 0.5
    return (thud + ring * 0.55 + tick) * strength


def place(out, x, at):
    i = int(at * SR)
    end = min(len(out), i + len(x))
    out[i:end] += x[: end - i]


def knocks(seed, times):
    rng = np.random.default_rng(seed)
    out = np.zeros(int((times[-1] + 0.6) * SR))
    for k, t in enumerate(times):
        place(out, knock(rng, 1.0 if k == 0 else 0.7 + rng.random() * 0.3), t)
    return d.room(out, rng, length=0.9, decay=0.18, mix=0.25, lp=3000)


def frenzy():
    rng = np.random.default_rng(1104)
    dur = 4.0
    out = np.zeros(int(dur * SR))
    t = 0.05
    while t < dur - 0.5:
        place(out, knock(rng, 0.6 + rng.random() * 0.5), t)
        t += 0.09 + rng.random() * 0.28
    # Скрежет когтя по стеклу: узкая полоса шума с дрожью высоты.
    n = int(1.2 * SR)
    scrape = d.band(rng.standard_normal(n), 1400, 3200) * d.env(n, 0.15, 0.6) * 0.35
    place(out, scrape, 1.3)
    # Бульканье густой жидкости.
    for _ in range(14):
        m = int(0.08 * SR)
        f = 140 + rng.random() * 220
        tt = np.arange(m) / SR
        bub = np.sin(2 * np.pi * f * (1 + 2.5 * tt) * tt) * d.env(m, 0.003, 0.03) * 0.4
        place(out, bub, rng.random() * (dur - 0.2))
    return d.room(out, rng, length=1.0, decay=0.2, mix=0.25, lp=3000)


def slosh(rng, sec):
    n = int(sec * SR)
    x = d.lowpass(rng.standard_normal(n), 500) * d.env(n, 0.05, sec * 0.35)
    wob = 0.6 + 0.4 * np.sin(2 * np.pi * 3.2 * np.arange(n) / SR)
    return x * wob * 1.5


def pickup():
    rng = np.random.default_rng(1105)
    out = np.zeros(int(1.2 * SR))
    clank = d.modes(int(0.4 * SR), [(410, 0.05, 1.0), (890, 0.03, 0.6), (1650, 0.02, 0.35)], rng)
    place(out, clank, 0.0)
    place(out, slosh(rng, 0.9), 0.08)
    place(out, knock(rng, 0.45), 0.55)
    return d.room(out, rng, length=0.9, decay=0.2, mix=0.25, lp=3500)


def taken_out():
    rng = np.random.default_rng(1106)
    out = np.zeros(int(2.4 * SR))
    bolt = d.modes(int(0.6 * SR), [(160, 0.12, 1.0), (330, 0.08, 0.7), (720, 0.04, 0.4)], rng)
    bolt += d.lowpass(rng.standard_normal(len(bolt)), 600) * d.env(len(bolt), 0.001, 0.03)
    place(out, bolt, 0.0)
    place(out, knock(rng, 1.2), 0.9)
    place(out, slosh(rng, 0.6) * 0.6, 1.0)
    return d.room(out, rng, length=1.4, decay=0.35, mix=0.35, lp=2500)


SOUNDS = {
    "specimen_knock1": lambda: knocks(1101, [0.02]),
    "specimen_knock2": lambda: knocks(1102, [0.02, 0.38]),
    "specimen_knock3": lambda: knocks(1103, [0.02, 0.27, 0.71]),
    "specimen_frenzy": frenzy,
    "specimen_pickup": pickup,
    "specimen_out": taken_out,
}


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/sounds"
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            print("made", d.write(name, fn(), out, tmp).name)


if __name__ == "__main__":
    main()
