"""Звуки Жильца из настоящих записей CC0 (не синтез): слои, замедление, эхо, плёночное «плавание».

    python -I tools/assets/make_tenant_sfx.py <repo root> <папка с исходниками wav> [папка для превью]

Исходники (все CC0, список с адресами в docs/sources.md) скачаны в D:\\Minecraft\\dev\\sfx_src\\tenant
и переведены в моно 44,1 кГц в подпапку wav (имена вида pack__file.wav). Их в репозиторий не кладём.

Делает в assets/bodycamhorror/sounds:
  tenant_breath1..2   присутствие: медленное хриплое дыхание рядом
  tenant_blink1..3    прыжок: скачок кадра на плёнке, глухой удар
  tenant_rush         нарастание перед удушьем (перевёрнутый удар)
  tenant_strangle     удушье: рычание вплотную, хрип жертвы, хруст, глухое сердце
  tenant_quiet        затихание: удар, длинный звон, шипение уходит
  tenant_scare        скример у лица (когда он исчезает, не трогая)
  tenant_click1..3    хруст суставов при шагах
Если указана папка превью: tenant_sounds_preview.mp3 (все по очереди) и tenant_attack_preview.mp3
(атака целиком по времени, как в игре, 5 секунд).
"""
import subprocess
import sys
import tempfile
from fractions import Fraction
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
PEAK_DB = -2.0


def load(src, name):
    sr, x = wavfile.read(src / f"{name}.wav")
    x = x.astype(np.float64)
    if np.max(np.abs(x)) > 2:
        x /= 32768.0
    return x / (np.max(np.abs(x)) + 1e-12)


def cut(x, start, end=None):
    a = int(start * SR)
    b = len(x) if end is None else int(end * SR)
    return x[a:b].copy()


def speed(x, factor):
    """Скорость и высота вместе, как на магнитофоне: factor < 1 медленнее и ниже."""
    fr = Fraction(factor).limit_denominator(200)
    return signal.resample_poly(x, fr.denominator, fr.numerator)


def lowpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, btype="low", fs=SR, output="sos"), x)


def highpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, btype="high", fs=SR, output="sos"), x)


def fade(x, fin=0.005, fout=0.05):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def reverb(x, length=1.6, decay=0.45, mix=0.3, seed=7):
    """Эхо пустого бетонного помещения (свёртка с затухающим шумом)."""
    rng = np.random.default_rng(seed)
    n = int(length * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (decay * SR))
    ir = lowpass(ir, 5000)
    ir /= np.sqrt(np.sum(ir ** 2))
    wet = signal.fftconvolve(x, ir)[: len(x) + n]
    out = np.zeros(len(wet))
    out[: len(x)] += x * (1 - mix)
    return out + wet * mix


def wobble(x, depth=0.0025, rate=0.7, seed=3):
    """Плёнка «плавает»: тон медленно гуляет."""
    rng = np.random.default_rng(seed)
    t = np.arange(len(x)) / SR
    d = depth * SR * (np.sin(2 * np.pi * rate * t + rng.uniform(0, 6)) + 0.4 * np.sin(2 * np.pi * rate * 2.7 * t))
    idx = np.clip(np.arange(len(x)) + d, 0, len(x) - 1)
    return np.interp(idx, np.arange(len(x)), x)


