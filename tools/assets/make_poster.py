"""Lobby poster ("field manual") and the new gear item icons. Own drawing, project palette.

Usage: python -I tools/assets/make_poster.py <repo root>
Writes:
  textures/gui/poster.png         1536x768, drawn 6x3 blocks on the lobby wall (PosterRenderer)
  textures/gui/poster_ru.png      the same in Russian (lang key bh.poster picks the texture)
  textures/item/bandage.png       16x16
  textures/item/battery.png       16x16
  textures/item/lobby_poster.png  16x16 (creative item icon)
Text is drawn with the mod's own bitmap fonts (font/horror.png for headings, font/vhs.png for body),
so the poster matches the HUD. Preview: docs/previews/poster.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

BONE = (196, 188, 170)
ASH = (120, 114, 102)
BLOOD = (122, 22, 18)
PAPER = (28, 26, 23)

TITLE = "FIELD MANUAL"
SUBTITLE = "RECOVERED FROM TAPE 07  -  READ BEFORE THE LIGHTS GO"
LEFT = [
    ("h", "THE JOB"),
    ("t", "FIND THE VHS TAPE. LISTEN FOR THE HISS."),
    ("t", "CARRY IT TO THE EXIT BEHIND YOUR SPAWN"),
    ("t", "AND HOLD STILL FOR 3 SECONDS."),
    ("t", "OR LEAVE NO ONE ALIVE."),
    ("g", ""),
    ("h", "CONTROLS"),
    ("k", "O|FLASHLIGHT"),
    ("k", "TAB|GEAR"),
    ("k", "F|BREAKER / GENERATOR"),
    ("k", "R|RELOAD"),
    ("k", "V|MELEE"),
    ("k", "0|VOICE CHAT SETUP"),
    ("k", "RMB|BANDAGE (HOLD) / BATTERY"),
]
RIGHT = [
    ("h", "DANGERS"),
    ("t", "THE DARK FEEDS YOUR FEAR. KEEP A LIGHT."),
    ("t", "HIGH FEAR: SHAKING HANDS, VOICES."),
    ("t", "BREAKERS AND GENERATOR CUT THE LIGHTS."),
    ("t", "THE TAPE HISSES. EVERYONE HEARS IT."),
    ("g", ""),
    ("h", "THE TENANT"),
    ("t", "IT WAKES 30 SECONDS INTO EVERY ROUND."),
    ("t", "IT IS BLIND. IT HEARS LIGHT, RUNNING, GUNSHOTS AND FEAR."),
    ("t", "LAMPS KEEP IT AWAY. IN A BLACKOUT IT HUNTS."),
    ("t", "BEAM IN ITS FACE UP CLOSE: IT FREEZES."),
    ("t", "NEVER WALK ALONE. ALONE, IT TAKES YOU."),
    ("t", "SHOOT IT TO DRIVE IT OFF. IT COMES BACK."),
]

# Russian poster (0.8.0): textures/gui/poster_ru.png, chosen by the lang key bh.poster.
RU = (
    "ПОЛЕВОЕ РУКОВОДСТВО",
    "СНЯТО С КАССЕТЫ 07  -  ПРОЧТИ, ПОКА НЕ ПОГАС СВЕТ",
    [
        ("h", "ЗАДАЧА"),
        ("t", "НАЙДИ ВИДЕОКАССЕТУ. СЛУШАЙ ШИПЕНИЕ."),
        ("t", "ОТНЕСИ ЕЁ К ВЫХОДУ ЗА СВОИМ СПАВНОМ"),
        ("t", "И ПРОСТОЙ НА МЕСТЕ 3 СЕКУНДЫ."),
        ("t", "ИЛИ НЕ ОСТАВЬ НИКОГО В ЖИВЫХ."),
        ("g", ""),
        ("h", "УПРАВЛЕНИЕ"),
        ("k", "O|ФОНАРИК"),
        ("k", "TAB|СНАРЯЖЕНИЕ"),
        ("k", "F|ЩИТОК / ГЕНЕРАТОР"),
        ("k", "R|ПЕРЕЗАРЯДКА"),
        ("k", "V|УДАР ВБЛИЗИ"),
        ("k", "0|НАСТРОЙКА ГОЛОСОВОГО ЧАТА"),
        ("k", "ПКМ|БИНТ (ДЕРЖАТЬ) / БАТАРЕЙКА"),
    ],
    [
        ("h", "ОПАСНОСТИ"),
        ("t", "ТЕМНОТА КОРМИТ СТРАХ. ДЕРЖИ СВЕТ."),
        ("t", "СИЛЬНЫЙ СТРАХ: ДРОЖЬ В РУКАХ, ГОЛОСА."),
        ("t", "ЩИТКИ И ГЕНЕРАТОР ГАСЯТ СВЕТ."),
        ("t", "КАССЕТА ШИПИТ. ЕЁ СЛЫШАТ ВСЕ."),
        ("g", ""),
        ("h", "ЖИЛЕЦ"),
        ("t", "ПРОСЫПАЕТСЯ НА 30-Й СЕКУНДЕ РАУНДА."),
        ("t", "ОН СЛЕП. ОН СЛЫШИТ СВЕТ, БЕГ, ВЫСТРЕЛЫ И СТРАХ."),
        ("t", "ЛАМПЫ ЕГО ОТГОНЯЮТ. ВО ТЬМЕ ОН ОХОТИТСЯ."),
        ("t", "ЛУЧ В ЛИЦО ВПЛОТНУЮ: ОН ЗАМИРАЕТ."),
        ("t", "НЕ ХОДИ ОДИН. ОДИНОЧЕК ОН ЗАБИРАЕТ."),
        ("t", "СТРЕЛЯЙ, ЧТОБЫ ОТОГНАТЬ. ОН ВЕРНЁТСЯ."),
    ],
)


def load_font(path):
    """Glyphs from the font sheet; the character layout comes from the font's json (Cyrillic since 0.8.0)."""
    import json
    chars = json.loads((path.parent.parent.parent / "font" / (path.stem + ".json")).read_text(encoding="utf-8"))
    chars = next(p["chars"] for p in chars["providers"] if p["type"] == "bitmap")
    img = np.array(Image.open(path).convert("RGBA"))
    cw, ch = img.shape[1] // 16, img.shape[0] // len(chars)
    glyphs = {}
    for row, line in enumerate(chars):
        for col, c in enumerate(line):
            g = img[row * ch:(row + 1) * ch, col * cw:(col + 1) * cw, 3] > 0
            cols = np.where(g.any(axis=0))[0]
            width = (cols.max() + 1) if len(cols) else 3
            if c != "\u0000":
                glyphs[c] = (g, width)
    return glyphs, ch


