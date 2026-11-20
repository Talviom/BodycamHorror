"""Sounds for the 0.9 horror events (plan item 15). All procedural, nothing taken from elsewhere.

Usage: python -I tools/assets/make_dread.py <repo root> [names...]

fake_step1..4  heavy dragging steps of something that is not a player (the fake footsteps event):
               a dull low thud and a long scrape, slower and heavier than any player step
door_slam      a heavy door slams somewhere close, rattle and a long room tail
metal_bang     a distant blow on a metal pipe, muffled by walls, ringing on for seconds
far_cry        a far wordless cry through the corridors, muffled and reverberant
phone_ring     an old bell phone rings twice in an empty room
tape_rewind    the bodycam tape jerks back: whirr and squeal of the transport
"""
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
PEAK_DB = -3.0


def band(x, lo, hi, order=4):
    return signal.sosfilt(signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def lowpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, btype="low", fs=SR, output="sos"), x)


def highpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, btype="high", fs=SR, output="sos"), x)


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(0, t - attack) / decay)


def room(x, rng, length=1.4, decay=0.35, mix=0.4, lp=5000):
    n = int(length * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (decay * SR))
    ir = lowpass(ir, lp)
    ir[0] = 0
    wet = signal.fftconvolve(x, ir)[: len(x)]
    wet /= np.max(np.abs(wet)) + 1e-9
    dry = x / (np.max(np.abs(x)) + 1e-9)
    return dry * (1 - mix) + wet * mix


def pad(x, sec):
    return np.concatenate([x, np.zeros(int(sec * SR))])


def modes(n, freqs, rng, start=0):
    t = np.arange(n - start) / SR
    out = np.zeros(n)
    for f, d, a in freqs:
        out[start:] += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t / d)
    return out


def fake_step(seed):
    rng = np.random.default_rng(seed)
    n = int(0.75 * SR)
    t = np.arange(n) / SR
    thud = lowpass(rng.standard_normal(n), 170) * env(n, 0.006, 0.09) * 3.0
    thud += np.sin(2 * np.pi * (55 + rng.uniform(-6, 6)) * t) * env(n, 0.004, 0.11) * 0.8
    # The foot drags after landing: a scrape that swells and fades over half a second.
    s0 = int(0.06 * SR)
    m = n - s0
    tt = np.linspace(0, 1, m)
    swell = np.sin(np.pi * np.clip(tt / 0.75, 0, 1)) ** 1.5
    scrape = band(rng.standard_normal(m), 500, 2600) * swell * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(13, 19) * tt * 0.75) ** 2)
    grit = (rng.random(m) < 0.004 * swell) * rng.standard_normal(m) * 4
    scrape = scrape * 0.5 + band(grit, 1500, 6000, 2) * 0.4
    out = thud
    out[s0:] += scrape
    return pad(room(out, rng, 0.6, 0.12, 0.25), 0.05)


def door_slam():
    rng = np.random.default_rng(71)
    n = int(2.2 * SR)
    hit = lowpass(rng.standard_normal(n), 900) * env(n, 0.002, 0.05) * 2.5
    body = modes(n, [(82, 0.35, 1.0), (131, 0.25, 0.7), (207, 0.18, 0.5), (390, 0.08, 0.3)], rng)
    rattle_n = int(0.35 * SR)
    rattle = band(rng.standard_normal(rattle_n), 1500, 5000) * (rng.random(rattle_n) < 0.02) * 6
    rattle = band(rattle, 1200, 6000, 2) * np.linspace(1, 0, rattle_n) ** 2
    out = hit + body
    out[int(0.03 * SR): int(0.03 * SR) + rattle_n] += rattle * 0.6
    return room(out, rng, 2.0, 0.55, 0.45, 3500)


def metal_bang():
    rng = np.random.default_rng(72)
    n = int(4.5 * SR)
    clang = modes(n, [(311, 2.4, 1.0), (847, 1.6, 0.6), (1493, 1.1, 0.45), (2211, 0.7, 0.3), (3020, 0.4, 0.2)], rng)
    clang += lowpass(rng.standard_normal(n), 2000) * env(n, 0.001, 0.02) * 1.5
    far = lowpass(clang, 1400)
    return room(far, rng, 3.0, 0.9, 0.6, 2500)