def saturate(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def gate_stutter(x, rate=9.0, duty=0.55, seed=5):
    """Рваные вдохи: звук то прерывается, то прорывается (хрип при удушье)."""
    rng = np.random.default_rng(seed)
    n = len(x)
    g = np.zeros(n)
    pos = 0
    while pos < n:
        on = int(SR / rate * duty * rng.uniform(0.6, 1.4))
        off = int(SR / rate * (1 - duty) * rng.uniform(0.5, 1.6))
        g[pos:pos + on] = 1
        pos += on + off
    return x * np.convolve(g, np.ones(200) / 200, mode="same")


def mix(n_sec, *layers):
    out = np.zeros(int(n_sec * SR))
    for x, at, gain in layers:
        a = int(at * SR)
        b = min(len(out), a + len(x))
        if b > a:
            out[a:b] += x[: b - a] * gain
    return out


def tape_hiss(n_sec, level, seed=11):
    rng = np.random.default_rng(seed)
    return highpass(rng.standard_normal(int(n_sec * SR)), 3000) * level


# ---------- звуки ----------

def breath(src, which):
    if which == 1:
        x = load(src, "ghostbreath")
        x = cut(x, 0.6, 3.4)
        x = lowpass(highpass(x, 120), 3500)
        low = speed(cut(load(src, "growl_0"), 0.3, 1.5), 0.55)
        x = mix(3.4, (x, 0, 1.0), (lowpass(low, 900), 0.6, 0.35))
    else:
        x = speed(cut(load(src, "80-CC0-creature-SFX_0__breath"), 0, 0.67), 0.5)
        x = mix(2.2, (lowpass(x, 3000), 0.0, 1.0), (lowpass(x, 1800), 1.1, 0.7))
    return fade(reverb(wobble(x), 1.2, 0.3, 0.22), 0.05, 0.4)


def blink(src, which):
    hit = {1: "horror_hit_soundpack_1__Mid_8", 2: "horror_hit_soundpack_1__Mid_3", 3: "horror_hit_soundpack_1__Mid_5"}[which]
    at = {1: 0.30, 2: 0.30, 3: 0.83}[which]
    x = load(src, hit)
    x = cut(x, max(0, at - 0.02), at + 0.5)
    x = fade(x, 0.002, 0.25) * np.exp(-np.arange(len(x)) / (0.12 * SR))
    crack = cut(load(src, f"deep_breaks__Deep_Break_{which + 1}"), 0.1, 0.45)
    crack = highpass(crack, 400)
    out = mix(0.55, (x, 0, 1.0), (crack, 0.0, 0.35), (tape_hiss(0.06, 0.12, which), 0.0, 1.0))
    return fade(saturate(out, 1.3), 0.001, 0.08)


def rush(src):
    """Перевёрнутый глубокий удар: звук сам «втягивается» в момент, когда он у лица."""
    boom = load(src, "horror_hit_soundpack_1__Very_Bassy_3")
    boom = cut(boom, 0.53, 0.53 + 2.0)[::-1]
    moan = cut(load(src, "qubodup-GhostMoans__qubodup-GhostMoan05"), 0.2, 1.7)[::-1]
    moan = speed(moan, 1.25)
    out = mix(2.0, (boom, 0, 1.0), (highpass(moan, 300), 2.0 - len(moan) / SR, 0.55))
    return fade(cut(out, 1.0, 2.0), 0.15, 0.003)


def strangle(src):
    sec = 2.5
    growl = speed(cut(load(src, "80-CC0-creature-SFX_0__monster_04"), 0, 1.28), 0.7)
    roar = speed(cut(load(src, "80-CC0-creature-SFX_0__roar_02"), 0, 0.9), 0.62)
    gasp = load(src, "breathing_tired")
    gasp = gate_stutter(lowpass(highpass(cut(gasp, 0.2, 2.6), 250), 5000), rate=8.0, duty=0.45)
    gasp = saturate(gasp * 1.3, 1.1)
    cough = speed(cut(load(src, "80-CC0-creature-SFX_0__cough_03"), 0, 0.84), 0.85)
    crack1 = cut(load(src, "deep_breaks__Deep_Break_6"), 0.3, 0.8)
    crack2 = cut(load(src, "deep_breaks__Deep_Break_1"), 0.1, 0.6)
    heart = lowpass(cut(load(src, "heartbeat_slow_reverb"), 0, 1.8), 180)
    out = mix(sec,
              (lowpass(growl, 2500), 0.0, 0.9),
              (lowpass(roar, 1800), 1.0, 0.7),
              (gasp, 0.15, 0.35),
              (lowpass(cough, 2500), 1.6, 0.45),
              (crack1, 0.25, 0.8),
              (crack2, 1.35, 0.7),
              (speed(heart, 0.8), 0.2, 0.9))
    out = reverb(wobble(saturate(out, 1.6)), 0.9, 0.25, 0.18)
    return fade(cut(out, 0, sec), 0.003, 0.02)


def quiet(src):
    sec = 4.0
    thud = cut(load(src, "horror_hit_soundpack_1__Very_Bassy_2"), 0.40, 0.40 + 4.0)
    ring = cut(load(src, "horror_hit_soundpack_1__High_9"), 0.80, 0.80 + 4.0)
    ring = highpass(ring, 1500) * np.linspace(0.3, 1.0, len(ring)) ** 0.5
    out = mix(sec, (thud, 0, 1.0), (ring, 0.2, 0.55), (tape_hiss(sec, 0.012) * np.linspace(1, 0, int(sec * SR)), 0, 1.0))
    return fade(out, 0.002, 1.2)


def scare(src):
    sec = 2.2
    boom = cut(load(src, "horror_hit_soundpack_1__Very_Bassy_3"), 0.50, 0.50 + 2.2)
    scream = speed(cut(load(src, "80-CC0-creature-SFX_0__scream_02"), 0, 0.96), 0.72)
    moan = cut(load(src, "qubodup-GhostMoans__qubodup-GhostMoan01"), 0.6, 2.2)
    hiss = tape_hiss(0.2, 0.15, 21)
    out = mix(sec, (boom, 0, 1.0), (lowpass(scream, 4500), 0.01, 0.75), (highpass(moan, 200), 0.05, 0.35), (hiss, 0, 1.0))
    return fade(reverb(saturate(out, 1.4), 1.4, 0.4, 0.25)[: int(sec * SR)], 0.001, 0.6)


def click(src, which):
    x = cut(load(src, f"deep_breaks__Deep_Break_{[7, 8, 9][which - 1]}"), 0.05, 0.55)
    return fade(highpass(x, 250), 0.001, 0.1)


def build(src):
    return {
        "tenant_breath1": breath(src, 1),
        "tenant_breath2": breath(src, 2),
        "tenant_blink1": blink(src, 1),
        "tenant_blink2": blink(src, 2),
        "tenant_blink3": blink(src, 3),
        "tenant_rush": rush(src),
        "tenant_strangle": strangle(src),
        "tenant_quiet": quiet(src),
        "tenant_scare": scare(src),
        "tenant_click1": click(src, 1),
        "tenant_click2": click(src, 2),
        "tenant_click3": click(src, 3),
    }


def norm(x):
    x = np.nan_to_num(x)
    x = x - np.mean(x)
    return x / (np.max(np.abs(x)) + 1e-12) * 10 ** (PEAK_DB / 20)


def write_ogg(x, path, tmp):
    wav = Path(tmp) / (path.stem + ".wav")
    wavfile.write(wav, SR, (norm(x) * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav), "-ac", "1", "-ar", str(SR),
                    "-c:a", "libvorbis", "-q:a", "6", str(path)], check=True)


