"""Звук рывка Жильца в сторону (0.9.1), из его же звуков в репозитории (свои, CC0-исходники уже в них).

    python -I tools/assets/make_tenant_dodge.py <repo root> [папка для превью]

Делает assets/bodycamhorror/sounds/tenant_dodge1..2.ogg: втянутый назад хлопок плёнки (перевёрнутый
tenant_blink), сразу за ним глухой удар и хруст сустава (tenant_click), рваный обрыв шипения, как
выпавший кусок кадра. Без «звона» и электронных свистов, чтобы не было похоже на эндермена и киберпанк.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100


def load_ogg(path, tmp):
    wav = Path(tmp) / (path.stem + ".wav")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), str(wav)], check=True)
    sr, x = wavfile.read(wav)
    x = x.astype(np.float64) / 32768.0
    return x / (np.max(np.abs(x)) + 1e-12)


def lowpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, "low", fs=SR, output="sos"), x)


def highpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, "high", fs=SR, output="sos"), x)


def fade(x, fin, fout):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b)
    return x


def speed(x, k):
    n = int(len(x) / k)
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)


def place(out, x, at, gain):
    a = int(at * SR)
    b = min(len(out), a + len(x))
    out[a:b] += x[: b - a] * gain


def stutter(x, rng, rate=28.0):
    """Звук пару раз обрывается: кусок плёнки выпал."""
    g = np.ones(len(x))
    for _ in range(3):
        a = rng.integers(int(0.05 * SR), max(int(0.06 * SR), len(x) - int(0.05 * SR)))
        g[a:a + int(SR / rate)] = 0.08
    return x * np.convolve(g, np.ones(120) / 120, mode="same")


def dodge(blink, click, which):
    rng = np.random.default_rng(40 + which)
    n = int(0.55 * SR)
    out = np.zeros(n)
    # 1) втягивание: перевёрнутый хлопок, 0,14 с
    suck = blink[: int(0.3 * SR)][::-1]
    suck = lowpass(speed(suck, 1.6 if which == 1 else 1.35), 2200)
    suck = fade(suck[-int(0.14 * SR):], 0.08, 0.004)
    place(out, suck, 0.0, 0.7)
    # 2) глухой удар, ниже обычного прыжка
    hit = lowpass(speed(blink, 0.82 if which == 1 else 0.9), 1600)[: int(0.38 * SR)]
    hit = fade(hit * np.exp(-np.arange(len(hit)) / (0.09 * SR)), 0.001, 0.12)
    place(out, hit, 0.14, 1.0)
    # 3) хруст сустава
    cr = highpass(click[: int(0.2 * SR)], 500)
    place(out, fade(cr, 0.001, 0.08), 0.15 + 0.02 * which, 0.45)
    # 4) шипение плёнки на всё время, с обрывами
    hiss = highpass(rng.standard_normal(n), 3000) * 0.05 * np.exp(-np.arange(n) / (0.25 * SR))
    out = stutter(out + hiss, rng)
    out = np.tanh(out * 1.4) / np.tanh(1.4)
    out = fade(out, 0.002, 0.15)
    return out / (np.max(np.abs(out)) + 1e-12) * 10 ** (-3 / 20)


def write(x, path, tmp, codec):
    wav = Path(tmp) / (path.stem + "_out.wav")
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    args = ["-c:a", "libvorbis", "-q:a", "5"] if codec == "ogg" else ["-c:a", "libmp3lame", "-b:a", "160k"]
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), *args, str(path)], check=True)


def main():
    root = Path(sys.argv[1])
    snd = root / "src/main/resources/assets/bodycamhorror/sounds"
    with tempfile.TemporaryDirectory() as tmp:
        blink = load_ogg(snd / "tenant_blink2.ogg", tmp)
        click = load_ogg(snd / "tenant_click1.ogg", tmp)
        outs = [dodge(blink, click, w) for w in (1, 2)]
        for i, x in enumerate(outs, 1):
            write(x, snd / f"tenant_dodge{i}.ogg", tmp, "ogg")
        if len(sys.argv) > 2:
            gap = np.zeros(int(0.6 * SR))
            write(np.concatenate([outs[0], gap, outs[1], gap, outs[0]]), Path(sys.argv[2]) / "tenant_dodge_preview.mp3", tmp, "mp3")
    print("ok")


if __name__ == "__main__":
    main()
