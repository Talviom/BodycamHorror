# Откуда взяты чужие материалы

Правило проекта: берём только своё (сделанное нами или пользователем) или CC0.
Ничего, что требует указывать автора (CC-BY, «бесплатно с упоминанием» и т. п.), не используем.
Авторов нигде указывать не нужно: ни в игре, ни в описании видео.
Этот список только для нас, чтобы при надобности доказать, что лицензия CC0.

## Музыка

Все треки обработаны под звук старой плёнки (плавание тона, шипение, срез верхов).

| Где играет | Файлы | Трек | Лицензия | Источник |
|---|---|---|---|---|
| Меню | `menu.ogg` | Cold Silence (Eponasoft) | CC0 | https://opengameart.org/content/cold-silence |
| Лобби, временно | `lobby.ogg` | Abandoned passages (congusbongus) | CC0 | https://opengameart.org/content/abandoned-passages-horror-ambience-loop |
| Лобби, магнитофон | `lobby_tape.ogg` | Sadness (Kistol), выбран пользователем 2026-11-28; мягко «через плёнку» (ffmpeg: моно, полоса 90–6500 Гц, лёгкое плавание, тихий шип) | CC0 | https://opengameart.org/content/sadness |
| Начало раунда | `round_start_1..4.ogg` | Searching (yd), четыре отрывка | CC0 | https://opengameart.org/content/searching |
| Конец матча, победа | `match_won.ogg` | Dark Intro (Nikke) | CC0 | https://opengameart.org/content/dark-intro |
| Конец матча, поражение | `match_lost.ogg` | Menacing Otherworld (Ruskerdax) | CC0 | https://opengameart.org/content/menacing-otherworld |
| Итоги раунда | `round_won.ogg`, `round_lost.ogg` | свои, сделаны скриптом | своё | `tools/assets/make_music.py` |

Лобби пользователь сделает сам, тогда `lobby.ogg` заменим его треком.

Не используем (нужно указывать автора): Dark Ambience Loop (CC-BY 3.0), DHSFX Horror Stingers, CREEPYSCAPES II (CC-BY 4.0).

## Своё (сделано нами)

| Что | Файлы | Как сделано |
|---|---|---|
| Фон главного меню (коридор, фигура в двери) | `textures/gui/menu/corridor_*.png` | Отрисовано скриптом `tools/assets/make_menu.py` с нуля, без чужих картинок |
| Фон ванильных экранов вместо земли | `overrides/assets/minecraft/textures/gui/options_background.png`, `light_dirt_background.png` | Плёночное зерно, `tools/assets/make_menu.py` |
| Кнопки, ползунки, флажки, панель предметов ванильных экранов | `overrides/assets/minecraft/textures/gui/widgets.png`, `slider.png`, `checkbox.png` | Нарисовано скриптом `tools/assets/make_widgets.py` с нуля |
| Значок окна игры (объектив камеры, точка REC) | `icons/icon_16.png` … `icon_256.png` | Нарисовано скриптом `tools/assets/make_icon.py` с нуля |
| Щелчок кнопок (вместо ванильного) | `sounds/ui_click.ogg`, подключён в `overrides/assets/minecraft/sounds.json` | Синтез в `tools/assets/make_sounds.py` (`ui_click`) |

## Звуки Жильца

Собраны из записей CC0 скриптом `tools/assets/make_tenant_sfx.py` (слои, замедление, эхо, плёночное плавание). Лицензия каждой проверена на её странице 2026-11-18. Исходники лежат на ПК в `D:\Minecraft\dev\sfx_src\tenant`, в репозиторий не кладутся.

| Что взято | Лицензия | Источник |
|---|---|---|
| Horror Hit Soundpack 1 (удары: прыжки, нарастание, скример, затихание) | CC0 | https://opengameart.org/content/horror-hit-soundpack-1 |
| Ghost Monster Voice Moaning & Growling | CC0 | https://opengameart.org/content/ghost-monster-voice-moaning-growling |
| Deep Bone Crack/Break SFX (хруст суставов, удушье) | CC0 | https://opengameart.org/content/deep-bone-crackbreak-sfx |
| Ghost breath (дыхание) | CC0 | https://opengameart.org/content/ghost-breath |
| growl | CC0 | https://opengameart.org/content/growl |
| 80 CC0 creature SFX (рычание, крик, кашель, дыхание) | CC0 | https://opengameart.org/content/80-cc0-creature-sfx |
| Breathing tired (хрип жертвы) | CC0 | https://opengameart.org/content/breathing-tired |
| Heartbeat sounds (глухое сердце) | CC0 | https://opengameart.org/content/heartbeat-sounds |
| Horror breathing | CC0 | https://opengameart.org/content/horror-breathing |
| horror sfx | CC0 | https://opengameart.org/content/horror-sfx |
| Monster or beast sounds (скачан, пока не используется) | CC0 | https://opengameart.org/content/monster-or-beast-sounds |
