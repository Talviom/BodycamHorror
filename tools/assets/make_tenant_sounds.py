"""Звуки Жильца: скример, дыхание, щелчки суставов. Синтез с нуля (numpy/scipy), как make_sounds.py.

    python -I tools/assets/make_tenant_sounds.py <repo root>
"""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import numpy as np  # noqa: E402

from make_sounds import SR, band, env_ad, highpass, lowpass, noise, place, small_room, write  # noqa: E402


def scream():
    """Скример: удар помех, срывающийся визг, падающий вниз, и глухой удар под ним."""
    rng = np.random.default_rng(461)
    sec = 1.4
    n = int(sec * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # Удар: широкий шум, как если бы плёнку рвануло.
    burst = highpass(noise(int(0.25 * SR), rng), 300) * env_ad(int(0.25 * SR), 0.002, 0.06)
    place(x, burst * 1.4, 0)
    # Визг: несколько расстроенных голосов, частота падает с 1100 до 380 Гц, дрожит.
    voice = np.zeros(n)
    for detune, amp in ((1.0, 1.0), (1.013, 0.7), (0.987, 0.7), (1.51, 0.35), (2.02, 0.25)):
        f = (380 + 720 * np.exp(-t / 0.45)) * detune
        f *= 1 + 0.035 * np.sin(2 * np.pi * 7.5 * t + rng.uniform(0, 6)) + 0.01 * rng.standard_normal(n).cumsum() / np.sqrt(n)
        ph = 2 * np.pi * np.cumsum(f) / SR
        saw = 2 * ((ph / (2 * np.pi)) % 1.0) - 1
        voice += amp * saw
    voice = np.tanh(voice * 1.8)
    voice = band(voice, 280, 5200)
    voice *= env_ad(n, 0.012, 0.55)
    x += voice * 0.9
    # Дыхание сквозь визг.
    x += band(noise(n, rng), 900, 3200) * env_ad(n, 0.02, 0.4) * 0.35
    # Глухой удар снизу.
    L = int(0.5 * SR)
    lt = np.arange(L) / SR
    thump = np.sin(2 * np.pi * np.cumsum(38 * (1 + 1.5 * np.exp(-lt / 0.03))) / SR) * env_ad(L, 0.003, 0.18)
    place(x, thump * 1.2, 0)
    # Плёнка «плывёт»: медленная модуляция громкости.
    x *= 1 + 0.15 * np.sin(2 * np.pi * 3.1 * t)
    return small_room(x, rng, mix=0.18)


def breath():
    """Медленный мокрый вдох и выдох, близко. Тихий, для фона рядом с ним."""
    rng = np.random.default_rng(462)
    sec = 3.2
    n = int(sec * SR)
    t = np.arange(n) / SR
    inhale = np.exp(-((t - 0.9) / 0.45) ** 2)
    exhale = np.exp(-((t - 2.2) / 0.55) ** 2)
    x = band(noise(n, rng), 350, 1600) * inhale * 0.8
    x += band(noise(n, rng), 180, 900) * exhale
    # Хрип: тихий низкий тон в выдохе.
    x += np.sin(2 * np.pi * np.cumsum(92 + 8 * np.sin(2 * np.pi * 1.3 * t)) / SR) * exhale * 0.25
    return lowpass(x, 2400)


def clicks():
    """Щелчки суставов: 4-6 сухих треска разной высоты."""
    rng = np.random.default_rng(463)
    sec = 0.9
    n = int(sec * SR)
    x = np.zeros(n)
    at = 0.02
    for _ in range(rng.integers(4, 7)):
        L = int(0.025 * SR)
        c = band(noise(L, rng), rng.uniform(1500, 2600), rng.uniform(4500, 7000)) * env_ad(L, 0.0005, 0.004)
        place(x, c * rng.uniform(0.5, 1.0), int(at * SR))
        at += rng.uniform(0.06, 0.18)
    return x


SOUNDS = {"tenant_scream": scream, "tenant_breath": breath, "tenant_click": clicks}


def main():
    root = Path(sys.argv[1]).resolve()
    out_dir = root / "src/main/resources/assets/bodycamhorror/sounds"
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            print("made", write(name, fn(), out_dir, tmp).name)


if __name__ == "__main__":
    main()