def draw(canvas, glyphs, text, x, y, scale, color, alpha=1.0):
    """Draw text with a bitmap font; returns the end x."""
    for c in text:
        if c == " ":
            x += 4 * scale
            continue
        g, w = glyphs.get(c, glyphs["?"])
        for gy, gx in zip(*np.where(g)):
            px, py = x + gx * scale, y + gy * scale
            canvas[py:py + scale, px:px + scale, :3] = (
                canvas[py:py + scale, px:px + scale, :3] * (1 - alpha) + np.array(color) * alpha)
        x += (w + 1) * scale
    return x


def width(glyphs, text, scale):
    return sum(4 * scale if c == " " else (glyphs.get(c, glyphs["?"])[1] + 1) * scale for c in text)


def poster(root, texts=None, name="poster"):
    title, subtitle, left, right = texts or (TITLE, SUBTITLE, LEFT, RIGHT)
    rng = np.random.default_rng(707)
    a = root / "src/main/resources/assets/bodycamhorror/textures/font"
    horror, _ = load_font(a / "horror.png")
    vhs, _ = load_font(a / "vhs.png")
    W, H = 1536, 768
    img = np.zeros((H, W, 4), float)
    img[..., :3] = PAPER
    img[..., 3] = 255
    # Worn paper: grain, a darker water stain, scuffed edges.
    img[..., :3] *= (1 + (rng.random((H, W, 1)) - 0.5) * 0.16)
    yy, xx = np.mgrid[0:H, 0:W]
    stain = np.exp(-(((xx - 1200) / 280.0) ** 2 + ((yy - 600) / 140.0) ** 2))
    img[..., :3] *= (1 - 0.25 * stain[..., None])
    edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy))
    img[..., :3] *= np.clip(0.55 + edge / 80.0, 0.55, 1.0)[..., None]
    # Frame line and tape strips in the corners.
    for t in (20, 21, 22):
        img[t, t:W - t, :3] = ASH
        img[H - 1 - t, t:W - t, :3] = ASH
        img[t:H - t, t, :3] = ASH
        img[t:H - t, W - 1 - t, :3] = ASH
    img[12:H - 12, 12:W - 12, :3] *= 1.0
    for (cx, cy) in ((28, 28), (W - 108, 28), (28, H - 56), (W - 108, H - 56)):
        img[cy:cy + 28, cx:cx + 80, :3] = img[cy:cy + 28, cx:cx + 80, :3] * 0.4 + np.array((88, 84, 74)) * 0.6

    # Title and subtitle (horror font, centred).
    s = 8
    tw = width(horror, title, s)
    draw(img, horror, title, (W - tw) // 2 + 4, 52 + 4, s, (10, 10, 10), 0.6)
    draw(img, horror, title, (W - tw) // 2, 52, s, BONE)
    sw = width(vhs, subtitle, 3)
    draw(img, vhs, subtitle, (W - sw) // 2, 140, 3, BLOOD)
    img[184:188, 80:W - 80, :3] = ASH

    col_w = W // 2 - 110

    def wrap(text, scale):
        out, line = [], ""
        for word in text.split(" "):
            cand = (line + " " + word).strip() if line else word
            if line and width(vhs, cand, scale) > col_w:
                out.append(line)
                line = word
            else:
                line = cand
        if line:
            out.append(line)
        return out

    def column(lines, x0):
        y = 214
        for kind, text in lines:
            if kind == "g":
                y += 20
                continue
            if kind == "h":
                draw(img, horror, text, x0, y, 5, BLOOD)
                y += 56
            elif kind == "k":
                key, action = text.split("|")
                draw(img, vhs, "[" + key + "]", x0, y, 3, ASH)
                draw(img, vhs, action, x0 + 130, y, 3, BONE)
                assert 130 + width(vhs, action, 3) <= col_w + 40, f"{name}: key line too wide: {action}"
                y += 31
            else:
                for part in wrap(text, 3):
                    draw(img, vhs, part, x0, y, 3, BONE)
                    y += 31
        # The last line (21 px high, 31 px step) must end above the bottom frame line area (H - 60).
        assert y - 10 <= H - 60, f"{name}: column runs to y={y - 10}"

    column(left, 72)
    img[214:H - 60, W // 2 - 6:W // 2 - 2, :3] = ASH
    column(right, W // 2 + 30)

    out = root / f"src/main/resources/assets/bodycamhorror/textures/gui/{name}.png"
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(out, optimize=True)
    return out


def icon(draw_fn, seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((16, 16, 4), np.uint8)
    draw_fn(img, rng)
    return img


def bandage(img, rng):
    # Rolled bandage: bone roll seen from the side, loose end, a dried blood stain.
    for y in range(4, 12):
        for x in range(3, 13):
            img[y, x] = (*BONE, 255)
    img[4, 3:13, :3] = (226, 220, 204)
    img[11, 3:13, :3] = (150, 143, 126)
    img[4:12, 3, :3] = (150, 143, 126)
    img[4:12, 12, :3] = (150, 143, 126)
    for y in (6, 8, 10):
        img[y, 4:12, :3] = (170, 163, 146)
    img[11:14, 9:14] = (*BONE, 255)
    img[13, 9:14, :3] = (150, 143, 126)
    for (x, y) in ((7, 7), (8, 7), (8, 8), (7, 8), (9, 8), (8, 9)):
        img[y, x, :3] = BLOOD
    img[6, 6, :3] = (98, 20, 16)


def battery(img, rng):
    # Worn AA cell lying diagonal-free: dark body, faded bone band, metal cap.
    for y in range(3, 14):
        for x in range(5, 11):
            img[y, x] = (40, 38, 34, 255)
    img[3:14, 5, :3] = (24, 23, 21)
    img[3:14, 10, :3] = (24, 23, 21)
    img[6:9, 5:11, :3] = (150, 143, 126)
    img[2, 7:9] = (140, 136, 128, 255)
    img[3, 6:10, :3] = (110, 106, 98)
    img[13, 6:10, :3] = (90, 86, 80)
    img[11, 7, :3] = (70, 66, 60)


def poster_icon(img, rng):
    img[2:14, 1:15] = (*PAPER, 255)
    img[2, 1:15, :3] = ASH
    img[13, 1:15, :3] = ASH
    img[2:14, 1, :3] = ASH
    img[2:14, 14, :3] = ASH
    img[4, 4:12, :3] = BONE
    for y in (7, 9, 11):
        img[y, 3:13, :3] = (120, 114, 102)
    img[7, 3:6, :3] = BLOOD


def main():
    root = Path(sys.argv[1]).resolve()
    out = poster(root)
    out_ru = poster(root, RU, "poster_ru")
    items = root / "src/main/resources/assets/bodycamhorror/textures/item"
    for name, fn, seed in (("bandage", bandage, 1), ("battery", battery, 2), ("lobby_poster", poster_icon, 3)):
        Image.fromarray(icon(fn, seed), "RGBA").save(items / f"{name}.png", optimize=True)
    prev = root / "docs/previews"
    prev.mkdir(parents=True, exist_ok=True)
    Image.open(out).convert("RGB").save(prev / "poster.png")
    Image.open(out_ru).convert("RGB").save(prev / "poster_ru.png")
    print("poster", out, out_ru)


if __name__ == "__main__":
    main()
