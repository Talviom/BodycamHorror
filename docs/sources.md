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
| Фон ванильных экранов вместо земли | `assets/minecraft/textures/gui/options_background.png`, `light_dirt_background.png` | Плёночное зерно, `tools/assets/make_menu.py` |
| Кнопки, ползунки, флажки, панель предметов ванильных экранов | `assets/minecraft/textures/gui/widgets.png`, `slider.png`, `checkbox.png` | Нарисовано скриптом `tools/assets/make_widgets.py` с нуля |
