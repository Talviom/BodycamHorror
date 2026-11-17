"""Модель шкалы страха вне игры: те же формулы, что в horror/FearSystem.java, для быстрого подбора баланса.

    python tools/tests/fear_sim.py [out_dir]

Считает раунд 180 с для трёх случаев (как в сценарии fear_round.txt) по старым и новым настройкам,
печатает сводку по 200 прогонам и рисует график одного прогона: fear_sim_old.png, fear_sim_new.png.
После правки FearSystem.java поправь и NEW здесь, а потом проверь в игре сценарием fear_round.txt.
"""
import random
import sys
from pathlib import Path

ROUND = 180
QUIET = 20
DT = 0.5

OLD = dict(name="СЕЙЧАС (0.5.x)", dark=4.0, alone=1.0, still=0.5, last_minute=0.5, calm=3.0, ally_calm=1.0,
           headroom=None, events_from=20, first=(5, 12),
           tiers=[(45, (20, 35), "weak"), (70, (14, 24), "medium"), (101, (9, 16), "strong")],
           spike=4, relief=None, strong_cap=None, no_repeat=False, heart_from=55, heart=(24, 18, 14))

NEW = dict(name="ПРЕДЛАГАЮ", dark=2.0, alone=0.6, still=0.3, last_minute=0.4, calm=2.5, ally_calm=1.0,
           headroom=130, events_from=25, first=(6, 14),
           tiers=[(50, (30, 45), "weak"), (75, (22, 32), "medium"), (101, (16, 24), "strong")],
           spike=4, relief={"weak": 10, "medium": 14, "strong": 20}, relief_calm=1.5, strong_cap=3, no_repeat=True,
           heart_from=65, heart=(30, 24, 18))

EVENTS = {"weak": ["knock", "whisper"], "medium": ["whisper", "footsteps", "footsteps"],
          "strong": ["knock", "glitch", "radio", "flicker", "footsteps"]}

CASES = {
    "A1": dict(label="вдвоём в темноте", dark=True, ally_close=True, flashlight_until=0),
    "B1": dict(label="один в темноте", dark=True, ally_close=False, flashlight_until=0),
    "B2": dict(label="один, фонарик 90 с", dark=True, ally_close=False, flashlight_until=90),
}


def run(cfg, case, rnd):
    fear, t, next_at, relief_until = 0.0, 0.0, None, -1.0
    last_ev, strong_used, still_since = None, 0, 0.0
    trace, events, heart_beats = [], [], 0
    heart_acc = 0.0
    while t < ROUND:
        light_on = t < case["flashlight_until"]
        dark = case["dark"] and not light_on
        still = t - still_since > 10
        gain = 0.0
        if dark:
            gain += cfg["dark"]
        if not case["ally_close"]:
            gain += cfg["alone"]
        if still and not light_on:
            gain += cfg["still"]
        if ROUND - t < 60:
            gain += cfg["last_minute"]
        if cfg["headroom"]:
            gain *= max(0.0, 1 - fear / cfg["headroom"])
        calm = (0.0 if dark else cfg["calm"]) + (cfg["ally_calm"] if case["ally_close"] else 0.0)
        if cfg["relief"] and t < relief_until:
            # После испуга отпускает: страх не растёт и понемногу спадает (пила напряжения).
            gain = 0.0
            calm += cfg["relief_calm"]
        fear = min(100.0, max(0.0, fear + (gain - calm) * DT))

        # события
        if fear < cfg["events_from"]:
            next_at = None
        elif next_at is None:
            next_at = t + rnd.uniform(*cfg["first"])
        elif t >= next_at and t >= QUIET and not (cfg["relief"] and t < relief_until):
            for top, (lo, hi), tier in cfg["tiers"]:
                if fear < top:
                    break
            if tier == "strong" and cfg["strong_cap"] is not None and strong_used >= cfg["strong_cap"]:
                tier, (lo, hi) = "medium", cfg["tiers"][1][1]
            pool = [e for e in EVENTS[tier] if not (cfg["no_repeat"] and e == last_ev)] or EVENTS[tier]
            ev = rnd.choice(pool)
            if tier == "strong":
                strong_used += 1
            events.append((t, ev, fear))
            last_ev = ev
            fear = min(100.0, fear + cfg["spike"])
            next_at = t + rnd.uniform(lo, hi)
            if cfg["relief"]:
                relief_until = t + cfg["relief"][tier]

        # сердце
        if fear >= cfg["heart_from"]:
            period = cfg["heart"][2] if fear >= 85 else cfg["heart"][1] if fear >= 70 else cfg["heart"][0]
            heart_acc += DT * 20
            while heart_acc >= period:
                heart_acc -= period
                heart_beats += 1
        trace.append((t, fear))
        t += DT
    return trace, events, heart_beats


