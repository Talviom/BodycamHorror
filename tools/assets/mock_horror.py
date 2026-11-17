"""Mockups of the horror restyle (round banner + inventory) over real dev-client screenshots.

Usage: python -I tools/assets/mock_horror.py <repo root> <screenshot dir>
Writes docs/previews/horror_mock_banner.png and horror_mock_inventory.png.
Direction: found-footage / analog horror. Dark, dirty, quiet. No neon colours and no
red/cyan colour fringes. Off-white "bone" text that is worn and slightly ghosted, with dried-blood
red only for REC and threats, heavy vignette, grain and dust.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).parent))
import make_gui as mg  # noqa: E402

BONE = (196, 188, 170)
DIM = (120, 114, 102)
DARK = (78, 74, 66)
BLOOD = (122, 22, 18)
rng = np.random.default_rng(13)


def worn_text(text, color, scale, seed=0, wear=0.16):
    """Pixel text with eroded strokes (as if printed long ago) and a faint ghost below."""
    base = mg.text_pixels(text, color, 1)
    r = np.random.default_rng(seed)
    mask = base[..., 3] > 0
    holes = (r.random(mask.shape) < wear) & mask
    base[holes, 3] = (base[holes, 3] * 0.25).astype(np.uint8)
    # Uneven ink: some strokes darker.
    shade = 0.82 + 0.18 * r.random(mask.shape)
    base[..., :3] = (base[..., :3] * shade[..., None]).astype(np.uint8)
    img = Image.fromarray(base, "RGBA").resize((base.shape[1] * scale, base.shape[0] * scale), Image.NEAREST)
    # Ghost: a dim, blurred copy 1 px lower (smeared analog picture, not cyber chroma).
    ghost = img.copy().filter(ImageFilter.GaussianBlur(scale * 0.6))
    ga = np.asarray(ghost).copy()
    ga[..., 3] = (ga[..., 3] * 0.35).astype(np.uint8)
    out = Image.new("RGBA", (img.width, img.height + scale * 2), (0, 0, 0, 0))
    out.alpha_composite(Image.fromarray(ga), (0, scale))
    out.alpha_composite(img, (0, 0))
    return out


def grade(frame, darken=0.35, desat=0.7):
    """Darken and desaturate the game frame toward a cold, dirty grade."""
    a = np.asarray(frame.convert("RGB")).astype(float) / 255
    grey = a.mean(axis=2, keepdims=True)
    a = a * (1 - desat) + grey * desat
    a = a * darken
    a[..., 0] *= 1.02
    a[..., 2] *= 0.94
    return a


def vignette(h, w, strength=0.85, power=2.2):
    yy, xx = np.mgrid[:h, :w]
    d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2) / np.sqrt(2)
    return 1 - strength * np.clip(d, 0, 1) ** power


def dust_and_grain(a, amount=0.06, specks=260, hairs=4):
    h, w = a.shape[:2]
    a = a + rng.normal(0, amount, (h, w, 1))
    for _ in range(specks):
        y, x = rng.integers(0, h), rng.integers(0, w)
        a[y, x] = a[y, x] * 0.3 if rng.random() < 0.7 else a[y, x] + 0.25
    for _ in range(hairs):  # thin film-hair scratches
        x = rng.integers(0, w)
        y0 = rng.integers(0, h // 2)
        for y in range(y0, min(h, y0 + rng.integers(40, 160))):
            x = int(np.clip(x + rng.integers(-1, 2), 0, w - 1))
            a[y, x] = a[y, x] * 0.35
    return np.clip(a, 0, 1)


def to_img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")


def paste_center(canvas, im, cx, y):
    canvas.alpha_composite(im, (int(cx - im.width / 2), int(y)))


def banner(bg_path, out):
    W, H = 854, 480
    bg = Image.open(bg_path).resize((W, H))
    a = grade(bg, darken=0.10, desat=0.85)  # almost black: the freeze before the round
    a *= vignette(H, W, 0.9, 1.8)[..., None]
    a = dust_and_grain(a, 0.035, 320, 6)
    # A slow, soft tracking band (not a glitch): a faint lighter smear.
    a[300:309] = np.clip(a[300:309] * 1.25 + 0.015, 0, 1)
    c = to_img(a)
    paste_center(c, worn_text("ROUND 1", BONE, 6, 1, 0.18), W / 2, 150)
    paste_center(c, worn_text("ALPHA 0  -  0 BRAVO", DIM, 2, 2, 0.12), W / 2, 222)
    paste_center(c, worn_text("STAY TOGETHER.", BLOOD, 2, 3, 0.22), W / 2, 262)
    paste_center(c, worn_text("9", DIM, 3, 4, 0.1), W / 2, 330)
    # Camcorder OSD, dim and small.
    c.alpha_composite(worn_text("CAM 01", DARK, 2, 5, 0.1), (24, 22))
    c.alpha_composite(worn_text("● REC", BLOOD, 2, 6, 0.1), (W - 112, 22))
    c.alpha_composite(worn_text("NOV 17 1998", DARK, 2, 7, 0.1), (24, H - 46))
    c.alpha_composite(worn_text("03:12:09 AM", DARK, 2, 8, 0.1), (W - 158, H - 46))
    c.convert("RGB").save(out)


def inventory(bg_path, out):
    W, H = 854, 480
    bg = Image.open(bg_path).resize((W, H))
    a = grade(bg, darken=0.30, desat=0.75)
    a *= vignette(H, W, 0.75, 2.0)[..., None]
    # The panel: a dirty, water-stained dark sheet with an uneven worn edge, no bright frame.
    pw, ph = 504, 300
    x0, y0 = (W - pw) // 2, (H - ph) // 2
    yy, xx = np.mgrid[:ph, :pw]
    edge = np.minimum.reduce([xx, yy, pw - 1 - xx, ph - 1 - yy]).astype(float)
    jag = mg.value_noise if hasattr(mg, "value_noise") else None
    wob = rng.random((ph, pw)) * 3
    inside = edge + wob > 3
    stain = np.asarray(Image.fromarray((rng.random((12, 20)) * 255).astype(np.uint8)).resize((pw, ph), Image.BICUBIC)) / 255
    panel = np.zeros((ph, pw, 3))
    panel[:] = (0.095, 0.086, 0.072)
    panel *= (0.8 + 0.4 * stain)[..., None]
    panel += rng.normal(0, 0.018, (ph, pw, 1))
    # Darker rim, like old cardboard soaked at the edges.
    rim = np.clip(1 - edge / 22, 0, 1)
    panel *= (1 - 0.55 * rim)[..., None]
    region = a[y0:y0 + ph, x0:x0 + pw]
    region[inside] = region[inside] * 0.15 + panel[inside] * 0.85
    a = dust_and_grain(a, 0.03, 380, 3)
    c = to_img(a)
    c.alpha_composite(worn_text("PERSONAL EFFECTS", BONE, 2, 11, 0.16), (x0 + 18, y0 + 14))
    c.alpha_composite(worn_text("ITEMS ON THE BODY", DARK, 2, 12, 0.1), (x0 + 18, y0 + 38))
    c.alpha_composite(worn_text("● REC", BLOOD, 1, 13, 0.08), (x0 + pw - 60, y0 + 16))
    # Player "photo": a faded ID photo with a height chart behind, slightly tilted feel.
    px0, py0, pw2, ph2 = x0 + 18, y0 + 66, 100, 196
    photo = Image.open(Path(bg_path).parent / "36_inv.png").crop((196, 128, 292, 326)).resize((pw2, ph2))
    pa = grade(photo, 0.62, 0.9) * 0.95 + np.array([0.07, 0.06, 0.04])
    for k in range(0, ph2, 12):  # height chart lines
        pa[k, :] = pa[k, :] * 0.6 + 0.08
    pa = dust_and_grain(pa, 0.03, 60, 1)
    ph_img = to_img(pa)
    c.alpha_composite(ph_img, (px0, py0))
    c.alpha_composite(worn_text("SUBJECT", DARK, 2, 14, 0.1), (px0, py0 + ph2 + 6))
    # Slots: recessed dark squares with a dim worn bone outline.
    sx0, sy0, pitch = x0 + 140, y0 + 80, 36
    for r in range(2):
        for k in range(10):
            sx, sy = sx0 + k * pitch, sy0 + r * pitch
            box = np.zeros((32, 32, 4), np.uint8)
            box[..., :3] = (10, 9, 8)
            box[..., 3] = 235
            outline = np.zeros((32, 32), bool)
            outline[0, :] = outline[-1, :] = outline[:, 0] = outline[:, -1] = True
            worn = rng.random((32, 32)) < 0.35
            box[outline & ~worn, :3] = (70, 66, 58)
            box[1, 1:-1, :3] = (4, 4, 3)  # inner shadow: recessed
            box[1:-1, 1, :3] = (4, 4, 3)
            c.alpha_composite(Image.fromarray(box), (sx, sy))
            if r == 0 and k < 9:
                c.alpha_composite(worn_text(str(k + 1), DARK, 2, 20 + k, 0.05), (sx + 2, sy - 20))
    # Item placeholder in slot 1 (the flashlight icon from the mod).
    try:
        icon = Image.open(sys.argv[1] + "/src/main/resources/assets/bodycamhorror/textures/item/flashlight.png").convert("RGBA").resize((32, 32), Image.NEAREST)
        ia = np.asarray(icon).copy()
        ia[..., :3] = (ia[..., :3] * 0.75).astype(np.uint8)
        c.alpha_composite(Image.fromarray(ia), (sx0, sy0))
    except OSError:
        pass
    c.alpha_composite(worn_text("SHIFT+CLICK MOVE   Q DROP", DARK, 2, 30, 0.08), (sx0, sy0 + 2 * pitch + 12))
    c.alpha_composite(worn_text("THEY WILL FIND WHAT YOU LEAVE.", (110, 28, 24), 2, 31, 0.2), (sx0, y0 + ph - 34))
    c.convert("RGB").save(out)


def main():
    root = Path(sys.argv[1])
    shots = Path(sys.argv[2])
    prev = root / "docs/previews"
    banner(shots / "18_state1.png", prev / "horror_mock_banner.png")
    inventory(shots / "35_beam.png", prev / "horror_mock_inventory.png")
    print("mockups written")


if __name__ == "__main__":
    main()
