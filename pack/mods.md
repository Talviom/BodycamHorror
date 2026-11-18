# Моды сборки (Forge 1.20.1-47.4.10)

Состояние на 2026-11-15. Источник: папка `D:\Minecraft\game\mods`.

## Отключены (лежат в `mods_disabled`, 2026-11-15)

| Мод | Почему |
|---|---|
| entity_model_features 3.3.9 | Нужен только ресурспакам с моделями мобов, а они не включены |
| entity_texture_features 7.2.4 | То же |
| entity_sound_features 0.8.2 | То же |
| epicdeathscreen 1.1.0-beta.2 | Экран смерти заменит наш режим наблюдения |
| simpleenemymod 0.1.6 | NPC-враги для TACZ, в PvP не нужны (можно вернуть для тестов с ботами) |

## Добавлены

| Мод | Зачем |
|---|---|
| modernfix-forge-5.27.93+mc1.20.1 (Modrinth, sha512 проверен) | Меньше памяти, быстрее загрузка |

## Оружие и снаряжение

tacz 1.1.8-hotfix, tacz-tweaks 2.14.2, tacz_tactical_breaching 1.0.5, tacz_x_guns_lights_addon 2.9.0,
tacz-mesh-loader 0.1.8, taczsoundoverhaul 0.2, daffas_arsenal 3.7.1.1, WaT 0.4.0 (ганпак Ready or Not),
ZeroContact build 72 (бронежилеты, шлемы, пластины, повязки команд, жетоны), curios 5.14.1 (нужен ZeroContact).

Ганпаки в `tacz/`: tacz_default_gun, daffas, Gamma-Beta1.0.2 (не грузятся minigun, scar_l, rpg7), duyupack+1.21.1 (под 1.21.1, проверить).

## Камера и анимации

firstperson 2.7.2, naturalcamera 0.0.4-a, leaningornot 0.3.5-BETA, viewmodel_tuner 1.0.5,
notenoughanimations 1.12.6, skinlayers3d 1.11.3, disablef5 1.2, hudtoggle 1.0.0 (последние два — временно, до нашего HUD).

## Звук и голос

voicechat 2.6.24, sound-physics-remastered 1.5.1, PresenceFootsteps 1.9.1-beta.1, AmbientSounds 6.3.8, Sounds 2.2.1.

## Эффекты

ragdollified 1.1.1, bleedingmod 1.6, betterbloodoverlay 2.2.0,
ItemPhysicLite 1.6.11, itemphysicguns 1.0.3.

## Строительство карты

mcw-doors, mcw-furniture, mcw-fences, mcw-windows, mcw-roofs, yuushya 2.3.0, construction deco, MOAdecor SCIENCE, ModernxlSurvival 1.3.2.

## Оптимизация

embeddium 0.3.31, oculus 1.8.0, ferritecore 6.0.1, ImmediatelyFast 1.5.5, entityculling 1.11.1, dynamic-fps 3.11.4, memoryleakfix 1.1.5.

## Библиотеки

architectury 9.2.14, cloth-config 11.1.136, yet_another_config_lib_v3 3.6.6, geckolib 4.8.4, kotlinforforge 4.12.0,
CreativeCore 2.12.39, vinconfig 0.0.4.5, MRU 1.0.4.

## Шейдеры и ресурспаки

Шейдеры: Body Camera Shader 1.6.1, FOOTAGE 1.0 (оба остаются).
Ресурспаки (не включены): Limitless Designs 2.2.6, LOW Realistic 3.4.1, No Hotbar, EXP Orb Muted, invisXP.

## Настройки (`pack/config-backup/options.txt`)

Дальность прорисовки 12 → 8, симуляция 12 → 6, VSync выключен. Оригинал: `options.txt.original`.

Убран 2026-11-18 по решению пользователя: Explosion-Overhaul 0.2.3.0 (84 МБ; запрос «Start chunk scanning?» поверх интерфейса). Jar лежит в `mods_disabled`.
