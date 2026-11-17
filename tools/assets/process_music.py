"""Old-tape processing for licensed music (ffmpeg only).

Usage:
  python -I tools/assets/process_music.py preview <src_dir> <out_dir>
      35 s excerpts of every candidate, as original and as "tape" (how it would sound in the game).
Later (after the owner picks the tracks), a `build` mode will cut loops and write the final files
into src/main/resources/assets/bodycamhorror/sounds/music/.

The tape treatment (dark found-footage style): slow pitch wow, light flutter, tape hiss, top end
cut around 7 kHz, soft saturation, narrow stereo, normalised to about -16 LUFS.
"""
import json
import subprocess
import sys
from pathlib import Path

# name -> (file, author, licence, page, proposed use, excerpt start in s)
CANDIDATES = [
    ("abandoned", "abandoned_passages__congusbongus__CC0.ogg", "Abandoned passages", "congusbongus", "CC0",
     "https://opengameart.org/content/abandoned-passages-horror-ambience-loop", "лобби (петля)", 0),
    ("searching", "searching__yd__CC0.ogg", "Searching", "yd", "CC0",
     "https://opengameart.org/content/searching", "начало раунда", 0),
    ("insistent", "insistent__yd__CC0.ogg", "Insistent: background loop", "yd", "CC0",
     "https://opengameart.org/content/insistent-background-loop", "меню или лобби (петля)", 10),
    ("horror_loop", "horror_loop__TinyWorlds__CC0.mp3", "Horror Loop", "TinyWorlds", "CC0 (в архиве 16 кГц, качество ниже)",
     "https://opengameart.org/content/horror-loop", "меню (петля)", 10),
    ("cold_silence", "cold_silence__Eponasoft__CC0.ogg", "Cold Silence", "Eponasoft", "CC0",
     "https://opengameart.org/content/cold-silence", "меню или лобби (длинный, 6 мин)", 40),
    ("dark_intro", "dark_intro__Nikke__CC0.ogg", "Dark Intro", "Nikke", "CC0",
     "https://opengameart.org/content/dark-intro", "начало раунда или итог матча", 0),
    ("menacing", "menacing_otherworld__Ruskerdax__CC0.mp3", "Menacing Otherworld", "Ruskerdax", "CC0",
     "https://opengameart.org/content/menacing-otherworld", "меню или конец матча", 20),
    ("dark_ambience", "dark_ambience_loop__Iwan_Gabovitch__CC-BY3.ogg", "Dark Ambience Loop", "Iwan Gabovitch",
     "CC-BY 3.0 / GPL (нужно указать автора)", "https://opengameart.org/content/dark-ambience-loop", "меню (петля)", 0),
]

TAPE = (
    "aformat=sample_rates=44100:channel_layouts=stereo,"
    "vibrato=f=0.45:d=0.06,vibrato=f=6.5:d=0.015,"   # slow wow + light flutter
    "lowpass=f=7000,highpass=f=35,"
    "acompressor=threshold=-22dB:ratio=3:attack=15:release=300:makeup=2,"  # tape squashes peaks
    "asoftclip=type=tanh:threshold=0.8,"             # soft saturation
    "extrastereo=m=0.45"                              # narrow the stereo image
)


def run(args):
    subprocess.run(args, check=True)


def render_normalised(src, graph, out_ogg, tmp_dir):
    """Render filtergraph `graph` (output label [o]) to a fixed WAV first, then two-pass loudnorm that
    file to -16 LUFS. Measuring a fixed file keeps both passes identical (the hiss is random)."""
    wav = Path(tmp_dir) / (Path(out_ogg).stem + ".wav")
    run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-filter_complex", graph, "-map", "[o]", "-ar", "44100", str(wav)])
    probe = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(wav), "-af",
                            "loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json", "-f", "null", "-"],
                           capture_output=True, text=True).stderr
    m = json.loads(probe[probe.rindex("{"): probe.rindex("}") + 1])
    ln = (f"loudnorm=I=-16:TP=-1.5:LRA=11:measured_I={m['input_i']}:measured_TP={m['input_tp']}:"
          f"measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true")
    run(["ffmpeg", "-y", "-v", "error", "-i", str(wav), "-af", ln, "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", str(out_ogg)])
    wav.unlink()