def far_cry():
    rng = np.random.default_rng(73)
    sec = 2.8
    n = int(sec * SR)
    t = np.arange(n) / SR
    # Pitch: rises, holds and cracks, falls away.
    f0 = np.interp(t, [0, 0.4, 1.3, 1.6, 2.4, sec], [260, 480, 520, 430, 300, 220])
    f0 *= 1 + 0.012 * np.sin(2 * np.pi * 5.5 * t) + 0.01 * rng.standard_normal(n).cumsum() / np.sqrt(np.arange(1, n + 1))
    phase = 2 * np.pi * np.cumsum(f0) / SR
    src = sum(np.sin(k * phase) / k for k in range(1, 18))
    src += band(rng.standard_normal(n), 300, 5000) * 0.25
    vowel = np.zeros(n)
    for fc, bw, a in ((750, 120, 1.0), (1150, 160, 0.6), (2600, 300, 0.25)):
        vowel += band(src, fc - bw, fc + bw, 2) * a
    amp = np.interp(t, [0, 0.25, 1.6, 2.2, sec], [0, 1, 0.9, 0.4, 0])
    voice = vowel * amp
    far = lowpass(voice, 1800)
    return pad(room(far, rng, 3.0, 0.8, 0.65, 2200), 0.6)


def phone_ring():
    rng = np.random.default_rng(74)
    one = int(1.3 * SR)
    ring = np.zeros(one)
    strike = int(SR / 22)
    for i in range(0, one - strike, strike):
        side = (i // strike) % 2
        n = one - i
        ring[i:] += modes(n, [(1180 + 40 * side, 0.25, 1.0), (2620, 0.12, 0.5), (3890, 0.07, 0.3)], rng) * 0.35
    ring *= np.interp(np.arange(one), [0, one * 0.9, one], [1, 1, 0])
    gap = np.zeros(int(1.6 * SR))
    out = np.concatenate([ring, gap, ring, np.zeros(int(0.8 * SR))])
    out = band(out, 400, 4500, 2)  # an old small speaker in another room
    return room(out, rng, 1.2, 0.3, 0.35, 4000)


def tape_rewind():
    rng = np.random.default_rng(75)
    sec = 1.5
    n = int(sec * SR)
    t = np.arange(n) / SR
    speed = np.interp(t, [0, 0.15, 1.2, sec], [0.2, 1.0, 1.0, 0.0])
    f = 1800 + 3800 * speed + 300 * np.sin(2 * np.pi * 7 * t)
    squeal = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.25 * speed
    whirr = band(rng.standard_normal(n), 600, 7000) * (0.4 + 0.6 * speed)
    flutter = 1 + 0.5 * np.sin(2 * np.pi * (18 + 30 * speed) * t)
    motor = np.sin(2 * np.pi * np.cumsum(90 + 140 * speed) / SR) * 0.3 * speed
    clunk = lowpass(rng.standard_normal(n), 500) * env(n, 0.002, 0.04) * 2
    stop = np.zeros(n)
    k = int(1.25 * SR)
    stop[k:] = lowpass(rng.standard_normal(n - k), 600) * env(n - k, 0.002, 0.05) * 2
    return whirr * flutter * 0.6 + squeal + motor + clunk + stop


SOUNDS = {
    "fake_step1": lambda: fake_step(81),
    "fake_step2": lambda: fake_step(82),
    "fake_step3": lambda: fake_step(83),
    "fake_step4": lambda: fake_step(84),
    "door_slam": door_slam,
    "metal_bang": metal_bang,
    "far_cry": far_cry,
    "phone_ring": phone_ring,
    "tape_rewind": tape_rewind,
}


def write(name, x, out_dir, tmp):
    x = np.nan_to_num(x)
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (PEAK_DB / 20)
    wav = Path(tmp) / f"{name}.wav"
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    dur = len(x) / SR
    fade = min(0.05, dur * 0.1)
    af = f"afade=t=in:d=0.004,afade=t=out:st={dur - fade:.4f}:d={fade:.4f}"
    ogg = out_dir / f"{name}.ogg"
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav), "-af", af, "-ac", "1", "-ar", str(SR),
                    "-c:a", "libvorbis", "-q:a", "6", str(ogg)], check=True)
    return ogg


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/sounds"
    only = set(sys.argv[2:])
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            if not only or name in only:
                print("made", write(name, fn(), out, tmp).name)


if __name__ == "__main__":
    main()
