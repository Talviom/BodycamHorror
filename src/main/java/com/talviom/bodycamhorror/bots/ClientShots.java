package com.talviom.bodycamhorror.bots;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Клиентская часть сценария проверки: скриншот, поворот камеры, скрытие интерфейса. Только для TestScript. */
final class ClientShots {
    /** Настройка «пауза при потере фокуса» до начала сценария; null, если сценарий её не трогал. */
    private static Boolean pauseOnLostFocusBefore;

    private ClientShots() {}

    /**
     * Во время сценария игра не встаёт на паузу, когда окно теряет фокус. Иначе стоит кликнуть в браузер,
     * и встроенный сервер замирает вместе с матчем ботов. Меняем только в памяти, в конце сценария
     * возвращаем как было, чтобы в options.txt пользователя осталась его настройка.
     */
    static void keepRunningUnfocused(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (on) {
                if (pauseOnLostFocusBefore == null) pauseOnLostFocusBefore = mc.options.pauseOnLostFocus;
                mc.options.pauseOnLostFocus = false;
                BodycamHorror.LOGGER.info("[bh-test] no pause on lost focus while the script runs");
            } else if (pauseOnLostFocusBefore != null) {
                mc.options.pauseOnLostFocus = pauseOnLostFocusBefore;
                pauseOnLostFocusBefore = null;
            }
        });
    }

    /** Скриншот в run/screenshots/<имя>.png (с шейдерами, как по F2). */
    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                msg -> BodycamHorror.LOGGER.info("[bh-test] shot " + name + ": " + msg.getString())));
    }

    /** Спрятать или показать интерфейс (как F1). */
    static void hud(boolean visible) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.options.hideGui = !visible);
    }

    /** Зажать или отпустить ПКМ (только окно сценария): игра сама начинает использовать предмет в руке. */
    static void holdUse(boolean hold) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.options.keyUse.setDown(hold));
    }

    /** Вид от первого лица или со спины (как F5). */
    static void view(String mode) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.CameraType type = switch (mode) {
            case "third" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
            case "back" -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
            default -> net.minecraft.client.CameraType.FIRST_PERSON;
        };
        mc.execute(() -> mc.options.setCameraType(type));
    }

    /** Открыть чат (как T). */
    static void openChat() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
    }

    /** Открыть главное меню мода поверх мира (для скриншота и проверки, что оно рисуется). */
    static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new com.talviom.bodycamhorror.client.menu.BhTitleScreen()));
    }

    /** Открыть меню паузы (как Esc): мод подменяет его своим. */
    static void openBriefing(int page) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new com.talviom.bodycamhorror.client.BriefingScreen(page)));
    }

    static void openPause() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
    }

    /** Ванильный экран смерти с причиной, как его открывает игра (наш MenuEvents заменит его на SIGNAL LOST). */
    static void openDeath() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new net.minecraft.client.gui.screens.DeathScreen(
                net.minecraft.network.chat.Component.translatable("death.attack.bodycamhorror.tenant", "Dev"), false)));
    }

    /** Сменить язык игры, как на экране «Язык»: ресурсы перезагружаются, lang-файлы читаются заново. */
    static void lang(String code) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            mc.getLanguageManager().setSelected(code);
            mc.options.languageCode = code;
            mc.reloadResourcePacks();
            BodycamHorror.LOGGER.info("[bh-test] language " + code);
        });
    }

    /** Закрыть открытый экран. */
    static void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(null));
    }

    /** Открыть ванильный экран поверх текущего: настройки, видео, ресурспаки, список миров, моды. */
    static void openVanilla(String what) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            net.minecraft.client.gui.screens.Screen parent = mc.screen;
            net.minecraft.client.gui.screens.Screen next = switch (what) {
                case "options" -> new net.minecraft.client.gui.screens.OptionsScreen(parent, mc.options);
                case "video" -> new net.minecraft.client.gui.screens.VideoSettingsScreen(parent, mc.options);
                case "packs" -> new net.minecraft.client.gui.screens.packs.PackSelectionScreen(mc.getResourcePackRepository(),
                        r -> {}, mc.getResourcePackDirectory(), net.minecraft.network.chat.Component.literal("Resource Packs"));
                case "worlds" -> new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(parent);
                case "lan" -> new net.minecraft.client.gui.screens.ShareToLanScreen(parent);
                case "controls" -> new net.minecraft.client.gui.screens.controls.ControlsScreen(parent, mc.options);
                case "mods" -> new net.minecraftforge.client.gui.ModListScreen(parent);
                // Экран чужого мода по имени класса (Embeddium, голосовой чат): конструктор (Screen) или без аргументов.
                default -> byClassName(what, parent);
            };
            if (next == null) return;
            mc.setScreen(next);
        });
    }

    private static net.minecraft.client.gui.screens.Screen byClassName(String name, net.minecraft.client.gui.screens.Screen parent) {
        for (String cls : name.split("[|]")) {
            try {
                Class<?> c = Class.forName(cls.trim());
                try {
                    return (net.minecraft.client.gui.screens.Screen) c.getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(parent);
                } catch (NoSuchMethodException ignored) {
                    return (net.minecraft.client.gui.screens.Screen) c.getConstructor().newInstance();
                }
            } catch (ReflectiveOperationException | ClassCastException | LinkageError ex) {
                BodycamHorror.LOGGER.info("[bh-test] screen " + cls + ": " + ex);
            }
        }
        BodycamHorror.LOGGER.error("[bh-test] screen " + name + ": no such screen");
        return null;
    }

    /** Нажать кнопку на открытом экране по надписи (как щелчок мышью). */
    static void press(String label) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen == null) {
                BodycamHorror.LOGGER.error("[bh-test] press " + label + ": no screen open");
                return;
            }
            for (net.minecraft.client.gui.components.events.GuiEventListener c : mc.screen.children()) {
                if (c instanceof net.minecraft.client.gui.components.AbstractButton b
                        && b.getMessage().getString().trim().equalsIgnoreCase(label)) {
                    b.onPress();
                    BodycamHorror.LOGGER.info("[bh-test] pressed " + label + " -> " + (mc.screen == null ? "game" : mc.screen.getClass().getSimpleName()));
                    return;
                }
            }
            BodycamHorror.LOGGER.error("[bh-test] press " + label + ": no such button on " + mc.screen.getClass().getSimpleName());
        });
    }

    /** Выбрать быстрый слот 1..9, как цифрой на клавиатуре. */
    static void slot(int n) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && n >= 1 && n <= 9) mc.player.getInventory().selected = n - 1;
        });
    }
}
