"""График страха по логу сценария fear_round.txt (строки [fear-trace] и [fear-event]).

    python tools/tests/fear_chart.py run/logs/latest.log out.png [подпись]
"""
import re
import sys
from collections import defaultdict

from PIL import Image, ImageDraw, ImageFont

TRACE = re.compile(r"\[fear-trace\] t=(\d+) p=(\w+) fear=(\d+)")
EVENT = re.compile(r"\[fear-event\] t=(\d+) p=(\w+) ev=(\w+) fear=(\d+)")
COLORS = {"A1": (196, 188, 170), "B1": (122, 22, 18), "B2": (120, 114, 102)}
LABELS = {"A1": "A1+A2: вдвоём в темноте", "B1": "B1: один в темноте", "B2": "B2: один, фонарик"}


def main():
    log, out = sys.argv[1], sys.argv[2]
    title = sys.argv[3] if len(sys.argv) > 3 else ""
    fear = defaultdict(dict)
    events = defaultdict(list)
    with open(log, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = TRACE.search(line)
            if m:
                fear[m.group(2)][int(m.group(1))] = int(m.group(3))
            m = EVENT.search(line)
            if m:
                events[m.group(2)].append((int(m.group(1)), m.group(3), int(m.group(4))))

    W, H, L, T, R, B = 1100, 520, 60, 50, 260, 50
    img = Image.new("RGB", (W, H), (20, 19, 17))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 15)
    pw, ph = W - L - R, H - T - B
    tmax = 180

    def xy(t, v):
        return L + pw * t / tmax, T + ph * (1 - v / 100)

    for v in range(0, 101, 20):
        y = xy(0, v)[1]
        d.line([(L, y), (L + pw, y)], fill=(45, 43, 39))
        d.text((L - 34, y - 8), str(v), fill=(120, 114, 102), font=font)
    for t in range(0, tmax + 1, 30):
        x = xy(t, 0)[0]
        d.line([(x, T), (x, T + ph)], fill=(45, 43, 39))
        d.text((x - 10, T + ph + 8), f"{t}s", fill=(120, 114, 102), font=font)
    d.text((L, 15), title, fill=(196, 188, 170), font=font)

    ly = T
    for name, color in COLORS.items():
        pts = sorted(fear[name].items())
        if len(pts) > 1:
            d.line([xy(t, v) for t, v in pts], fill=color, width=3)
        for t, ev, v in events[name]:
            x, y = xy(t, v)
            d.ellipse([x - 5, y - 5, x + 5, y + 5], outline=color, width=2)
        n = len(events[name])
        d.text((L + pw + 15, ly), LABELS[name], fill=color, font=font)
        d.text((L + pw + 15, ly + 18), f"событий: {n}", fill=color, font=font)
        ly += 50
    d.text((L + pw + 15, ly + 10), "кружок = событие", fill=(120, 114, 102), font=font)
    img.save(out)
    for name in COLORS:
        print(name, "events:", [(t, e) for t, e, _ in events[name]])


if __name__ == "__main__":
    main()