def first_time(trace, level):
    for t, v in trace:
        if v >= level:
            return t
    return None


def summary(cfg):
    rnd = random.Random(1)
    print(f"== {cfg['name']}")
    for key, case in CASES.items():
        evs, hearts, t50, t75, top = [], [], [], [], []
        for _ in range(200):
            trace, events, beats = run(cfg, case, rnd)
            evs.append(len(events))
            hearts.append(beats)
            top.append(sum(1 for _, v in trace if v >= 75) * DT)
            a, b = first_time(trace, 50), first_time(trace, 75)
            if a is not None:
                t50.append(a)
            if b is not None:
                t75.append(b)
        avg = lambda xs: sum(xs) / len(xs) if xs else float("nan")
        print(f"  {key} {case['label']:<20} событий {avg(evs):4.1f}  до 50: {avg(t50):5.1f} с  до 75: {avg(t75):5.1f} с"
              f"  выше 75: {avg(top):5.1f} с  ударов сердца: {avg(hearts):5.0f}")


def chart(cfg, out):
    from PIL import Image, ImageDraw, ImageFont
    colors = {"A1": (196, 188, 170), "B1": (122, 22, 18), "B2": (120, 114, 102)}
    W, H, L, T, R, B = 1100, 460, 60, 50, 280, 50
    img = Image.new("RGB", (W, H), (20, 19, 17))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 15)
    pw, ph = W - L - R, H - T - B

    def xy(t, v):
        return L + pw * t / ROUND, T + ph * (1 - v / 100)

    for v in range(0, 101, 25):
        y = xy(0, v)[1]
        d.line([(L, y), (L + pw, y)], fill=(45, 43, 39))
        d.text((L - 34, y - 8), str(v), fill=(120, 114, 102), font=font)
    for t in range(0, ROUND + 1, 30):
        x = xy(t, 0)[0]
        d.line([(x, T), (x, T + ph)], fill=(45, 43, 39))
        d.text((x - 12, T + ph + 8), f"{t}s", fill=(120, 114, 102), font=font)
    d.text((L, 15), f"Страх за раунд 180 с: {cfg['name']}", fill=(196, 188, 170), font=font)
    ly = T
    for key, case in CASES.items():
        trace, events, beats = run(cfg, case, random.Random(7))
        d.line([xy(t, v) for t, v in trace], fill=colors[key], width=3)
        for t, ev, v in events:
            x, y = xy(t, v)
            d.ellipse([x - 5, y - 5, x + 5, y + 5], outline=colors[key], width=2)
        d.text((L + pw + 15, ly), f"{key}: {case['label']}", fill=colors[key], font=font)
        d.text((L + pw + 15, ly + 18), f"событий: {len(events)}", fill=colors[key], font=font)
        ly += 50
    d.text((L + pw + 15, ly + 10), "кружок = событие", fill=(120, 114, 102), font=font)
    img.save(out)


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    for cfg, name in ((OLD, "fear_sim_old.png"), (NEW, "fear_sim_new.png")):
        summary(cfg)
        chart(cfg, out / name)
