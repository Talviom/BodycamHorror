"""Briefing folder 2.0 (screen textures, own drawing, project palette): an old case file opened on a dark desk.

  textures/gui/folder_cover.png   256x320  closed cardboard case file: printed frame, string ties, stains
  textures/gui/folder_spread.png  512x320  the file opened: cardboard inside cover (left), typed sheet (right),
                                           sheets under it, binding prongs in the middle, water and coffee stains
  textures/gui/folder_photo.png    96x72   grainy black-and-white photo of a dark corridor, white border
  textures/gui/folder_clip.png     16x40   bent paperclip
  textures/gui/folder_stamp.png   128x48   faded red rectangular stamp frame (words come from lang files)
  textures/gui/folder_slip.png    128x56   torn squared-paper slip for pencil notes
Text is never baked in: the game writes it from lang files (BriefingScreen).
Run: python -I tools/assets/make_folder.py [--mock <dir>]   (--mock also writes rough previews of the screen)
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/bodycamhorror/textures/gui"
R = np.random.default_rng(1986)

BONE = np.array([196, 188, 170], np.float32)
CARD = np.array([118, 98, 66], np.float32)     # old manila cardboard, darkened
CARD_IN = np.array([136, 116, 82], np.float32)
PAPER = np.array([188, 176, 146], np.float32)
BLOOD = np.array([122, 22, 18], np.float32)


def noise(h, w, scale):
    g = max(2, int(max(h, w) / scale))
    small = R.random((g + 1, g + 1)).astype(np.float32)
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(img, np.float32) / 255.0


def surface(h, w, color, grain=9, blotch=0.12):
    img = np.zeros((h, w, 4), np.float32)
    img[..., :3] = color
    img[..., 3] = 255
    img[..., :3] *= (1 - blotch / 2 + blotch * noise(h, w, 10))[..., None]
    img[..., :3] += R.normal(0, grain, (h, w, 1))
    return img


def stain(img, cx, cy, r, strength=26, tint=(0.7, 0.85, 1.15)):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    wob = 1 + 0.25 * (noise(h, w, 6) - 0.5)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / (r * wob)
    body = np.clip(1 - d, 0, 1) ** 1.5 * strength * 0.5
    rim = np.clip(1 - np.abs(d - 1) * 9, 0, 1) * strength
    img[..., :3] -= (body + rim)[..., None] * np.array(tint)


def ring(img, cx, cy, r, strength=34):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.abs(np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) - r)
    gap = (np.arctan2(yy - cy, xx - cx) % (2 * np.pi)) > 5.2  # a coffee ring is never closed
    v = np.clip(1 - d / 2.4, 0, 1) * strength * np.where(gap, 0.25, 1.0)
    img[..., :3] -= v[..., None] * np.array([0.55, 0.8, 1.2])


def edges(img, k=0.75, width=0.07):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    e = np.clip(np.minimum(np.minimum(xx, w - 1 - xx) / (w * width), np.minimum(yy, h - 1 - yy) / (h * width)), 0, 1)
    img[..., :3] *= (k + (1 - k) * e)[..., None]


def ragged(img, side="right", depth=4):
    """Cut a torn edge into the alpha channel."""
    h, w = img.shape[:2]
    prof = (noise(1, h if side in ("left", "right") else w, 3)[0] * depth + R.random(h if side in ("left", "right") else w) * 1.5).astype(int)
    for i, d in enumerate(prof):
        if side == "right":
            img[i, w - d:, 3] = 0
        elif side == "bottom":
            img[h - d:, i, 3] = 0


def scuffs(img, n=30, light=True):
    h, w = img.shape[:2]
    for _ in range(n):
        x, y = R.integers(0, w), R.integers(0, h)
        L = R.integers(3, 14)
        dx, dy = R.choice([-1, 1]), R.uniform(-0.4, 0.4)
        for i in range(L):
            xi, yi = int(x + i * dx), int(y + i * dy)
            if 0 <= xi < w and 0 <= yi < h:
                img[yi, xi, :3] *= 1.18 if light else 0.75


def mold(img, n=40):
    h, w = img.shape[:2]
    for _ in range(n):
        x, y = R.integers(0, w), R.integers(int(h * 0.6), h)
        img[y, x, :3] *= R.uniform(0.55, 0.8)


def string_tie(img, x, y, up=True):
    d = ImageDraw.Draw(im := Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA"))
    pts = [(x, y)]
    for i in range(1, 7):
        pts.append((x + R.uniform(-3, 3) + i * 1.5, y + (i * 6 if up else -i * 6)))
    d.line(pts, fill=(170, 160, 136, 255), width=2)
    d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(62, 58, 50, 255))
    return np.asarray(im, np.float32)


def cover():
    h, w = 320, 256
    img = surface(h, w, CARD, 10, 0.18)
    # Printed frame and lines of an old "ДЕЛО" cover (the words are written by the game).
    d = ImageDraw.Draw(im := Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA"))
    ink = (44, 38, 30, 255)
    d.rectangle([18, 18, w - 19, h - 19], outline=ink, width=2)
    d.rectangle([24, 24, w - 25, h - 25], outline=(60, 52, 40, 255), width=1)
    for y in (150, 176, 202, 228):
        d.line([(48, y), (w - 48, y)], fill=(70, 60, 46, 255), width=1)
    img = np.asarray(im, np.float32).copy()
    stain(img, 70, 250, 46, 24)
    ring(img, 196, 92, 26)
    scuffs(img, 60)
    mold(img, 70)
    edges(img, 0.6, 0.06)
    img = string_tie(img, w - 6, 150, True)
    img = string_tie(img, w - 6, 170, False)
    return img


def spread():
    h, w = 320, 512
    img = surface(h, w, CARD_IN, 9, 0.16)
    # Left: inside of the cover, pale rectangles where something was glued and torn off.
    img[40:92, 150:226, :3] *= 1.06
    stain(img, 60, 260, 52, 22)
    scuffs(img, 50)
    # Middle: a shadowed fold with two metal binding prongs.
    yy, xx = np.mgrid[0:h, 0:w]
    fold = np.exp(-((xx - 256) / 7.0) ** 2)
    img[..., :3] *= (1 - 0.45 * fold)[..., None]
    for py in (60, 260):
        img[py - 2:py + 2, 250:282, :3] = [96, 94, 88]
        img[py - 2:py - 1, 250:282, :3] = [150, 146, 136]
    # Right: three sheets, slightly offset, the top one typed on (text by the game).
    for i, (ox, oy, col) in enumerate([(6, 8, PAPER * 0.82), (3, 4, PAPER * 0.9), (0, 0, PAPER)]):
        x0, y0, x1, y1 = 268 + ox, 10 + oy, w - 12 + ox, h - 10 + oy
        sheet = surface(y1 - y0, x1 - x0, col, 8, 0.14)
        if i == 2:
            ring(sheet, 196, 246, 24, 30)
            stain(sheet, 30, 280, 40, 16)
            # Punched holes near the binding.
            for py in (50, 250):
                sheet[py - 3:py + 3, 6:12, :3] = [40, 34, 26]
            mold(sheet, 30)
            ragged(sheet, "right", 4)
        edges(sheet, 0.82, 0.05)
        a = sheet[..., 3:4] / 255.0
        img[y0:y1, x0:x1, :3] = img[y0:y1, x0:x1, :3] * (1 - a) + sheet[..., :3] * a
        # Shadow under each sheet.
        img[y1:y1 + 2, x0 + 2:x1, :3] *= 0.7
    edges(img, 0.62, 0.05)
    return img


def photo():
    h, w = 72, 96
    img = np.zeros((h, w, 4), np.float32)
    img[..., 3] = 255
    img[..., :3] = [214, 206, 188]
    # Inside the border: a dark corridor vanishing to a dim point, heavy grain.
    ih, iw = h - 10, w - 10
    yy, xx = np.mgrid[0:ih, 0:iw]
    cx, cy = iw * 0.55, ih * 0.45
    depth = np.maximum(np.abs(xx - cx) / iw, np.abs(yy - cy) / ih)
    lum = 22 + 170 * np.clip(0.5 - depth, 0, 1) ** 1.6 + R.normal(0, 10, (ih, iw))
    # Floor tiles and a door frame on the right wall.
    lum[(yy > cy + 6) & ((xx + yy) % 9 == 0)] *= 0.7
    lum[int(cy - 10):int(cy + 14), int(cx + 14):int(cx + 16)] += 20
    lum = np.clip(lum, 0, 255)
    img[5:5 + ih, 5:5 + iw, :3] = lum[..., None] * np.array([1.0, 0.97, 0.9])
    edges(img, 0.8, 0.12)
    return img


def clip():
    im = Image.new("RGBA", (16, 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = (150, 148, 140, 255)
    d.rounded_rectangle([2, 1, 13, 38], 5, outline=c, width=2)
    d.rounded_rectangle([5, 6, 11, 30], 3, outline=(120, 118, 110, 255), width=1)
    return np.asarray(im, np.float32).copy()


def stamp():
    h, w = 48, 128
    img = np.zeros((h, w, 4), np.float32)
    img[..., :3] = BLOOD
    m = np.zeros((h, w), np.float32)
    m[2:5, 2:w - 2] = m[h - 5:h - 2, 2:w - 2] = 1
    m[2:h - 2, 2:5] = m[2:h - 2, w - 5:w - 2] = 1
    m[8:9, 8:w - 8] = m[h - 9:h - 8, 8:w - 8] = 0.8
    ink = np.clip(noise(h, w, 4) * 1.6 - 0.25, 0, 1)  # uneven ink, half the frame never printed
    img[..., 3] = m * ink * 210
    return img


def slip():
    h, w = 56, 128
    img = surface(h, w, np.array([200, 194, 172], np.float32), 6, 0.1)
    for y in range(4, h, 6):
        img[y, :, :3] *= 0.86
    for x in range(4, w, 6):
        img[:, x, :3] *= 0.9
    img[:, 10:11, :3] = img[:, 10:11, :3] * 0.6 + BLOOD * 0.4  # margin line
    ragged(img, "bottom", 4)
    ragged(img, "right", 3)
    edges(img, 0.85, 0.08)
    return img


def save(img, name):
    ROOT.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(ROOT / name)
    print("wrote", name)


def mock(out):
    """Rough preview of the screen at 1280x720 with a stand-in pixel font (the game uses its own font)."""
    from PIL import ImageFont
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 9)
    big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 13)

    def pix(im):
        return im.resize((im.width * 2, im.height * 2), Image.NEAREST)

    def bg():
        return Image.new("RGBA", (640, 360), (9, 8, 7, 255))

    def finish(canvas, name):
        a = np.asarray(canvas, np.float32).copy()
        a[..., :3] *= 0.86
        a[..., :3] += R.normal(0, 7, a.shape[:2] + (1,))
        edges(a, 0.35, 0.22)
        pix(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")).save(Path(out) / name)
        print("mock", name)

    c = bg()
    cov = Image.open(ROOT / "folder_cover.png").resize((240, 300))
    c.alpha_composite(cov, (200, 26))
    d = ImageDraw.Draw(c)
    d.text((320, 70), "ДЕЛО № 1987-Б", font=big, fill=(40, 34, 26), anchor="mm")
    d.text((320, 92), "ОТРЯД. ИНСТРУКТАЖ", font=font, fill=(52, 44, 34), anchor="mm")
    for i, s in enumerate(["Прочитать до выхода.", "В здание папку не брать.", "Начато: 14.10.1987", "Окончено: —"]):
        d.text((250, 152 + i * 24), s, font=font, fill=(50, 42, 32))
    st = Image.fromarray(np.clip(stamp(), 0, 255).astype(np.uint8), "RGBA").rotate(8, expand=True)
    c.alpha_composite(st, (300, 234))
    d.text((350, 258), "ДЛЯ СЛУЖЕБНОГО\nПОЛЬЗОВАНИЯ", font=font, fill=(122, 22, 18, 200), anchor="mm", align="center")
    finish(c, "folder_mock_cover.png")

    c = bg()
    sp = Image.open(ROOT / "folder_spread.png").resize((560, 350))
    c.alpha_composite(sp, (40, 5))
    d = ImageDraw.Draw(c)
    ph = Image.open(ROOT / "folder_photo.png").rotate(-4, expand=True)
    c.alpha_composite(ph, (78, 30))
    c.alpha_composite(Image.open(ROOT / "folder_clip.png").rotate(-10, expand=True), (90, 18))
    d.text((80, 116), "корп. 2, 2-й этаж. 03:12", font=font, fill=(36, 40, 58))
    for i, note in enumerate(["Не ходите по одному.\nСерёга пошёл один.", "Если тихо, это плохо."]):
        sl = Image.open(ROOT / "folder_slip.png")
        sl = sl.rotate([-3, 2][i], expand=True)
        c.alpha_composite(sl, (70 + i * 60, 160 + i * 70))
        d.text((86 + i * 60, 172 + i * 70), note, font=font, fill=(36, 40, 58))
    st = Image.fromarray(np.clip(stamp(), 0, 255).astype(np.uint8), "RGBA").rotate(-12, expand=True)
    c.alpha_composite(st, (170, 270))
    d.text((236, 300), "ВЫНОС ЗАПРЕЩЁН", font=font, fill=(122, 22, 18, 190), anchor="mm")
    x0 = 352
    d.text((x0, 30), "1. ЗАДАЧА", font=big, fill=(38, 32, 26))
    d.line([(x0, 47), (x0 + 74, 48)], fill=(122, 22, 18), width=1)
    body = ["Найти видеокассету. Она шипит,", "идите на звук.", "", "Донести кассету до выхода за", "своим спавном и простоять у", "выхода 3 секунды.", "",
            "Раунд засчитывается и тогда,", "когда из чужого отряда в живых", "не осталось никого.", "", "Кто несёт кассету, того слышно.", "Убитый роняет её на месте."]
    for i, s in enumerate(body):
        d.text((x0, 58 + i * 12), s, font=font, fill=(40, 34, 28))
    d.rectangle([x0, 230, x0 + 150, 237], fill=(22, 20, 18))
    d.rectangle([x0, 242, x0 + 96, 249], fill=(22, 20, 18))
    d.text((x0 + 150, 320), "лист 2", font=font, fill=(90, 80, 66))
    d.text((x0 - 16, 120), "!", font=big, fill=(122, 22, 18))
    finish(c, "folder_mock_page.png")


def main():
    save(cover(), "folder_cover.png")
    save(spread(), "folder_spread.png")
    save(photo(), "folder_photo.png")
    save(clip(), "folder_clip.png")
    save(stamp(), "folder_stamp.png")
    save(slip(), "folder_slip.png")
    if "--mock" in sys.argv:
        mock(sys.argv[sys.argv.index("--mock") + 1])


if __name__ == "__main__":
    main()
