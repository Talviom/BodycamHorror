"""Звуки смерти «экран гаснет» (интерфейс, 0.9.x): выключение старого ЭЛТ-монитора и шипение помех.

Usage: python -I tools/assets/make_deathfx.py <repo root>
Свои, синтез на numpy/scipy, без чужих записей. Чтобы не звучало «восьмибитно»: нет чистых тонов,
всё из шума и резонансов, писк строчника тонет в зерне, сверху плёночная грязь (сатурация, срез верхов,
маленькая комната).
  crt_off.ogg      1,3 с: глухой щелчок реле, сухой треск разряда, уходящий вниз писк, гаснущий гул сети
  death_static.ogg 1,4 с: мягкое приглушённое шипение (верхи срезаны, чтобы не резало ухо) с плавающей громкостью, редкие трески и гул 50 Гц
"""
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_dread as d  # noqa: E402

SR = d.SR


def crackle(rng, n, rate, lo, hi):
    """Редкие сухие щелчки: разряд статики на стекле."""
    x = np.zeros(n)
    for i in np.nonzero(rng.random(n) < rate / SR)[0]:
        k = min(n - i, int(rng.uniform(0.0008, 0.004) * SR))
        x[i:i + k] += rng.standard_normal(k) * np.exp(-np.arange(k) / (k / 3)) * rng.uniform(0.3, 1.0)
    return d.band(x, lo, hi)


def crt_off():
    rng = np.random.default_rng(1701)
    n = int(1.3 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)

    # Реле и корпус: глухой «тук» с резонансом пластика.
    body = d.modes(n, [(92, 0.09, 1.0), (148, 0.05, 0.6), (410, 0.02, 0.35), (1260, 0.006, 0.3)], rng)
    body += d.band(rng.standard_normal(n), 600, 2500) * d.env(n, 0.0005, 0.006) * 0.9
    out += body * 0.9

    # Разряд: короткая «каша» треска сразу после щелчка.
    zap = d.band(rng.standard_normal(n), 1800, 7000) * d.env(n, 0.002, 0.07)
    zap *= 0.5 + 0.5 * (rng.random(n) < 0.35)                      # рваный, не ровный шум
    out += zap * 0.5 + crackle(rng, n, 60, 1500, 8000) * np.exp(-t / 0.35) * 0.5

    # Писк строчника уходит вниз и тонет в зерне: не чистый тон, а узкая полоса шума.
    f = 7800 * np.exp(-t / 0.22) + 900
    phase = 2 * np.pi * np.cumsum(f) / SR
    whine = np.sin(phase) * (0.6 + 0.4 * d.lowpass(rng.standard_normal(n), 80) / 0.3)
    whine = d.band(whine + rng.standard_normal(n) * 0.25, 700, 9000)
    out += whine * np.clip(t / 0.03, 0, 1) * np.exp(-t / 0.28) * 0.22

    # Гул сети гаснет вместе с кадром.
    hum = np.sin(2 * np.pi * 50 * t) + 0.5 * np.sin(2 * np.pi * 100 * t + 1.1) + 0.25 * np.sin(2 * np.pi * 150 * t)
    out += hum * np.exp(-t / 0.4) * 0.12

    out = d.room(out, rng, length=0.5, decay=0.08, mix=0.15, lp=4500)
    out = np.tanh(out * 1.6)                                       # плёночная сатурация
    return d.lowpass(out, 9000)


def death_static():
    rng = np.random.default_rng(1702)
    n = int(1.4 * SR)
    t = np.arange(n) / SR
    white = rng.standard_normal(n)
    pink = d.lowpass(white, 900) * 2.4 + d.band(white, 900, 3500) * 0.35   # мягкий, без резких верхов
    flutter = 0.75 + 0.25 * np.clip(d.lowpass(rng.standard_normal(n), 12) / 0.15, -1, 1)
    hiss = pink * flutter
    hiss += crackle(rng, n, 12, 800, 3500) * 0.8
    hiss += (np.sin(2 * np.pi * 50 * t) + 0.4 * np.sin(2 * np.pi * 100 * t)) * 0.08
    # Плавный вход и долгий уход: помехи растворяются, когда проступает картинка.
    shape = np.clip(t / 0.12, 0, 1) * np.clip((1.4 - t) / 0.55, 0, 1)
    out = d.highpass(hiss * shape, 120)
    out = np.tanh(out * 1.1)
    return d.lowpass(out, 3800)


SOUNDS = {"crt_off": crt_off, "death_static": death_static}


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/sounds"
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            print("made", d.write(name, fn(), out, tmp).name)


if __name__ == "__main__":
    main()
