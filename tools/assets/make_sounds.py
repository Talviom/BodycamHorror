"""Synthesizes the mod's sounds (mono 44.1 kHz) and encodes them to Ogg Vorbis with ffmpeg.

Usage: python -I tools/assets/make_sounds.py <repo root> [sound names, to make only those]
Every sound is procedural (numpy/scipy), with no samples taken from elsewhere.
ffmpeg adds the short fades and encodes to libvorbis.
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


def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def noise(n, rng):
    return rng.standard_normal(n)


def band(x, lo, hi, order=4):
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, btype="low", fs=SR, output="sos"), x)


def highpass(x, f, order=4):
    return signal.sosfilt(signal.butter(order, f, btype="high", fs=SR, output="sos"), x)


def env_ad(n, attack, decay, start=0):
    """Envelope: linear attack, exponential decay, starting at sample `start`."""
    e = np.zeros(n)
    a = max(1, int(attack * SR))
    idx = np.arange(n - start)
    seg = np.where(idx < a, idx / a, np.exp(-(idx - a) / max(1, decay * SR)))
    e[start:] = seg
    return e


def place(dst, src, at):
    at = int(at)
    end = min(len(dst), at + len(src))
    if end > at:
        dst[at:end] += src[: end - at]


def small_room(x, rng, length=0.35, decay=0.08, mix=0.25):
    n = int(length * SR)
    ir = noise(n, rng) * np.exp(-np.arange(n) / (decay * SR))
    ir = lowpass(ir, 6000)
    ir[0] = 0
    wet = signal.fftconvolve(x, ir)[: len(x)]
    wet /= np.max(np.abs(wet)) + 1e-9
    dry = x / (np.max(np.abs(x)) + 1e-9)
    return dry * (1 - mix) + wet * mix


def damped_modes(n, modes, rng, start=0):
    """Sum of damped sines (freq, decay sec, amp) with a small random phase."""
    t = np.arange(n - start) / SR
    out = np.zeros(n)
    for f, d, a in modes:
        out[start:] += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / d)
    return out


# ---------------------------------------------------------------- sounds

def whisper(seed, sec, rate):
    """A breathy wordless whisper: noise through gliding vowel formants, plus hissing consonants."""
    rng = np.random.default_rng(seed)
    n = int(sec * SR)
    src = noise(n, rng)
    nfft, hop = 2048, 256
    f, _, Z = signal.stft(src, fs=SR, nperseg=nfft, noverlap=nfft - hop)
    frames = Z.shape[1]
    vowels = [(300, 870, 2240), (640, 1190, 2390), (440, 1020, 2240), (270, 2290, 3010),
              (570, 840, 2410), (660, 1720, 2410), (300, 870, 2240)]
    # Syllables: a vowel target and a loudness for each one.
    syl_len = SR / rate
    n_syl = int(n / syl_len) + 2
    targets = [vowels[rng.integers(len(vowels))] for _ in range(n_syl)]
    tt = np.arange(frames) * hop
    gain = np.zeros((len(f), frames))
    for j, s in enumerate(tt):
        k = s / syl_len
        i0 = int(k)
        w = k - i0
        w = w * w * (3 - 2 * w)
        F = [(1 - w) * targets[i0][m] + w * targets[i0 + 1][m] for m in range(3)]
        g = np.zeros(len(f))
        for m, fm in enumerate(F):
            bw = 90 + 60 * m
            g += [1.0, 0.7, 0.35][m] * np.exp(-0.5 * ((f - fm) / bw) ** 2)
        g += 0.12 * np.exp(-0.5 * ((f - 4500) / 1500) ** 2)  # breath
        gain[:, j] = g
    voiced = signal.istft(Z * gain, fs=SR, nperseg=nfft, noverlap=nfft - hop)[1][:n]

    # Each syllable rises and falls in loudness, with little gaps between them.
    amp = np.zeros(n)
    pos = int(rng.uniform(0.05, 0.15) * SR)
    while pos < n - int(0.2 * SR):
        L = int(rng.uniform(0.6, 1.3) * syl_len)
        seg = np.sin(np.linspace(0, np.pi, L)) ** 1.5 * rng.uniform(0.5, 1.0)
        place(amp, seg, pos)
        # Sometimes a hissing "s" or "sh" before the syllable.
        if rng.random() < 0.45:
            sl = int(rng.uniform(0.08, 0.18) * SR)
            fc = rng.choice([3200, 5200, 6800])
            sib = band(noise(sl, rng), fc * 0.7, min(fc * 1.5, 16000)) * np.hanning(sl) * 0.6
            place(voiced, sib * np.std(voiced) / (np.std(sib) + 1e-9), max(0, pos - sl // 2))
        pos += L + int(rng.uniform(0.02, 0.12) * SR)
    amp = lowpass(np.maximum(amp, 0), 30, order=2)
    out = voiced * (0.15 + amp)
    # A slow drift in loudness, so the voice seems to move closer and back.
    out *= 0.7 + 0.3 * np.sin(2 * np.pi * rng.uniform(0.2, 0.5) * t_axis(sec) + rng.uniform(0, 6))
    out = highpass(out, 180)
    return small_room(out, rng, length=0.6, decay=0.15, mix=0.35)


def radio_static(seed, sec):
    rng = np.random.default_rng(seed)
    n = int(sec * SR)
    body = band(noise(n, rng), 350, 3200)
    # A hiss that pumps up and down like a weak signal.
    flutter = lowpass(np.abs(noise(n, rng)), 12, order=2)
    flutter = 0.55 + 0.9 * flutter / (flutter.max() + 1e-9)
    body *= flutter
    # Crackle: sparse clicks.
    clicks = np.zeros(n)
    for _ in range(int(sec * rng.uniform(25, 45))):
        place(clicks, band(noise(80, rng), 800, 6000) * np.hanning(80) * rng.uniform(1, 4), rng.integers(n))
    # Ghost tone: a carrier that drifts.
    tt = t_axis(sec)
    tone = 0.12 * np.sin(2 * np.pi * (1000 + 60 * np.sin(2 * np.pi * 0.7 * tt)) * tt)
    x = body + clicks * np.std(body) + tone * np.std(body) * 3
    x = np.tanh(2.2 * x / (np.std(x) * 3))
    # Squelch: a sharp click at the start and a short "ksshh" tail at the end.
    sq_on = int(0.03 * SR)
    head = highpass(noise(sq_on, rng), 1500) * np.linspace(1.4, 0.3, sq_on)
    x[:sq_on] = head
    tail_len = int(0.12 * SR)
    tail = highpass(noise(tail_len, rng), 2500) * np.linspace(1.2, 0, tail_len) ** 0.7
    x[-tail_len - int(0.04 * SR):-int(0.04 * SR)] = tail
    x[-int(0.04 * SR):] = 0
    return x


def clunk(n, rng, start=0, heavy=1.0):
    x = np.zeros(n)
    thump = np.sin(2 * np.pi * 70 * np.arange(int(0.25 * SR)) / SR) * env_ad(int(0.25 * SR), 0.002, 0.05)
    place(x, thump * heavy, start)
    hit = lowpass(noise(int(0.06 * SR), rng), 2500) * env_ad(int(0.06 * SR), 0.0005, 0.012)
    place(x, hit * 0.8, start)
    ring = damped_modes(int(0.4 * SR), [(1230, 0.06, 0.25), (2710, 0.04, 0.18), (4130, 0.03, 0.1), (530, 0.09, 0.2)], rng)
    place(x, ring, start)
    # A spring rattle right after the hit.
    place(x, band(noise(int(0.05 * SR), rng), 3000, 9000) * env_ad(int(0.05 * SR), 0.001, 0.01) * 0.3, start + int(0.025 * SR))
    return x


def hum(tt, f0, rng):
    """Mains hum with a buzzy edge: 50 Hz plus harmonics."""
    ph = 2 * np.pi * np.cumsum(f0) / SR
    h = np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.35 * np.sin(3 * ph) + 0.2 * np.sin(4 * ph) + 0.12 * np.sin(6 * ph)
    buzz = np.sign(np.sin(2 * ph)) * 0.08
    return h + lowpass(buzz, 2500)


def breaker_off():
    rng = np.random.default_rng(11)
    sec = 1.05
    n = int(sec * SR)
    tt = t_axis(sec)
    x = clunk(n, rng, start=int(0.01 * SR), heavy=1.1)
    # The power dies: hum sags in pitch and fades out.
    st = int(0.03 * SR)
    f0 = np.full(n, 50.0)
    k = np.clip((tt - 0.03) / 0.8, 0, 1)
    f0 = 50 * (1 - 0.55 * k ** 1.5)
    h = hum(tt, f0, rng) * np.where(tt < 0.03, 0, np.exp(-np.clip(tt - 0.03, 0, None) / 0.22))
    x += 0.35 * h
    # An electrical snap as the contacts open.
    place(x, highpass(noise(int(0.012 * SR), rng), 2000) * 0.6, st)
    return x


def breaker_on():
    rng = np.random.default_rng(12)
    sec = 1.3
    n = int(sec * SR)
    tt = t_axis(sec)
    x = clunk(n, rng, start=int(0.01 * SR), heavy=1.0)
    k = np.clip((tt - 0.05) / 0.9, 0, 1)
    f0 = 50 * (0.45 + 0.55 * (1 - (1 - k) ** 2))
    amp = np.where(tt < 0.05, 0, 1 - np.exp(-np.clip(tt - 0.05, 0, None) / 0.3))
    x += 0.32 * hum(tt, f0, rng) * amp
    # A few zaps while the current settles.
    for at in (0.06, 0.11, 0.19):
        place(x, band(noise(int(0.01 * SR), rng), 2000, 9000) * 0.5, at * SR)
    return x


def engine(tt, rate, rng, roughness=0.3):
    """A small engine: one low thump per firing at a variable rate (Hz)."""
    n = len(tt)
    x = np.zeros(n)
    phase = np.cumsum(rate) / SR
    fire = np.where(np.diff(np.floor(phase), prepend=0) > 0)[0]
    pulse_len = int(0.09 * SR)
    for i in fire:
        r = rate[i]
        L = min(pulse_len, int(SR / max(r, 1) * 1.6))
        p = np.sin(2 * np.pi * rng.uniform(55, 75) * np.arange(L) / SR) * env_ad(L, 0.002, 0.03)
        p += lowpass(noise(L, rng), 900) * env_ad(L, 0.001, 0.015) * 0.7
        place(x, p * rng.uniform(1 - roughness, 1), i)
    mech = band(noise(n, rng), 1500, 5000) * 0.04
    return lowpass(x, 3000) + mech


def generator_down():
    rng = np.random.default_rng(21)
    sec = 3.6
    tt = t_axis(sec)
    k = np.clip(tt / 3.0, 0, 1)
    rate = 32 * (1 - k) ** 1.4 + 2.5
    amp = np.clip(1.1 - 0.5 * k, 0, 1)
    x = engine(tt, rate, rng, roughness=0.3) * amp
    # Wind-down whine.
    whine_f = 420 * (1 - 0.85 * k)
    x += 0.06 * np.sin(2 * np.pi * np.cumsum(whine_f) / SR) * (1 - k) ** 2
    # A final rattle and clank.
    n = len(tt)
    x[int(3.05 * SR):] *= np.linspace(1, 0, n - int(3.05 * SR))
    place(x, clunk(int(0.4 * SR), rng, heavy=0.6) * 0.6, 3.05 * SR)
    return x


def generator_up():
    rng = np.random.default_rng(22)
    sec = 3.6
    tt = t_axis(sec)
    n = len(tt)
    # The recoil starter: two pulls on the cord.
    x = np.zeros(n)
    for start, dur in ((0.05, 0.45), (0.75, 0.5)):
        L = int(dur * SR)
        lt = np.arange(L) / SR
        r = 9 + 10 * np.sin(np.pi * lt / dur)
        c = engine(lt, r, rng, roughness=0.5) * np.sin(np.pi * lt / dur) * 0.7
        c += band(noise(L, rng), 600, 2500) * np.sin(np.pi * lt / dur) * 0.08
        place(x, c, start * SR)
    # It catches and revs up.
    k = np.clip((tt - 1.35) / 1.4, 0, 1)
    rate = np.where(tt < 1.35, 0.01, 6 + 26 * (1 - (1 - k) ** 2))
    amp = np.where(tt < 1.35, 0, np.clip((tt - 1.35) / 0.15, 0, 1) * (0.8 + 0.25 * k))
    x += engine(tt, rate, rng, roughness=0.35) * amp
    whine_f = 120 + 300 * k
    x += 0.05 * np.sin(2 * np.pi * np.cumsum(whine_f) / SR) * amp
    return x


def heartbeat():
    rng = np.random.default_rng(31)
    sec = 0.8
    n = int(sec * SR)
    x = np.zeros(n)
    for at, f, a, d in ((0.0, 52, 1.0, 0.09), (0.27, 64, 0.7, 0.07)):
        L = int(0.32 * SR)
        lt = np.arange(L) / SR
        fpath = f * (1 + 0.6 * np.exp(-lt / 0.02))
        beat = np.sin(2 * np.pi * np.cumsum(fpath) / SR) * env_ad(L, 0.006, d)
        beat += lowpass(noise(L, rng), 200) * env_ad(L, 0.003, d * 0.5) * 0.5
        place(x, beat * a, int((at + 0.01) * SR))
    return lowpass(x, 400)


def knock(seed, base):
    rng = np.random.default_rng(seed)
    sec = 0.42
    n = int(sec * SR)
    x = np.zeros(n)
    st = int(0.004 * SR)
    x += damped_modes(n, [(base, 0.06, 1.0), (base * 2.37, 0.035, 0.6), (base * 4.9, 0.02, 0.35),
                          (base * 8.3, 0.012, 0.2), (90, 0.05, 0.8)], rng, start=st)
    hit = lowpass(noise(int(0.008 * SR), rng), 4000) * np.hanning(int(0.008 * SR)) * 1.5
    place(x, hit, st)
    # The door panel rattles a little in its frame.
    place(x, band(noise(int(0.06 * SR), rng), 300, 1200) * env_ad(int(0.06 * SR), 0.001, 0.02) * 0.25, st + int(0.01 * SR))
    return small_room(x, rng, length=0.35, decay=0.06, mix=0.2)


def lamp_buzz(seed, sec):
    rng = np.random.default_rng(seed)
    n = int(sec * SR)
    tt = t_axis(sec)
    ph = 2 * np.pi * 100 * tt
    buzz = np.sign(np.sin(ph)) * 0.5 + np.sin(ph) + 0.4 * np.sin(3 * ph) + 0.3 * np.sin(5 * ph)
    buzz = band(buzz, 90, 6000, order=2)
    # The tube flickers: random on/off gating with soft edges.
    gate = np.zeros(n)
    pos = 0
    on = True
    while pos < n:
        L = int(rng.uniform(0.02, 0.18 if on else 0.08) * SR)
        gate[pos:pos + L] = 1.0 if on else rng.uniform(0.0, 0.15)
        pos += L
        on = not on
    gate = lowpass(gate, 300, order=2)
    x = buzz * gate
    # Arcing crackles where the gate changes.
    edges = np.where(np.abs(np.diff(gate, prepend=gate[0])) > 0.004)[0][::40]
    for e in edges:
        L = int(rng.uniform(0.003, 0.012) * SR)
        place(x, highpass(noise(L, rng), 2500) * rng.uniform(0.6, 1.5), e)
    # A high whine from the ballast.
    x += 0.03 * np.sin(2 * np.pi * 15600 * tt) * gate
    return x


def ui_click():
    """A camcorder button: a dry plastic tick, the latch a moment later, a faint body thump.
    Replaces the vanilla button click (assets/minecraft/sounds.json)."""
    rng = np.random.default_rng(71)
    n = int(0.16 * SR)
    x = np.zeros(n)
    press = band(noise(int(0.012 * SR), rng), 1800, 7000) * env_ad(int(0.012 * SR), 0.0003, 0.0025)
    place(x, press, 0.004 * SR)
    place(x, damped_modes(int(0.08 * SR), [(2350, 0.012, 0.5), (3900, 0.008, 0.3), (1150, 0.02, 0.25)], rng), 0.004 * SR)
    latch = band(noise(int(0.01 * SR), rng), 2500, 8000) * env_ad(int(0.01 * SR), 0.0003, 0.002)
    place(x, latch * 0.45, 0.034 * SR)
    place(x, damped_modes(int(0.06 * SR), [(2900, 0.008, 0.25), (1600, 0.012, 0.15)], rng), 0.034 * SR)
    thump = np.sin(2 * np.pi * 110 * t_axis(0.07)) * env_ad(int(0.07 * SR), 0.001, 0.018)
    place(x, thump * 0.35, 0.004 * SR)
    # A breath of tape hiss under it, so it sounds recorded rather than clean.
    x += lowpass(highpass(noise(n, rng), 1500), 7000) * 0.012 * env_ad(n, 0.002, 0.06)
    return small_room(x, rng, length=0.12, decay=0.02, mix=0.12)


SOUNDS = {
    "whisper1": lambda: whisper(1, 3.0, 4.5),
    "whisper2": lambda: whisper(2, 2.4, 6.0),
    "whisper3": lambda: whisper(3, 3.6, 3.5),
    "radio_static1": lambda: radio_static(41, 1.4),
    "radio_static2": lambda: radio_static(42, 1.9),
    "breaker_off": breaker_off,
    "breaker_on": breaker_on,
    "generator_down": generator_down,
    "generator_up": generator_up,
    "heartbeat": heartbeat,
    "knock1": lambda: knock(51, 160),
    "knock2": lambda: knock(52, 205),
    "lamp_buzz1": lambda: lamp_buzz(61, 1.2),
    "lamp_buzz2": lambda: lamp_buzz(62, 1.45),
    "ui_click": ui_click,
}
# 14 files listed in our sounds.json, plus ui_click used by assets/minecraft/sounds.json.
assert len(SOUNDS) == 15


def write(name, x, out_dir, tmp):
    x = np.nan_to_num(x)
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (PEAK_DB / 20)
    wav = Path(tmp) / f"{name}.wav"
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    dur = len(x) / SR
    fade_out = min(0.03, dur * 0.1)
    af = f"afade=t=in:d=0.004,afade=t=out:st={dur - fade_out:.4f}:d={fade_out:.4f}"
    ogg = out_dir / f"{name}.ogg"
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav), "-af", af, "-ac", "1", "-ar", str(SR),
                    "-c:a", "libvorbis", "-q:a", "6", str(ogg)], check=True)
    return ogg


def main():
    root = Path(sys.argv[1]).resolve()
    out_dir = root / "src/main/resources/assets/bodycamhorror/sounds"
    out_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        # Names after the root make only those sounds: python -I tools/assets/make_sounds.py . ui_click
        only = set(sys.argv[2:])
        for name, fn in SOUNDS.items():
            if not only or name in only:
                print("made", write(name, fn(), out_dir, tmp).name)


if __name__ == "__main__":
    main()
