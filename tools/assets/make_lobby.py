"""Lobby 1.0 textures (own drawing, project palette): wall sheets and the briefing folder.

  textures/block/brief_memo.png     256x256  yellowed typed memo sheet (text is drawn by the game from lang files)
  textures/block/brief_note.png     128x128  torn notebook scrap with a ragged edge (cutout)
  textures/block/brief_photo.png    128x160  faded portrait photo with a white border, caption strip at the bottom
  textures/block/brief_plan.png     256x192  hand-drawn floor plan of the hospital (labels drawn by the game)
  textures/block/brief_label.png    128x32   small paper label strip
  textures/item/briefing_folder.png 16x16    cardboard folder with a string
  textures/gui/briefing_page.png    256x320  folder page background for the screen
Run: python tools/assets/make_lobby.py
"""
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/bodycamhorror/textures"
rng = np.random.default_rng(1977)


def paper(w, h, base=(196, 184, 150), grain=10, stains=3):
    img = np.zeros((h, w, 4), np.float32)
    img[..., :3] = base
    img[..., 3] = 255
    n = rng.normal(0, grain, (h, w, 1))
    img[..., :3] += n
    # slow blotches: aged paper is uneven
    yy, xx = np.mgrid[0:h, 0:w]
    for _ in range(stains):
        cx, cy, r = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(w * 0.15, w * 0.5)
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
        img[..., :3] -= (np.clip(1 - d, 0, 1) ** 2 * rng.uniform(10, 28))[..., None] * np.array([0.8, 0.9, 1.1])
    # darker edges
    ex = np.minimum(xx, w - 1 - xx) / (w * 0.08)
    ey = np.minimum(yy, h - 1 - yy) / (h * 0.08)
    edge = np.clip(np.minimum(ex, ey), 0, 1)
    img[..., :3] *= (0.78 + 0.22 * edge)[..., None]
    return img


def coffee_ring(img, cx, cy, r):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.abs(np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) - r)
    ring = np.clip(1 - d / 3.0, 0, 1) * 30
    img[..., :3] -= ring[..., None] * np.array([0.6, 0.8, 1.2])


def save(img, rel):
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(p)
    print("wrote", p)


def memo():
    img = paper(256, 256)
    coffee_ring(img, 196, 206, 26)
    # header rule and a faint stamp box, text itself comes from the game
    img[30:32, 14:242, :3] -= 70
    img[226:227, 14:242, :3] -= 40
    yy, xx = np.mgrid[0:256, 0:256]
    stamp = (np.abs(np.sqrt((xx - 214) ** 2 + (yy - 40) ** 2) - 17) < 1.6)
    img[stamp, 0] -= 10
    img[stamp, 1] -= 55
    img[stamp, 2] -= 55
    # pin hole and rust at the top
    img[6:10, 126:130, :3] = (60, 50, 40)
    save(img, "block/brief_memo.png")


def note():
    w = h = 128
    img = paper(w, h, base=(204, 196, 170), grain=8, stains=2)
    for y in range(22, h, 13):  # faint ruled lines
        img[y, :, 0] -= 30
        img[y, :, 1] -= 20
        img[y, :, 2] -= 4
    img[:, 14, 0] += 0
    img[:, 14, 1] -= 40
    img[:, 14, 2] -= 40  # margin line, faded red
    # ragged torn top edge
    edge = (rng.normal(0, 2.5, w).cumsum() * 0.35 + 6).clip(2, 12).astype(int)
    for x in range(w):
        img[: edge[x], x, 3] = 0
        img[edge[x]:edge[x] + 2, x, :3] -= 25
    save(img, "block/brief_note.png")