def write_mp3(x, path, tmp):
    wav = Path(tmp) / (path.stem + ".wav")
    wavfile.write(wav, SR, (norm(x) * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav), "-c:a", "libmp3lame", "-q:a", "3", str(path)], check=True)


def attack_demo(s):
    """Атака по времени, как в игре: смотрит 1 с, влево, вправо, 5 рывков, удушье, затихание."""
    t = 0.0
    layers = [(s["tenant_breath1"] * 0.5, 0.0, 1.0)]
    t = 1.0
    layers.append((s["tenant_blink1"], t, 0.8)); t += 0.35
    layers.append((s["tenant_blink2"], t, 0.8)); t += 0.35
    layers.append((s["tenant_rush"], t - 0.05, 0.7))
    for i in range(5):
        layers.append((s[f"tenant_blink{1 + i % 3}"], t, 0.9)); t += 0.16
    layers.append((s["tenant_strangle"], t, 1.0)); t += 2.2
    layers.append((s["tenant_quiet"], t, 0.9))
    return mix(t + 4.0, *layers)


def main():
    root = Path(sys.argv[1]).resolve()
    src = Path(sys.argv[2]).resolve()
    out_dir = root / "src/main/resources/assets/bodycamhorror/sounds"
    sounds = build(src)
    with tempfile.TemporaryDirectory() as tmp:
        for name, x in sounds.items():
            write_ogg(x, out_dir / f"{name}.ogg", tmp)
            print(f"made {name}.ogg {len(x) / SR:.2f}s")
        if len(sys.argv) > 3:
            prev = Path(sys.argv[3])
            prev.mkdir(parents=True, exist_ok=True)
            gap = np.zeros(int(0.8 * SR))
            seq = np.concatenate([np.concatenate([norm(x), gap]) for x in sounds.values()])
            write_mp3(seq, prev / "tenant_sounds_preview.mp3", tmp)
            write_mp3(attack_demo(sounds), prev / "tenant_attack_preview.mp3", tmp)
            print("previews in", prev)


if __name__ == "__main__":
    main()
