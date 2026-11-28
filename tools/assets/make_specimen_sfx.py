"""Звуки «ОБРАЗЦА №7» (0.9.8). Свои, синтез numpy/scipy. Варианты B и D выбраны пользователем 28.11.2026.

Usage: python -I tools/assets/make_specimen_sfx.py <repo root>
  specimen_thump1..2  (B) тяжёлый мокрый удар телом о стенку колбы изнутри, потом бульканье
  specimen_moan1..2   (D) глухой низкий стон из-под жидкости с пузырями, будто оно дышит
  specimen_frenzy     ~4 с: колба упала, существо бьётся (несколько ударов подряд и стон)
  specimen_pickup     колбу снимают: лязг стальной ручки, плеск густой жидкости
  specimen_out        колбу выносят: тяжёлый засов, глухой удар изнутри и тишина
sounds.json: specimen_knock = thump1, thump2, moan1, moan2 (обычный звук), specimen_thump = только удары (рядом Жилец).
Всё низко и глухо, как в подвале, без звона.
"""
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_dread as d  # noqa: E402

SR = d.SR


def place(out, x, at):
    i = int(at * SR)
    end = min(len(out), i + len(x))
    out[i:end] += x[: end - i]


def bubbles(out, rng, start, count, spread):
    for k in range(count):
        m = int(0.09 * SR)
        tt = np.arange(m) / SR
        fq = 120 + rng.random() * 160
        bub = np.sin(2 * np.pi * fq * (1 + 3 * tt) * tt) * d.env(m, 0.004, 0.04) * 0.35
        place(out, bub, start + k * spread + rng.random() * 0.05)


def thump_hit(rng, strength=1.0):
    """Один удар телом о стекло: низкий мокрый толчок и приглушённая стенка колбы."""
    n = int(0.5 * SR)
    body = d.lowpass(rng.standard_normal(n), 140) * d.env(n, 0.004, 0.09) * 4
    body += np.sin(2 * np.pi * (50 + rng.random() * 12) * np.arange(n) / SR) * d.env(n, 0.003, 0.12) * 0.8
    glass = d.modes(n, [(620, 0.05, 0.25), (1040, 0.03, 0.12)], rng)
    return (body + glass) * strength


def thump(seed):
    rng = np.random.default_rng(seed)
    out = np.zeros(int(1.6 * SR))
    place(out, thump_hit(rng), 0.02)
    bubbles(out, rng, 0.25, 6, 0.12)
    return d.room(out, rng, length=1.2, decay=0.3, mix=0.3, lp=1800)


def moan_voice(rng, dur, base):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = base + 18 * np.sin(2 * np.pi * (0.3 + rng.random() * 0.2) * t) - 25 * t / dur
    phase = 2 * np.pi * np.cumsum(f0) / SR
    v = np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.3 * np.sin(3 * phase + 0.4)
    v *= 1 + 0.3 * np.sin(2 * np.pi * (5.5 + rng.random() * 2) * t)
    v = d.lowpass(v, 600) + d.lowpass(rng.standard_normal(n), 400) * 0.3
    return v * np.clip(np.minimum(t / 0.5, (dur - t) / 0.8), 0, 1)


def moan(seed, base):
    rng = np.random.default_rng(seed)
    x = moan_voice(rng, 2.4, base)
    bubbles(x, rng, 1.4, 4, 0.17)
    return d.room(x, rng, length=1.4, decay=0.35, mix=0.35, lp=1500)


def frenzy():
    rng = np.random.default_rng(1104)
    out = np.zeros(int(4.0 * SR))
    for at in (0.05, 0.55, 0.9, 1.6, 1.95):
        place(out, thump_hit(rng, 0.7 + rng.random() * 0.35), at)
    place(out, moan_voice(rng, 1.8, 110) * 0.8, 2.1)
    bubbles(out, rng, 0.3, 10, 0.3)
    return d.room(out, rng, length=1.2, decay=0.3, mix=0.3, lp=1800)


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
    place(out, thump_hit(rng, 0.4), 0.55)
    return d.room(out, rng, length=0.9, decay=0.2, mix=0.25, lp=3500)


def taken_out():
    rng = np.random.default_rng(1106)
    out = np.zeros(int(2.4 * SR))
    bolt = d.modes(int(0.6 * SR), [(160, 0.12, 1.0), (330, 0.08, 0.7), (720, 0.04, 0.4)], rng)
    bolt += d.lowpass(rng.standard_normal(len(bolt)), 600) * d.env(len(bolt), 0.001, 0.03)
    place(out, bolt, 0.0)
    place(out, thump_hit(rng, 1.1), 0.9)
    place(out, slosh(rng, 0.6) * 0.6, 1.0)
    return d.room(out, rng, length=1.4, decay=0.35, mix=0.35, lp=2500)


SOUNDS = {
    "specimen_thump1": lambda: thump(2),
    "specimen_thump2": lambda: thump(12),
    "specimen_moan1": lambda: moan(4, 95),
    "specimen_moan2": lambda: moan(14, 82),
    "specimen_frenzy": frenzy,
    "specimen_pickup": pickup,
    "specimen_out": taken_out,
}


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/sounds"
    for old in ("specimen_knock1", "specimen_knock2", "specimen_knock3"):
        (out / f"{old}.ogg").unlink(missing_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            print("made", d.write(name, fn(), out, tmp).name)


if __name__ == "__main__":
    main()
