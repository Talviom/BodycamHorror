"""Анимации Жильца для GeckoLib: animations/entity/tenant.animation.json.

    python -I tools/assets/make_tenant_anims.py <repo root>

Повороты в градусах добавляются к позе модели. Минус по X у руки и ноги = вперёд.
"""
import json
import sys
from pathlib import Path


def kf(*pairs):
    return {f"{t:.2f}": list(v) for t, v in pairs}


ANIMS = {
    # Стоит, едва покачиваясь; голова иногда дёргается.
    "animation.tenant.idle": {
        "loop": True, "animation_length": 4.0,
        "bones": {
            "body": {"rotation": kf((0, (0, 0, 0)), (2, (3, 0, 1.5)), (4, (0, 0, 0)))},
            "head": {"rotation": kf((0, (0, 0, 0)), (1.3, (0, 0, 0)), (1.4, (4, 8, -6)), (1.9, (4, 8, -6)), (2.0, (0, 0, 0)), (4, (0, 0, 0)))},
            "arm_right": {"rotation": kf((0, (0, 0, 0)), (2, (4, 0, 0)), (4, (0, 0, 0)))},
            "arm_left": {"rotation": kf((0, (0, 0, 0)), (2, (-3, 0, 0)), (4, (0, 0, 0)))},
        },
    },
    # Идёт на след: длинные шаги, руки болтаются.
    "animation.tenant.walk": {
        "loop": True, "animation_length": 1.2,
        "bones": {
            "leg_right": {"rotation": kf((0, (-26, 0, 0)), (0.6, (26, 0, 0)), (1.2, (-26, 0, 0)))},
            "leg_left": {"rotation": kf((0, (26, 0, 0)), (0.6, (-26, 0, 0)), (1.2, (26, 0, 0)))},
            "arm_right": {"rotation": kf((0, (14, 0, 0)), (0.6, (-14, 0, 0)), (1.2, (14, 0, 0)))},
            "arm_left": {"rotation": kf((0, (-14, 0, 0)), (0.6, (14, 0, 0)), (1.2, (-14, 0, 0)))},
            "hips": {"position": kf((0, (0, 0, 0)), (0.3, (0, 0.6, 0)), (0.6, (0, 0, 0)), (0.9, (0, 0.6, 0)), (1.2, (0, 0, 0)))},
            "head": {"rotation": kf((0, (0, -4, 0)), (0.6, (0, 4, 0)), (1.2, (0, -4, 0)))},
        },
    },
    # Крадётся: медленно, руки вытянуты вперёд, пальцы шевелятся.
    "animation.tenant.stalk": {
        "loop": True, "animation_length": 2.4,
        "bones": {
            "leg_right": {"rotation": kf((0, (-18, 0, 0)), (1.2, (18, 0, 0)), (2.4, (-18, 0, 0)))},
            "leg_left": {"rotation": kf((0, (18, 0, 0)), (1.2, (-18, 0, 0)), (2.4, (18, 0, 0)))},
            "body": {"rotation": kf((0, (8, 0, 0)), (1.2, (12, 0, 0)), (2.4, (8, 0, 0)))},
            "arm_right": {"rotation": kf((0, (-38, 0, 0)), (1.2, (-46, 0, 4)), (2.4, (-38, 0, 0)))},
            "arm_left": {"rotation": kf((0, (-44, 0, 0)), (1.2, (-36, 0, -4)), (2.4, (-44, 0, 0)))},
            "finger_right_a": {"rotation": kf((0, (0, 0, 0)), (0.6, (-25, 0, 0)), (1.2, (0, 0, 0)), (2.4, (0, 0, 0)))},
            "finger_left_c": {"rotation": kf((0, (0, 0, 0)), (1.2, (0, 0, 0)), (1.8, (-25, 0, 0)), (2.4, (0, 0, 0)))},
        },
    },
    # Скример: голова рывком вперёд к камере, руки вскидываются.
    "animation.tenant.scare": {
        "loop": "hold_on_last_frame", "animation_length": 0.5,
        "bones": {
            "body": {"rotation": kf((0, (0, 0, 0)), (0.12, (12, 0, 0)), (0.5, (10, 0, 0)))},
            "neck": {"rotation": kf((0, (0, 0, 0)), (0.12, (14, 0, 0)), (0.5, (12, 0, 0)))},
            # Голова запрокидывается назад, чтобы пустое лицо смотрело прямо в камеру.
            "head": {"rotation": kf((0, (0, 0, 0)), (0.1, (-30, 0, 20)), (0.25, (-22, 0, 14)), (0.5, (-26, 0, 18)))},
            # Руки вскинуты и разведены в стороны: обрамляют лицо, а не закрывают его.
            "arm_right": {"rotation": kf((0, (0, 0, 0)), (0.12, (-120, 0, -40)), (0.5, (-112, 0, -36)))},
            "arm_left": {"rotation": kf((0, (0, 0, 0)), (0.12, (-120, 0, 40)), (0.5, (-112, 0, 36)))},
        },
    },
    # Хват: руки вперёд на плечи жертвы, голова наклоняется к лицу.
    "animation.tenant.grab": {
        "loop": True, "animation_length": 1.0,
        "bones": {
            "body": {"rotation": kf((0, (14, 0, 0)), (0.5, (16, 0, 0)), (1.0, (14, 0, 0)))},
            "arm_right": {"rotation": kf((0, (-62, 0, 12)), (0.5, (-66, 0, 12)), (1.0, (-62, 0, 12)))},
            "arm_left": {"rotation": kf((0, (-62, 0, -12)), (0.5, (-58, 0, -12)), (1.0, (-62, 0, -12)))},
            "forearm_right": {"rotation": kf((0, (-10, 0, 0)), (1.0, (-10, 0, 0)))},
            "forearm_left": {"rotation": kf((0, (-10, 0, 0)), (1.0, (-10, 0, 0)))},
            "head": {"rotation": kf((0, (6, 0, 10)), (0.5, (10, 0, 16)), (1.0, (6, 0, 10)))},
        },
    },
    # Оглушён лучом: закрывается руками, отшатывается.
    "animation.tenant.stunned": {
        "loop": True, "animation_length": 0.8,
        "bones": {
            "body": {"rotation": kf((0, (-20, 0, 0)), (0.4, (-24, 0, 3)), (0.8, (-20, 0, 0)))},
            "arm_right": {"rotation": kf((0, (-130, 0, 20)), (0.4, (-125, 0, 24)), (0.8, (-130, 0, 20)))},
            "arm_left": {"rotation": kf((0, (-128, 0, -22)), (0.4, (-134, 0, -18)), (0.8, (-128, 0, -22)))},
            "forearm_right": {"rotation": kf((0, (-60, 0, 0)), (0.8, (-60, 0, 0)))},
            "forearm_left": {"rotation": kf((0, (-60, 0, 0)), (0.8, (-60, 0, 0)))},
            "head": {"rotation": kf((0, (-10, 15, 0)), (0.4, (-14, -10, 0)), (0.8, (-10, 15, 0)))},
        },
    },
}


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    out = root / "src/main/resources/assets/bodycamhorror/animations/entity/tenant.animation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"format_version": "1.8.0", "animations": ANIMS}, indent=2), encoding="utf-8")
    print("ok:", out)


if __name__ == "__main__":
    main()
