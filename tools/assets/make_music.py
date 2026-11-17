"""TEMPORARY dark music for the mod (placeholders until the final tracks are decided).

Usage: python -I tools/assets/make_music.py <repo root>
Writes src/main/resources/assets/bodycamhorror/sounds/music/*.ogg (stereo 44.1 kHz Ogg Vorbis,
two-pass loudness normalisation with ffmpeg to about -16 LUFS).

Style (see the Obsidian note "Визуальный стиль"): found-footage horror. Low detuned drones,
tape hiss and wow, distant metallic hits in a big empty space. No beats, no melody, nothing
upbeat or synthwave. Everything is synthesized here with numpy/scipy; no samples are used.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
TARGET_LUFS = -16.0


# ---------------------------------------------------------------- building blocks

def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def lp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, "low", fs=SR, output="sos"), x, axis=0)


def hp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, "high", fs=SR, output="sos"), x, axis=0)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(signal.butter(order, [lo, hi], "band", fs=SR, output="sos"), x, axis=0)


def slow_lfo(n, rng, rate=0.05, depth=1.0):
    """Smooth random wander (sum of slow sines with random phases)."""
    tt = np.arange(n) / SR
    out = np.zeros(n)
    for k in range(3):
        out += np.sin(2 * np.pi * rate * (k + 1) * rng.uniform(0.6, 1.4) * tt + rng.uniform(0, 6.3)) / (k + 1)
    return out / 1.83 * depth


def drone(sec, rng, base=41.2, detune_cents=(0, 7, -11, 1200 + 4, 1200 - 9, 600 + 13), cutoff=380, drift=0.004):
    """Detuned saw/sine cluster with slow pitch drift and a wandering low-pass."""
    n = int(sec * SR)
    out = np.zeros(n)
    wander = 1 + slow_lfo(n, rng, 0.02, drift)
    for i, c in enumerate(detune_cents):
        f = base * 2 ** (c / 1200)
        ph = 2 * np.pi * np.cumsum(f * wander * (1 + slow_lfo(n, rng, 0.07, 0.0015))) / SR
        saw = 2 * ((ph / (2 * np.pi)) % 1) - 1
        layer = 0.6 * np.sin(ph) + 0.4 * saw
        amp = (0.9 if c < 1200 else 0.35) * (0.35 if 500 < c % 1200 < 700 else 1.0)  # tritone layer very faint
        out += layer * amp * (0.75 + 0.25 * slow_lfo(n, rng, 0.03))
    # Wandering filter: process in blocks with a slowly changing cutoff.
    block = SR // 10
    res = np.zeros(n)
    cut = cutoff * (1 + 0.45 * slow_lfo(n, rng, 0.015))
    zi = None
    for s in range(0, n, block):
        sos = signal.butter(2, max(60, cut[s]), "low", fs=SR, output="sos")
        if zi is None:
            zi = signal.sosfilt_zi(sos) * 0
        res[s:s + block], zi = signal.sosfilt(sos, out[s:s + block], zi=zi)
    return res / (np.max(np.abs(res)) + 1e-9)


def tape_hiss(n, rng, level=0.05):
    white = rng.standard_normal(n)
    pink = lp(white, 3000, 1) * 0.6 + hp(white, 2500, 1) * 0.25
    hiss = pink * level * (0.85 + 0.15 * slow_lfo(n, rng, 0.2))
    # Crackle: sparse clicks.
    clicks = np.zeros(n)
    idx = rng.integers(0, n, int(n / SR * 6))
    clicks[idx] = rng.uniform(-1, 1, len(idx))
    return hiss + lp(clicks, 4000, 1) * level * 3


def metal_hit(rng, sec=7.0, base=None):
    """A distant, inharmonic metallic strike (pipe or girder), dark and long."""
    n = int(sec * SR)
    tt = np.arange(n) / SR
    base = base or rng.uniform(70, 120)
    ratios = [1.0, 2.76, 5.40, 8.93, 13.34]
    out = np.zeros(n)
    for k, r in enumerate(ratios):
        out += np.sin(2 * np.pi * base * r * tt + rng.uniform(0, 6)) * np.exp(-tt / (2.6 / (k + 1) ** 0.7)) / (k + 1)
    strike = lp(rng.standard_normal(n) * np.exp(-tt / 0.01), 1500)
    return lp(out + strike * 0.4, 1400)


def thump(rng, sec=1.2, f=48):
    n = int(sec * SR)
    tt = np.arange(n) / SR
    body = np.sin(2 * np.pi * np.cumsum(f * (1 + 0.8 * np.exp(-tt / 0.03))) / SR) * np.exp(-tt / 0.25)
    knock = lp(rng.standard_normal(n), 200) * np.exp(-tt / 0.05)
    return body + knock * 0.5


def impact(rng, sec=6.0):
    """Dull heavy impact: sub boom, low rumble and a long dark tail."""
    n = int(sec * SR)
    tt = np.arange(n) / SR
    boom = np.sin(2 * np.pi * np.cumsum(38 * (1 + 1.5 * np.exp(-tt / 0.05))) / SR) * np.exp(-tt / 0.9)
    rumble = lp(rng.standard_normal(n), 120) * np.exp(-tt / 1.6) * 2.5
    debris = bp(rng.standard_normal(n), 300, 1500) * np.exp(-tt / 0.15) * 0.3
    return boom + rumble + debris


def reverb_ir(sec, rng, decay=1.6, bright=1800):
    n = int(sec * SR)
    tt = np.arange(n) / SR
    ir = np.stack([rng.standard_normal(n), rng.standard_normal(n)], axis=1)
    ir *= np.exp(-tt / decay)[:, None]
    ir = lp(ir, bright)
    ir[: int(0.02 * SR)] *= np.linspace(0, 1, int(0.02 * SR))[:, None]  # pre-delay feel
    return ir / np.sqrt(np.sum(ir ** 2, axis=0, keepdims=True))


def place(dst, src, at):
    at = int(at * SR)
    end = min(len(dst), at + len(src))
    if end > at:
        dst[at:end] += src[: end - at]


def stereo(mono, rng, width=0.0015):
    """Make a mono signal stereo with a tiny decorrelating delay."""
    d = int(width * SR)
    left = mono
    right = np.concatenate([np.zeros(d), mono[:-d]]) if d else mono
    return np.stack([left, right], axis=1)


def wet(x2, ir, mix):
    out = np.stack([signal.fftconvolve(x2[:, c], ir[:, c])[: len(x2)] for c in range(2)], axis=1)
    return x2 * (1 - mix) + out * mix


def tape(x2, rng, wow=0.0025, flutter=0.0006, sat=1.4, cutoff=7000):
    """Wow and flutter (variable delay), soft saturation and a dull top end."""
    n = len(x2)
    tt = np.arange(n) / SR
    mod = wow * np.sin(2 * np.pi * 0.55 * tt + rng.uniform(0, 6)) + flutter * np.sin(2 * np.pi * 7.3 * tt)
    mod += slow_lfo(n, rng, 0.3, wow * 0.5)
    delay = (0.012 + mod * 0.5) * SR
    idx = np.clip(np.arange(n) - delay, 0, n - 1)
    i0 = np.floor(idx).astype(int)
    fr = idx - i0
    i1 = np.minimum(i0 + 1, n - 1)
    out = x2[i0] * (1 - fr)[:, None] + x2[i1] * fr[:, None]
    out = np.tanh(sat * out) / np.tanh(sat)
    return lp(out, cutoff, 2)


def fade(x2, fin=0.0, fout=0.0):
    n = len(x2)
    if fin:
        k = int(fin * SR)
        x2[:k] *= np.linspace(0, 1, k)[:, None] ** 2
    if fout:
        k = int(fout * SR)
        x2[n - k:] *= np.linspace(1, 0, k)[:, None] ** 2
    return x2


def make_loop(render, sec, xfade=6.0):
    """Render sec + xfade seconds and fold the overlap onto the start, so the track loops cleanly."""
    x = render(sec + xfade)
    k = int(xfade * SR)
    head = x[:k]
    tail = x[-k:]
    w = np.linspace(0, 1, k)[:, None]
    loop = x[: int(sec * SR)].copy()
    loop[:k] = head * np.sqrt(w) + tail * np.sqrt(1 - w)
    return loop


# ---------------------------------------------------------------- tracks

def menu():
    rng = np.random.default_rng(101)
    ir = reverb_ir(5.0, rng, decay=2.2, bright=1500)

    def render(sec):
        n = int(sec * SR)
        d = drone(sec, rng, base=41.2, cutoff=420) * 0.55
        hits = np.zeros(n)
        tpos = rng.uniform(4, 8)
        while tpos < sec - 3:
            place(hits, metal_hit(rng) * rng.uniform(0.25, 0.45), tpos)
            tpos += rng.uniform(9, 15)
        mono = d + hits
        x = stereo(mono, rng)
        x = wet(x, ir, 0.45)
        x += stereo(tape_hiss(n, rng, 0.035), rng, 0.0007)
        return tape(x, rng)

    return make_loop(render, 80.0)


def lobby():
    rng = np.random.default_rng(202)
    ir = reverb_ir(5.0, rng, decay=2.6, bright=1200)

    def render(sec):
        n = int(sec * SR)
        d = drone(sec, rng, base=36.7, detune_cents=(0, -8, 1200 + 6), cutoff=260) * 0.42
        hits = np.zeros(n)
        for tpos in (rng.uniform(15, 25), rng.uniform(45, 58)):
            place(hits, metal_hit(rng, base=rng.uniform(55, 80)) * 0.22, tpos)
        # A far creak: narrow noise band sweeping slowly.
        creak_t = rng.uniform(30, 40)
        cl = int(2.5 * SR)
        cr = bp(rng.standard_normal(cl), 500, 900) * np.hanning(cl) * 0.08
        place(hits, cr, creak_t)
        x = stereo(d + hits, rng)
        x = wet(x, ir, 0.55)
        x += stereo(tape_hiss(n, rng, 0.045), rng, 0.0007)
        return tape(x, rng, wow=0.003)

    return make_loop(render, 70.0)


def round_start():
    rng = np.random.default_rng(303)
    sec = 16.0
    n = int(sec * SR)
    tt = t_axis(sec)
    ir = reverb_ir(5.0, rng, decay=2.0, bright=1600)
    rise = np.clip(tt / 9.8, 0, 1) ** 1.7
    d = drone(sec, rng, base=43.65, cutoff=300) * (0.15 + 0.85 * rise)
    # Open the filter as tension rises.
    d = lp(d, 900) * rise + d * (1 - rise)
    pulses = np.zeros(n)
    tpos = 1.2
    gap = 1.7
    while tpos < 9.6:
        place(pulses, thump(rng) * (0.35 + 0.5 * tpos / 9.6), tpos)
        tpos += gap
        gap = max(0.9, gap * 0.9)
    hit = np.zeros(n)
    place(hit, impact(rng) * 1.1, 10.0)
    # After the impact the drone drops away, leaving the tail and hiss.
    after = np.where(tt < 10.0, 1.0, np.exp(-(tt - 10.0) / 1.2))
    mono = d * after + pulses + hit
    x = wet(stereo(mono, rng), ir, 0.35)
    x += stereo(tape_hiss(n, rng, 0.03), rng, 0.0007)
    x = tape(x, rng)
    return fade(x, 0.4, 2.5)


def chord(sec, rng, freqs, amp_env):
    n = int(sec * SR)
    tt = t_axis(sec)
    out = np.zeros(n)
    for f in freqs:
        ph = 2 * np.pi * np.cumsum(f * (1 + slow_lfo(n, rng, 0.4, 0.002))) / SR
        out += (np.sin(ph) + 0.25 * np.sin(2 * ph)) * rng.uniform(0.7, 1.0)
    return lp(out, 1200) * amp_env(tt)


def round_won():
    rng = np.random.default_rng(404)
    sec = 3.6
    n = int(sec * SR)
    env = lambda tt: np.clip(tt / 0.6, 0, 1) * np.exp(-np.clip(tt - 0.8, 0, None) / 1.4)
    c = chord(sec, rng, [55.0, 82.41, 110.0, 138.59], env)  # low open A major, quiet
    x = wet(stereo(c, rng), reverb_ir(3.0, rng, 1.4, 1400), 0.4)
    x += stereo(tape_hiss(n, rng, 0.03), rng, 0.0007)
    return fade(tape(x, rng, wow=0.004), 0.05, 0.8)


def round_lost():
    rng = np.random.default_rng(505)
    sec = 4.0
    n = int(sec * SR)
    tt = t_axis(sec)
    f = 196 * 2 ** (-np.clip(tt / 3.2, 0, 1) * 1.0)  # slides down an octave
    out = np.zeros(n)
    for det in (0, 0.018, -0.011):
        out += np.sin(2 * np.pi * np.cumsum(f * (1 + det)) / SR)
    out = lp(out, 900) * np.clip(tt / 0.2, 0, 1) * np.exp(-tt / 2.4)
    noise = bp(rng.standard_normal(n), 200, 2000) * np.exp(-tt / 1.5) * 0.12
    x = wet(stereo(out + noise, rng), reverb_ir(3.0, rng, 1.2, 1500), 0.35)
    x += stereo(tape_hiss(n, rng, 0.035), rng, 0.0007)
    return fade(tape(x, rng, wow=0.006), 0.02, 0.8)


def match_won():
    rng = np.random.default_rng(606)
    sec = 8.0
    n = int(sec * SR)
    env = lambda tt: np.clip(tt / 2.0, 0, 1) ** 1.5 * np.exp(-np.clip(tt - 3.5, 0, None) / 1.8)
    c = chord(sec, rng, [41.2, 61.74, 82.41, 103.83, 123.47], env)  # low E major, slow swell
    bell = np.zeros(n)
    place(bell, metal_hit(rng, base=98) * 0.35, 2.4)
    x = wet(stereo(c + bell, rng), reverb_ir(4.0, rng, 2.0, 1300), 0.45)
    x += stereo(tape_hiss(n, rng, 0.03), rng, 0.0007)
    return fade(tape(x, rng, wow=0.003), 0.3, 1.5)


def match_lost():
    rng = np.random.default_rng(707)
    sec = 8.0
    n = int(sec * SR)
    tt = t_axis(sec)
    d = drone(sec, rng, base=46.25, cutoff=500)
    d *= np.clip(tt / 0.8, 0, 1) * np.exp(-tt / 3.5)
    fall = np.zeros(n)
    f = 110 * 2 ** (-np.clip(tt / 6.0, 0, 1) * 1.5)
    fall += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 3.0) * 0.35
    hit = np.zeros(n)
    place(hit, impact(rng, 5.0) * 0.9, 0.6)
    x = wet(stereo(d * 0.6 + lp(fall, 700) + hit, rng), reverb_ir(5.0, rng, 2.4, 1200), 0.45)
    x += stereo(tape_hiss(n, rng, 0.04), rng, 0.0007)
    return fade(tape(x, rng, wow=0.005), 0.05, 2.0)


TRACKS = {
    "menu": menu, "lobby": lobby, "round_start": round_start, "round_won": round_won,
    "round_lost": round_lost, "match_won": match_won, "match_lost": match_lost,
}


# ---------------------------------------------------------------- output

def loudnorm(wav_in, ogg_out):
    """Two-pass EBU R128 normalisation to TARGET_LUFS, true peak -1.5 dBTP, then Vorbis."""
    first = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(wav_in), "-af",
                            f"loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=11:print_format=json", "-f", "null", "-"],
                           capture_output=True, text=True)
    txt = first.stderr
    m = json.loads(txt[txt.rindex("{"): txt.rindex("}") + 1])
    af = (f"loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=11:measured_I={m['input_i']}:measured_TP={m['input_tp']}:"
          f"measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true")
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav_in), "-af", af, "-ar", str(SR), "-ac", "2",
                    "-c:a", "libvorbis", "-q:a", "5", str(ogg_out)], check=True)


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/assets/bodycamhorror/sounds/music"
    out.mkdir(parents=True, exist_ok=True)
    names = sys.argv[2:] or list(TRACKS)
    with tempfile.TemporaryDirectory() as tmp:
        for name in names:
            x = TRACKS[name]()
            x = x - x.mean(axis=0)
            x = x / (np.max(np.abs(x)) + 1e-9) * 0.9
            wav = Path(tmp) / f"{name}.wav"
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            loudnorm(wav, out / f"{name}.ogg")
            print("music", name, f"{len(x) / SR:.1f}s")


if __name__ == "__main__":
    main()