def preview(src_dir, out_dir):
    src_dir, out_dir = Path(src_dir), Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    lines = ["Кандидаты музыки для Bodycam Horror (превью по 35 с: _original как есть, _tape как будет в игре)", ""]
    for i, (key, fname, title, author, lic, url, use, start) in enumerate(CANDIDATES, 1):
        src = src_dir / fname
        tag = f"{i:02d}_{key}"
        cut = (f"aformat=sample_rates=44100:channel_layouts=stereo,atrim=start={start}:duration=35,"
               f"asetpts=PTS-STARTPTS,afade=t=in:d=1.5,afade=t=out:st=32:d=3")
        # Original excerpt (only trimmed, faded and loudness-matched so all previews are equally loud).
        render_normalised(src, f"[0:a]{cut}[o]", out_dir / f"{tag}_original.ogg", out_dir)
        # Tape version: the same excerpt + hiss mixed in (hiss as long as the excerpt).
        g = (f"[0:a]{cut},{TAPE}[t];anoisesrc=c=pink:r=44100:a=0.012,highpass=f=1500,lowpass=f=8000,"
             f"aformat=channel_layouts=stereo[h];[t][h]amix=inputs=2:duration=first:weights=1 1:normalize=0[o]")
        render_normalised(src, g, out_dir / f"{tag}_tape.ogg", out_dir)
        lines.append(f"{i:02d}. {title} — {author} — {lic}")
        lines.append(f"    Страница: {url}")
        lines.append(f"    Предлагаю для: {use}")
        lines.append(f"    Файлы: {tag}_original.ogg, {tag}_tape.ogg")
        lines.append("")
        print("preview", tag)
    lines += ["Стингеры DHSFX (itch.io): не скачаны. Readme просит указать автора и ссылку на SoundCloud в игре,",
              "про видео ничего не сказано, поэтому ждём решения.",
              "Для итогов раунда и матча пока остаются наши синтезированные заглушки (make_music.py)."]
    (out_dir / "list.txt").write_text("\n".join(lines) + "\n", encoding="utf-8-sig")


# ---------------------------------------------------------------- final tracks (owner's picks, 2026-11-17)
# Only CC0 sources (no attribution anywhere, see docs/sources.md). Source files are not committed;
# they live in D:\Minecraft\dev\music_src\opengameart (download links in docs/sources.md).
#   menu          Cold Silence (Eponasoft)            3 min seamless loop
#   lobby         Abandoned passages (congusbongus)   loop, TEMPORARY until the owner's own track
#   round_start_N Searching (yd)                      4 different 15 s pieces, played at random
#   match_won     Dark Intro (Nikke)                  10 s
#   match_lost    Menacing Otherworld (Ruskerdax)     10 s
#   round_won / round_lost stay our own (tools/assets/make_music.py)

FINAL = {
    # name: (source file, start s, length s, mono, mode)
    # mode: "cut" fades in/out, "loop" crossfades its tail into its head, "asis" keeps an existing seamless loop
    "menu": ("cold_silence__Eponasoft__CC0.ogg", 40, 180, False, "loop"),
    "lobby": ("abandoned_passages__congusbongus__CC0.ogg", 0, 31.3, False, "asis"),  # already a seamless loop
    # Round start pieces begin on loud passages so each one starts strongly (loudness envelope checked).
    "round_start_1": ("searching__yd__CC0.ogg", 12, 15, False, "cut"),
    "round_start_2": ("searching__yd__CC0.ogg", 28, 15, False, "cut"),
    "round_start_3": ("searching__yd__CC0.ogg", 56, 15, False, "cut"),
    "round_start_4": ("searching__yd__CC0.ogg", 72, 15, False, "cut"),
    "match_won": ("dark_intro__Nikke__CC0.ogg", 0, 10, False, "cut"),
    "match_lost": ("menacing_otherworld__Ruskerdax__CC0.mp3", 0, 10, False, "cut"),
}
LOOP_XFADE = 4.0


def _decode(path):
    import numpy as np
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "2", "-ar", "44100", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 2).copy()


def _write_wav(path, x):
    import numpy as np
    from scipy.io import wavfile
    wavfile.write(path, 44100, (np.clip(x, -1, 1) * 32767).astype(np.int16))


