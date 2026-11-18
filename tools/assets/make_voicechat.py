"""Dark panels for Simple Voice Chat screens (own drawing, project palette).

Only the layout follows the voice chat textures (panel sizes and where the dark list/text bar sits),
measured once; nothing is copied or recoloured from them.

Usage: python -I tools/assets/make_voicechat.py <repo root>
Writes src/main/resources/overrides/assets/voicechat/textures/gui/<name>.png (256x256 canvas, panel at 0,0).
Not drawn (left as they are): gui_volumes.png (has a small icon), voice_activation_slider.png (level gradient).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# name: (width, height, inner dark bar (x0, y0, x1, y1) or None)
PANELS = {
    "gui_voicechat": (195, 76, None),
    "gui_enter_password": (195, 74, None),
    "gui_create_group": (195, 124, None),
    "gui_voicechat_settings": (248, 198, None),
    "gui_audio_devices": (236, 66, (7, 15, 228, 34)),
    "gui_group": (236, 66, (7, 15, 228, 34)),
    "gui_join_group": (236, 66, (7, 15, 228, 34)),
}

FILL = np.array([26, 25, 23])
LIGHT = (70, 66, 59)     # worn top/left edge, soot
SHADE = (12, 11, 10)     # bottom/right edge
OUTLINE = (4, 4, 4)
BAR = (11, 11, 10)
BAR_EDGE = (52, 49, 44)


def panel(w, h, bar, seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((256, 256, 4), np.uint8)
    grain = (rng.random((h, w, 1)) - 0.5) * 0.18
    img[:h, :w, :3] = np.clip(FILL * (1 + grain), 0, 255)
    img[:h, :w, 3] = 255
    # Bevel like a vanilla window, but dark and worn: 1px outline, 2px light top/left, 2px shade bottom/right.
    img[0, 1:w - 1, :3] = OUTLINE
    img[h - 1, 1:w - 1, :3] = OUTLINE
    img[1:h - 1, 0, :3] = OUTLINE
    img[1:h - 1, w - 1, :3] = OUTLINE
    img[0, 0, 3] = img[0, w - 1, 3] = img[h - 1, 0, 3] = img[h - 1, w - 1, 3] = 0
    img[1:3, 1:w - 2, :3] = LIGHT
    img[1:h - 2, 1:3, :3] = LIGHT
    img[h - 3:h - 1, 2:w - 1, :3] = SHADE
    img[2:h - 1, w - 3:w - 1, :3] = SHADE
    # Scuffs on the light edge.
    for _ in range(max(4, w // 25)):
        x = int(rng.integers(3, w - 3))
        img[int(rng.integers(1, 3)), x, :3] = FILL
    if bar:
        x0, y0, x1, y1 = bar
        img[y0:y1, x0:x1, :3] = BAR
        img[y0, x0:x1, :3] = SHADE
        img[y1 - 1, x0:x1, :3] = BAR_EDGE
        img[y0:y1, x1 - 1, :3] = BAR_EDGE
    return img


def main():
    root = Path(sys.argv[1]).resolve()
    out = root / "src/main/resources/overrides/assets/voicechat/textures/gui"
    out.mkdir(parents=True, exist_ok=True)
    for i, (name, (w, h, bar)) in enumerate(PANELS.items()):
        Image.fromarray(panel(w, h, bar, 900 + i), "RGBA").save(out / f"{name}.png", optimize=True)
        print("made", name, w, h)


if __name__ == "__main__":
    main()
