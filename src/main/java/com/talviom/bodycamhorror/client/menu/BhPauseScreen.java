package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Клиент: меню паузы (Esc) вместо ванильного. Мир за ним темнеет, как будто камера ушла в режим ожидания:
 * зерно, тёмные края, служебные надписи камеры, слово STANDBY печатной машинкой.
 * Пункты: RESUME, OPTIONS, OPEN TO LAN (только у хоста, пока мир не открыт), SAVE AND QUIT или DISCONNECT.
 * В одиночной игре мир на паузе, как в ванильном меню; в сетевой игре идёт дальше, о чём и говорит подпись.
 */
public final class BhPauseScreen extends Screen {
    private final long openedAt = System.currentTimeMillis();
    private float textAlpha = 1f;

    public BhPauseScreen() {
        super(Component.translatable("menu.game"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int step = 15;
        boolean local = minecraft.isLocalServer();
        IntegratedServer host = minecraft.getSingleplayerServer();
        boolean canShare = host != null && !host.isPublished();
        int rows = canShare ? 4 : 3;
        int y = Math.max(height / 2 - 4, height - 40 - (rows * step + 6));

        addRenderableWidget(new TapeButton(cx, y, "RESUME", this::textAlpha, b -> onClose()));
        addRenderableWidget(new TapeButton(cx, y + step, "OPTIONS", this::textAlpha,
                b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        int next = y + step * 2;
        if (canShare) {
            addRenderableWidget(new TapeButton(cx, next, "OPEN TO LAN", this::textAlpha,
                    b -> minecraft.setScreen(new ShareToLanScreen(this))));
            next += step;
        }
        TapeButton quit = new TapeButton(cx, next + 6, local ? "SAVE AND QUIT" : "DISCONNECT", this::textAlpha, b -> {
            b.active = false;
            leave();
        });
        addRenderableWidget(quit);
    }

    /** Как в ванильном меню паузы: выйти из мира и вернуться в главное меню или к списку серверов. */
    private void leave() {
        boolean local = minecraft.isLocalServer();
        if (minecraft.level != null) minecraft.level.disconnect();
        if (local) minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        else minecraft.clearLevel();
        TitleScreen title = new TitleScreen();
        minecraft.setScreen(local ? title : new JoinMultiplayerScreen(title));
    }

    private float textAlpha() {
        return textAlpha;
    }

    /** В сценарии проверки мир под меню не останавливается, иначе сценарий встанет. */
    @Override
    public boolean isPauseScreen() {
        return System.getProperty("bh.testScript") == null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long t = System.currentTimeMillis() - openedAt;
        float in = Mth.clamp(t / 250f, 0f, 1f);
        textAlpha = in;

        // Мир за меню почти уходит в темноту.
        g.fill(0, 0, width, height, ((int) (0xC8 * in)) << 24);
        Vhs.noise(g, 0, 0, width, height, 0.13f * in);
        Vhs.vignette(g, width, height, 0.95f * in);
        Vhs.flicker(g, width, height);

        int cx = width / 2;
        int titleY = Math.max(24, height / 2 - 62);
        Vhs.ghostCentered(g, Vhs.title("STANDBY"), cx, titleY, MenuOsd.a(230, in) | Vhs.BONE, 2.5f, 0.3f);
        boolean paused = isPauseScreen() && minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished();
        String sub = paused ? "THE TAPE IS PAUSED." : "THE TAPE IS STILL RUNNING.";
        Vhs.ghostCentered(g, Vhs.title(sub), cx, titleY + 28, MenuOsd.a(paused ? 150 : 210, in) | (paused ? Vhs.ASH : Vhs.DRIED_BLOOD), 1f, 0.2f);

        MenuOsd.draw(g, width, height, in, null);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
