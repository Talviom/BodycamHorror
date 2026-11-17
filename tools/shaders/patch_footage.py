"""Правит шейдер FOOTAGE 1.0 под Oculus 1.8.0 и собирает исправленную копию.

Сам шейдер чужой (All Rights Reserved), поэтому в репозитории лежит только этот скрипт,
а не исправленный пак. Скрипт берёт оригинальный zip и пишет рядом папку FOOTAGE_bh.

    python tools/shaders/patch_footage.py <FOOTAGE_1.0.zip> <папка shaderpacks>

Что исправляется (ошибки из лога Oculus):
1. scale.composite1 = 0.67 0.67: строка записана неверно, Oculus её отбрасывает. Комментируем,
   поведение то же, ошибки нет.
2. block.properties: комментарии с не-латинскими буквами ломают предобработку, и Oculus
   выбрасывает весь список блоков (свечение, отражения, материалы). Оставляем в комментариях только ASCII.
3. Три текстуры грязи объектива (dirt.png, codlens.png, gen.png) в пак не положили.
   Подставляем ту, что есть, чтобы настройки DIRT_STYLE 2..4 не давали ошибок.
4. tsrFrame использует функцию mod, которой нет в Oculus, а tsrRenderScale нигде не задан.
5. Лишние пробелы в конце строк (screen.DLSS.columns = "1 ").

Что меняется по смыслу (стиль проекта, мрачная «найденная плёнка»):
6. Встроенный фонарик пака горел всегда ночью. Теперь светит только наш нагрудный фонарик:
   мод передаёт его яркость в uniform bhFlashlight (клавиша O, батарейка, мигание, помехи).
   Луч идёт от правого плеча, пятно тёплое и неровное, в воздухе виден слабый конус.
7. Страх (uniform bhFear): края экрана сжимаются в такт сердцу, цвет уходит в серую кость,
   камера «плавает» экспозицией, в тенях больше зерна. При страхе 0 картинка как без эффекта.
8. Чёрное остаётся чёрным: тени не поднимаются в серое.
9. Хроматическая аберрация (разъезд красного и голубого) выключена: запрещена правилами стиля.
"""
import re
import shutil
import sys
import zipfile
from pathlib import Path


def patch_shaders_properties(text: str) -> str:
    text = re.sub(r"(?m)^scale\.composite1\s*=\s*0\.67 0\.67\s*$",
                  "# scale.composite1 = 0.67 0.67  (bh: invalid directive, Oculus ignored it anyway)", text)
    for missing in ("dirt.png", "codlens.png", "gen.png"):
        text = text.replace("texture/" + missing, "texture/dirtastralex.png")
    text = re.sub(r"(?m)^(variable\.float\.tsrFrame\s*=\s*)mod\(frameCounter, 16\.0\)\s*$",
                  r"variable.float.tsrRenderScale = 1.0\n\1frameCounter % 16", text)
    return text


FLASHLIGHT_GLSL = r"""
//----// FLASHLIGHT EFFECT (bodycamhorror) //------------------------------------------------------//
// Наш нагрудный фонарик: яркость приходит из мода (bhFlashlight 0..1), луч от правого плеча.
{
    if (bhFlashlight > 0.001) {
        const vec3 lampPos = vec3(0.24, -0.34, 0.05);
        vec3 lampAim = vec3(0.0, 0.0, -1.0);
        vec2 sway = (gbufferPreviousModelView[0].xy - gbufferModelView[0].xy) + (gbufferPreviousModelView[1].xy - gbufferModelView[1].xy);
        lampAim.xy += sway * 1.2;
        lampAim = normalize(lampAim - lampPos * 0.02);

        vec3 toPix = viewPos - lampPos;
        float f_dist = length(toPix);
        vec3 lampDir = toPix / f_dist;
        float f_dot = dot(lampDir, lampAim);
        float f_ang = acos(clamp(f_dot, -1.0, 1.0));

        // Дешёвый отражатель: горячее пятно с тусклым центром, кольца и широкий слабый ореол.
        float hotspot = smoothstep(0.30, 0.16, f_ang);
        float hole = 1.0 - 0.25 * smoothstep(0.05, 0.0, f_ang);
        float rings = 0.88 + 0.12 * sin(f_ang * 95.0);
        float spill = smoothstep(0.60, 0.28, f_ang) * 0.18;
        float mask = hotspot * hole * rings + spill;

        vec3 lampColor = mix(vec3(1.0, 0.70, 0.42), vec3(1.0, 0.88, 0.70), bhFlashlight);
        float atten = 1.0 / (1.0 + f_dist * f_dist * 0.07);
        atten *= smoothstep(26.0, 10.0, f_dist);
        float ndotl = max(0.0, dot(normal, -lampDir));
        sceneData += albedo * lampColor * mask * atten * ndotl * 2.6 * bhFlashlight;

        // Конус в воздухе: слабая пыль, видимая вдоль луча.
        float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        float rayLen = min(length(viewPos), 14.0);
        float haze = 0.0;
        for (int i = 0; i < 8; i++) {
            float t = (float(i) + jitter) / 8.0 * rayLen;
            vec3 hp = viewDir * t - lampPos;
            float hd = length(hp);
            float ha = acos(clamp(dot(hp / hd, lampAim), -1.0, 1.0));
            haze += smoothstep(0.34, 0.12, ha) / (1.0 + hd * hd * 0.09);
        }
        sceneData += lampColor * haze * (rayLen / 8.0) * 0.010 * bhFlashlight;
    }
}
//------------------------------------------------------------------------------------------------//
"""

