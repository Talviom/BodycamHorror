"""Звуки рации (0.9, пункт 14): щелчок тангенты с коротким шипением, отпускание со «squelch»-хвостом.

Usage: python -I tools/assets/make_radio.py <repo root>
Свои, синтез на numpy/scipy: пластиковый щелчок (короткий резонанс корпуса) и полоса шума 300..3200 Гц,
как у дешёвой УКВ-рации. Глухо и грязно, без «цифровых» писков.
"""
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_dread as d  # noqa: E402

SR = d.SR


def click(rng, pitch=1.0):
    n = int(0.03 * SR)
    x = d.modes(n, [(1900 * pitch, 0.004, 1.0), (3100 * pitch, 0.002, 0.6), (740 * pitch, 0.006, 0.5)], rng)
    x += rng.standard_normal(n) * d.env(n, 0.0005, 0.003) * 0.8
    return x


def hiss(rng, sec, attack, decay):
    n = int(sec * SR)
    x = d.band(rng.standard_normal(n), 300, 3200)
    # Неровная «каша» помех: шум дрожит по громкости, как плохой приём.
    wobble = 0.7 + 0.3 * d.lowpass(rng.standard_normal(n), 30) / 0.2
    x = x * np.clip(wobble, 0.2, 1.4) * d.env(n, attack, decay)
    return np.tanh(x * 2.5)


def radio_on():
    rng = np.random.default_rng(901)
    c = click(rng, 1.0)
    h = hiss(rng, 0.22, 0.004, 0.06) * 0.45
    out = np.zeros(int(0.26 * SR))
    out[: len(c)] += c
    out[int(0.012 * SR): int(0.012 * SR) + len(h)] += h
    return d.pad(out, 0.02)


def radio_off():
    rng = np.random.default_rng(902)
    h = hiss(rng, 0.32, 0.002, 0.12) * 0.7
    c = click(rng, 0.85)
    out = np.zeros(int(0.36 * SR))
    out[: len(h)] += h
    pos = int(0.24 * SR)
    out[pos: pos + len(c)] += c * 0.9
    return d.pad(out, 0.02)


SOUNDS = {"radio_on": radio_on, "radio_off": radio_off}


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/sounds"
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            print("made", d.write(name, fn(), out, tmp).name)


if __name__ == "__main__":
    main()
