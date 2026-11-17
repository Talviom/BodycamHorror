package com.talviom.bodycamhorror.bots;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Клиентская часть сценария проверки: скриншот, поворот камеры, скрытие интерфейса. Только для TestScript. */
final class ClientShots {
    private ClientShots() {}

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

    /** Открыть чат (как T). */
    static void openChat() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
    }

    /** Закрыть открытый экран. */
    static void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(null));
    }
}
