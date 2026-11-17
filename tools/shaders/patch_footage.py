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
    sp.write_text(patch_shaders_properties(sp.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
    for name in ("block.properties", "entity.properties", "item.properties"):
        f = dst / "shaders" / name
        if f.exists():
            f.write_text(patch_block_properties(f.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
    # Настройки пользователя для оригинального пака переносятся на исправленный.
    opts = src.with_name(src.name + ".txt")
    if opts.exists():
        shutil.copyfile(opts, dst.with_name(dst.name + ".txt"))
    print("patched pack:", dst)


if __name__ == "__main__":
    main()
