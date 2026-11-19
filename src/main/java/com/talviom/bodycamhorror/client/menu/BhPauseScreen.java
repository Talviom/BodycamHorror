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
import net.minecraft.world.level.GameType;

/**
 * Клиент: меню паузы (Esc) вместо ванильного. Мир за ним темнеет, как будто камера ушла в режим ожидания:
 * зерно, тёмные края, служебные надписи камеры, слово STANDBY печатной машинкой.
 * Пункты: RESUME, START THE MATCH (у хоста, пока матч не идёт), OPTIONS, OPEN TO FRIENDS (у хоста, пока мир не открыт:
 * сразу на порту 25565), SAVE AND QUIT или DISCONNECT.
 * В одиночной игре мир на паузе, как в ванильном меню; в сетевой игре идёт дальше, о чём и говорит подпись.
 */
public final class BhPauseScreen extends Screen {
    /** Порт для друзей: его же хост делит в PortHole (см. pack/INSTALL.txt). */
    public static final int FRIENDS_PORT = 25565;

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
        // Погибший в матче хост наблюдает (inMatch у него false), ему кнопка запуска не нужна.
        boolean canStart = host != null && !com.talviom.bodycamhorror.client.ClientState.inMatch
                && minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR;
        int rows = 3 + (canShare ? 1 : 0) + (canStart ? 1 : 0);
        int y = Math.max(height / 2 - 4, height - 40 - (rows * step + 6));

        addRenderableWidget(new TapeButton(cx, y, Vhs.tr("bh.pause.resume"), this::textAlpha, b -> onClose()));
        int next = y + step;
        if (canStart) {
            // Хост в лобби запускает матч отсюда, а не командой /bh start.
            addRenderableWidget(new TapeButton(cx, next, Vhs.tr("bh.pause.start"), this::textAlpha, b -> {
                minecraft.player.connection.sendCommand("bh start");
                onClose();
            }));
            next += step;
        }
        addRenderableWidget(new TapeButton(cx, next, Vhs.tr("bh.menu.options"), this::textAlpha,
                b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        next += step;
        if (canShare) {
            addRenderableWidget(new TapeButton(cx, next, Vhs.tr("bh.pause.share"), this::textAlpha, b -> openToFriends(host)));
            next += step;
        }
        TapeButton quit = new TapeButton(cx, next + 6, Vhs.tr(local ? "bh.pause.save_quit" : "bh.pause.disconnect"), this::textAlpha, b -> {
            b.active = false;
            leave();
        });
        addRenderableWidget(quit);
    }

    /**
     * Открыть мир для друзей одним нажатием: сразу порт 25565 (его хост делит через PortHole), режим приключения,
     * без читов для гостей (хост свои команды сохраняет, если они разрешены в мире). Если порт занят,
     * открывается ванильный экран, где порт можно выбрать самому.
     */
    private void openToFriends(IntegratedServer host) {
        if (host.publishServer(GameType.ADVENTURE, false, FRIENDS_PORT)) {
            minecraft.gui.getChat().addMessage(Component.translatable("commands.publish.started", FRIENDS_PORT));
            com.talviom.bodycamhorror.BodycamHorror.LOGGER.info("[BH menu] world opened to friends on port {}", FRIENDS_PORT);
            minecraft.setScreen(new BhPauseScreen());
        } else {
            minecraft.setScreen(new ShareToLanScreen(this));
        }
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
        Vhs.ghostCentered(g, Vhs.title(Vhs.tr("bh.pause.title")), cx, titleY, MenuOsd.a(230, in) | Vhs.BONE, 2.5f, 0.3f);
        boolean paused = isPauseScreen() && minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished();
        String sub = Vhs.tr(paused ? "bh.pause.paused" : "bh.pause.running");
        Vhs.ghostCentered(g, Vhs.title(sub), cx, titleY + 28, MenuOsd.a(paused ? 150 : 210, in) | (paused ? Vhs.ASH : Vhs.DRIED_BLOOD), 1f, 0.2f);

        MenuOsd.draw(g, width, height, in, null);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