def photo():
    w, h = 128, 160
    img = np.zeros((h, w, 4), np.float32)
    img[..., :3] = (200, 194, 178)
    img[..., 3] = 255
    img[..., :3] += rng.normal(0, 6, (h, w, 1))
    # the picture itself: dark grey, faded, a head-and-shoulders silhouette, scratches
    x0, y0, x1, y1 = 8, 8, w - 8, 118
    pic = np.full((y1 - y0, x1 - x0), 92.0) + rng.normal(0, 10, (y1 - y0, x1 - x0))
    yy, xx = np.mgrid[0:y1 - y0, 0:x1 - x0]
    cx = (x1 - x0) / 2
    head = ((xx - cx) / 19) ** 2 + ((yy - 42) / 24) ** 2 < 1
    body = (((xx - cx) / 44) ** 2 + ((yy - 118) / 50) ** 2 < 1)
    pic[head | body] -= 46
    pic -= np.clip(1 - np.sqrt(((xx - cx) / cx) ** 2 + ((yy - 55) / 70) ** 2), 0, 1) * -18
    for _ in range(14):
        sx = rng.integers(0, x1 - x0)
        pic[:, max(0, sx - 0):sx + 1] += rng.uniform(15, 40) * (rng.random(y1 - y0) < 0.6)[:, None]
    pic = np.array(Image.fromarray(np.clip(pic, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), np.float32)
    img[y0:y1, x0:x1, 0] = pic * 1.02
    img[y0:y1, x0:x1, 1] = pic * 0.98
    img[y0:y1, x0:x1, 2] = pic * 0.9
    # a corner torn off and a bit of tape
    for i in range(18):
        img[h - 1 - i, w - 18 + i:, 3] = 0
    img[0:10, 50:78, :3] = (170, 165, 140)
    save(img, "block/brief_photo.png")


def plan():
    w, h = 256, 192
    img = paper(w, h, base=(188, 180, 154), grain=7, stains=2)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(im)
    ink = (52, 48, 44, 255)
    s = 2.4  # plan units -> px ; site x 0..88, z -8..62

    def P(x, z):
        return (int(18 + x * s * 0.92), int(12 + (z + 8) * s * 0.95))

    def rect(x1, z1, x2, z2, width=2, fill=None):
        d.rectangle([P(x1, z1), P(x2, z2)], outline=ink, width=width, fill=fill)

    # fence (dashed)
    for x in range(0, 88, 4):
        d.line([P(x, -8), P(x + 2, -8)], fill=ink, width=1)
        d.line([P(x, 62), P(x + 2, 62)], fill=ink, width=1)
    for z in range(-8, 62, 4):
        d.line([P(0, z), P(0, z + 2)], fill=ink, width=1)
        d.line([P(88, z), P(88, z + 2)], fill=ink, width=1)
    rect(12, 8, 76, 52, 3)                       # building
    rect(26, 22, 62, 38, 2, fill=(170, 164, 140, 255))  # courtyard
    rect(39, 40, 49, 52, 1)                      # hall
    rect(34, -6, 56, 4, 2)                       # outbuilding
    rect(42, 4, 46, 8, 1)                        # covered passage
    # spawn rooms hatched
    for z in range(22, 38, 3):
        d.line([P(13, z), P(16, z + 3)], fill=ink, width=1)
        d.line([P(72, z), P(75, z + 3)], fill=ink, width=1)
    # exits: arrows in the side fences
    for x, dx in ((2, -1), (86, 1)):
        a, b = P(x, 30), P(x + dx * 6, 30)
        d.line([a, b], fill=(110, 26, 22, 255), width=3)
    # red pencil marks: crosses
    red = (122, 22, 18, 255)
    for x, z in ((52, -1), (22, 23), (66, 36), (44, 46)):
        cx, cy = P(x, z)
        d.line([cx - 5, cy - 5, cx + 5, cy + 5], fill=red, width=2)
        d.line([cx - 5, cy + 5, cx + 5, cy - 5], fill=red, width=2)
    # pencil circle around the outbuilding
    x1, y1 = P(31, -8)
    x2, y2 = P(59, 6)
    d.ellipse([x1, y1, x2, y2], outline=red, width=1)
    save(np.array(im, np.float32), "block/brief_plan.png")


def label():
    img = paper(128, 32, base=(208, 200, 176), grain=6, stains=1)
    save(img, "block/brief_label.png")


def page():
    img = paper(256, 320, base=(186, 174, 140), grain=9, stains=4)
    coffee_ring(img, 60, 270, 30)
    img[:, 22:24, :3] -= 18  # folder crease
    for y in (18, 19):
        img[y, 30:240, :3] -= 30
    save(img, "gui/briefing_page.png")


def folder_icon():
    px = np.zeros((16, 16, 4), np.uint8)
    body = (120, 100, 70, 255)
    dark = (78, 64, 46, 255)
    for y in range(3, 15):
        for x in range(2, 14):
            px[y, x] = body
    for x in range(2, 7):
        px[2, x] = body
    for y in range(3, 15):
        px[y, 2] = dark
        px[y, 13] = dark
    for x in range(2, 14):
        px[14, x] = dark
    px[3:6, 4:12] = (196, 188, 170, 255)  # paper sticking out
    px[8, 6:10] = (60, 50, 40, 255)        # string
    px[9, 8] = (60, 50, 40, 255)
    px[10, 7] = (122, 22, 18, 255)         # red stamp dot
    Image.fromarray(px, "RGBA").save(ROOT / "item/briefing_folder.png")
    print("wrote folder icon")


if __name__ == "__main__":
    memo()
    note()
    photo()
    plan()
    label()
    page()
    folder_icon()