def build_track(name, src_dir, out_dir, tmp_dir):
    import numpy as np
    fname, start, length, mono, mode = FINAL[name]
    src = Path(src_dir) / fname
    taped = Path(tmp_dir) / f"{name}_taped.wav"
    extra = LOOP_XFADE if mode == "loop" else 0
    fades = f",afade=t=in:d=0.3,afade=t=out:st={length - 2}:d=2" if mode == "cut" else ""
    # The tape effect runs in ffmpeg; the hiss is added in numpy (mixing anoisesrc via amix in the
    # graph turned out to be unreliable: some renders came out almost silent).
    g = (f"[0:a]aformat=sample_rates=44100:channel_layouts=stereo,atrim=start={start}:duration={length + extra},"
         f"asetpts=PTS-STARTPTS,{TAPE}{fades}[o]")
    run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-filter_complex", g, "-map", "[o]", "-ar", "44100", str(taped)])
    x = _decode(taped)
    x = x + _tape_hiss(len(x), seed=sum(map(ord, name)), level=0.12 * float(np.sqrt(np.mean(x ** 2))))
    if mode == "loop" and extra:
        # Fold the extra tail onto the head with an equal-power crossfade: the end flows into the start.
        k = int(LOOP_XFADE * 44100)
        body = x[:-k].copy()
        w = np.linspace(0, 1, k)[:, None]
        body[:k] = x[-k:] * np.sqrt(1 - w) + body[:k] * np.sqrt(w)
        x = body
    if mono:
        x = np.repeat(x.mean(axis=1, keepdims=True), 2, axis=1)
    pre = Path(tmp_dir) / f"{name}_pre.wav"
    _write_wav(pre, x)
    # Normalise: measure integrated loudness, apply one linear gain to -16 LUFS, then a soft limiter
    # so peaks stay under about -1.5 dBFS. (ffmpeg loudnorm falls back to dynamic mode on sources that
    # already hit 0 dBFS and then misses the target, so it is not used here.)
    gain_db = -16.0 - _integrated_lufs(pre)
    x = x * 10 ** (gain_db / 20)
    x = _soft_limit(x, ceiling=10 ** (-1.5 / 20))
    _write_wav(pre, x)
    run(["ffmpeg", "-y", "-v", "error", "-i", str(pre), "-ar", "44100", "-ac", "1" if mono else "2",
         "-c:a", "libvorbis", "-q:a", "4", str(Path(out_dir) / f"{name}.ogg")])
    taped.unlink()
    pre.unlink()


def _tape_hiss(n, seed, level):
    """Band-limited hiss (1.5-8 kHz) with a slow level drift, stereo, at the given RMS level."""
    import numpy as np
    from scipy import signal
    rng = np.random.default_rng(seed)
    sos = signal.butter(2, [1500, 8000], "band", fs=44100, output="sos")
    h = signal.sosfilt(sos, rng.standard_normal((n, 2)), axis=0)
    h *= (0.85 + 0.15 * np.sin(2 * np.pi * 0.13 * np.arange(n) / 44100))[:, None]
    return h / (np.sqrt(np.mean(h ** 2)) + 1e-12) * level


def _integrated_lufs(path):
    out = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path), "-af", "ebur128", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    line = [l for l in out.splitlines() if l.strip().startswith("I:")][-1]
    return float(line.split()[1])


def _soft_limit(x, ceiling=0.84, knee=0.7):
    """Leave everything under `knee * ceiling` untouched, bend louder samples smoothly toward the ceiling."""
    import numpy as np
    t = knee * ceiling
    a = np.abs(x)
    over = a > t
    y = x.copy()
    room = ceiling - t
    y[over] = np.sign(x[over]) * (t + room * np.tanh((a[over] - t) / room))
    return y


def build_all(src_dir, repo_root, names=None):
    import tempfile
    out = Path(repo_root) / "src/main/resources/assets/bodycamhorror/sounds/music"
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name in names or FINAL:
            build_track(name, src_dir, out, tmp)
            print("built", name)


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "preview":
        preview(sys.argv[2], sys.argv[3])
    elif len(sys.argv) >= 4 and sys.argv[1] == "build":
        build_all(sys.argv[2], sys.argv[3], sys.argv[4:] or None)
    else:
        print(__doc__)