FEAR_GLSL = r"""
        // BODYCAMHORROR: темнота и страх
        {
            vec2 vc = texcoordRaw - 0.5;
            float r = length(vc * vec2(aspectRatio, 1.0));
            finalData = max(finalData - 0.012, 0.0) * (1.0 / 0.988);
            float beat = pow(0.5 + 0.5 * sin(frameTimeCounter * mix(5.0, 9.0, bhFear)), 6.0);
            float tunnel = smoothstep(mix(1.25, 0.32, bhFear), 1.05, r + beat * 0.05 * bhFear);
            finalData *= 1.0 - tunnel * bhFear * 0.94;
            float gain = 1.0 + (sin(frameTimeCounter * 1.7) * 0.6 + sin(frameTimeCounter * 4.3) * 0.4) * (0.012 + 0.05 * bhFear);
            finalData *= gain;
            float lum = dot(finalData, vec3(0.299, 0.587, 0.114));
            finalData = mix(finalData, lum * vec3(1.03, 0.99, 0.90), bhFear * 0.65);
            float g = fract(sin(dot(gl_FragCoord.xy + floor(frameTimeCounter * 24.0) * 17.0, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
            finalData += g * (0.012 + 0.06 * bhFear) * (1.0 - smoothstep(0.0, 0.35, lum));
            finalData = clamp(finalData, 0.0, 1.0);
        }
"""


def patch_composite1(text: str) -> str:
    start = text.index("//----// FLASHLIGHT EFFECT //")
    end = text.index("#endif", text.index("sceneData += albedo * f_color", start))
    end = text.index("\n", text.index("//----", end)) + 1
    text = text[:start] + FLASHLIGHT_GLSL.lstrip("\n") + text[end:]
    return text.replace("uniform int worldTime;", "uniform int worldTime;\nuniform float bhFlashlight;", 1)


def patch_final(text: str) -> str:
    text = text.replace("out vec3 finalData;", "out vec3 finalData;\nuniform float bhFear;", 1)
    anchor = "        finalData = applyChromaticAberration(texcoord, finalData);\n"
    assert anchor in text
    return text.replace(anchor, anchor + FEAR_GLSL, 1)


OPTION_OVERRIDES = {
    "CHROMATIC_ABERRATION_STRENGTH": "0.0",
    "FLASHLIGHT_TOGGLE": "0",
}


def patch_options(text: str) -> str:
    lines = [l for l in text.splitlines() if l.split("=")[0] not in OPTION_OVERRIDES]
    lines += [k + "=" + v for k, v in OPTION_OVERRIDES.items()]
    return "\n".join(lines) + "\n"


def patch_block_properties(text: str) -> str:
    out = []
    for line in text.splitlines():
        if line.lstrip().startswith("#"):
            line = "#" + re.sub(r"[^\x20-\x7E]", "", line.lstrip()[1:]).rstrip()
            line = re.sub(r"[()]", "", line)
        out.append(line)
    return "\n".join(out) + "\n"


def main() -> None:
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2]) / "FOOTAGE_bh"
    if dst.exists():
        shutil.rmtree(dst)
    with zipfile.ZipFile(src) as z:
        z.extractall(dst)
    sp = dst / "shaders" / "shaders.properties"
    props = patch_shaders_properties(sp.read_text(encoding="utf-8"))
    props = "\n".join(l.rstrip() for l in props.splitlines()) + "\n"
    sp.write_text(props, encoding="utf-8", newline="\n")
    c1 = dst / "shaders" / "world0" / "composite1.fsh"
    c1.write_text(patch_composite1(c1.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
    fin = dst / "shaders" / "program" / "Post" / "Final.glsl"
    fin.write_text(patch_final(fin.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
    for name in ("block.properties", "entity.properties", "item.properties"):
        f = dst / "shaders" / name
        if f.exists():
            f.write_text(patch_block_properties(f.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
    # Настройки пользователя для оригинального пака переносятся на исправленный.
    opts = src.with_name(src.name + ".txt")
    text = opts.read_text(encoding="utf-8") if opts.exists() else ""
    dst.with_name(dst.name + ".txt").write_text(patch_options(text), encoding="utf-8", newline="\n")
    print("patched pack:", dst)


if __name__ == "__main__":
    main()
