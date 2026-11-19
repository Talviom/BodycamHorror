package com.talviom.bodycamhorror.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;

import java.util.Random;

/**
 * Клиент: главное меню вместо ванильного титульного экрана.
 *
 * Кадр с нагрудной камеры: тёмный коридор, в конце открытая дверь со светом, на полпути слабая лампа.
 * Камера чуть покачивается, как в руках, и немного следует за мышью. Свет иногда заикается и гаснет,
 * а пока темно, в коридоре кто-то появляется: сначала в дверном проёме, потом ближе, потом исчезает.
 * Поверх плёночное зерно, тёмные края, срывы ленты и служебные надписи камеры.
 * Название печатной машинкой с бледным двойником, пункты меню простым выцветшим текстом.
 * Под меню играет наша музыка меню (MusicDirector). Отключается в настройках клиента: hud.customMenu.
 */
public final class BhTitleScreen extends Screen {
    private static final ResourceLocation[] CORRIDOR = {
            Vhs.rl("textures/gui/menu/corridor_0.png"),
            Vhs.rl("textures/gui/menu/corridor_1.png"),
            Vhs.rl("textures/gui/menu/corridor_2.png")
    };
    private static final int BG_W = 960;
    private static final int BG_H = 540;

    /** Первое открытие после запуска игры: долгий выход из черноты. Потом короткий. */
    private static boolean firstOpen = true;

    private final Random rnd = new Random();
    private final long openedAt = System.currentTimeMillis();
    private final long fadeMs;
    private final String omen;

    // Свет в коридоре: заикание, затем темнота, затем свет возвращается (иногда уже не один).
    private long nextStutterAt;
    private long stutterUntil;
    private long blackoutUntil;
    private long toggleAt;
    private boolean stutterOn;
    private int figure;
    private int pendingFigure = -1;
    private float steadyDip;
    private long steadyDipUntil;

    // Срыв ленты: полоса кадра на миг съезжает вбок.
    private long nextTearAt;
    private long tearUntil;
    private int tearY;
    private int tearH;
    private int tearShift;

    /** Прозрачность надписей (0..1) в текущем кадре: при выходе из черноты они проступают позже фона. */
    private float textAlpha = 1f;

    public BhTitleScreen() {
        super(Component.translatable("narrator.screen.title"));
        fadeMs = firstOpen ? 2600 : 700;
        firstOpen = false;
        String[] omens = Vhs.trList("bh.menu.omens");
        omen = omens[rnd.nextInt(omens.length)];
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = menuTop();
        int step = step();
        addRenderableWidget(new TapeButton(cx, y, Vhs.tr("bh.menu.singleplayer"), this::textAlpha, b -> minecraft.setScreen(new SelectWorldScreen(this))));
        TapeButton mp = new TapeButton(cx, y + step, Vhs.tr("bh.menu.multiplayer"), this::textAlpha, b -> minecraft.setScreen(
                minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)));
        mp.active = minecraft.allowsMultiplayer();
        addRenderableWidget(mp);
        addRenderableWidget(new TapeButton(cx, y + step * 2, Vhs.tr("bh.menu.options"), this::textAlpha, b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new TapeButton(cx, y + step * 3, Vhs.tr("bh.menu.mods"), this::textAlpha, b -> minecraft.setScreen(new ModListScreen(this))));
        addRenderableWidget(new TapeButton(cx, y + step * 4 + 6, Vhs.tr("bh.menu.quit"), this::textAlpha, b -> minecraft.stop()));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Мир под меню (только в сценарии проверки) не ставится на паузу. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- раскладка ----------

    private Component titleText() {
        return Vhs.title("BODYCAM HORROR");
    }

    private float titleScale() {
        float s = width * 0.6f / Math.max(1, Vhs.width(titleText()));
        return Mth.clamp(s, 1.5f, 3.5f);
    }

    private int titleY() {
        return Math.max(26, (int) (height * 0.17f));
    }

    private int step() {
        return height < 260 ? 13 : 15;
    }

    /** Высота столбца пунктов: четыре шага, отступ перед QUIT и сама строка. */
    private int menuHeight() {
        return step() * 4 + 6 + 11;
    }

    /**
     * Верх меню: под названием, примерно с середины экрана, но так, чтобы QUIT не наезжал
     * на строку версии внизу (в маленьком окне 854x480 наезжал).
     */
    private int menuTop() {
        int belowTitle = titleY() + Math.round(9 * titleScale()) + 34;
        int wanted = Math.max(belowTitle, (int) (height * 0.55f));
        int limit = height - 40 - menuHeight();
        return Math.max(belowTitle - 12, Math.min(wanted, limit));
    }

    // ---------- кадр ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        long t = now - openedAt;
        updateLights(now);
        updateTear(now);

        g.fill(0, 0, width, height, 0xFF060606);
        float light = lightLevel(now);
        if (light > 0.01f) drawCorridor(g, now, mouseX, mouseY, light, 0);
        if (now < tearUntil && light > 0.01f) {
            g.enableScissor(0, tearY, width, tearY + tearH);
            g.fill(0, tearY, width, tearY + tearH, 0xFF060606);
            drawCorridor(g, now, mouseX, mouseY, Math.min(1f, light * 1.15f), tearShift);
            g.disableScissor();
            g.fill(0, tearY, width, tearY + 1, 0x30000000 | Vhs.BONE);
        }

        Vhs.noise(g, 0, 0, width, height, 0.15f);
        Vhs.vignette(g, width, height, 1f);
        Vhs.flicker(g, width, height);

        float fade = Mth.clamp(t / (float) fadeMs, 0f, 1f);
        textAlpha = Mth.clamp((t - fadeMs * 0.35f) / (fadeMs * 0.65f), 0f, 1f);
        drawTitle(g);
        drawOsd(g, now);
        super.render(g, mouseX, mouseY, partialTick);

        if (fade < 1f) g.fill(0, 0, width, height, (int) ((1f - fade) * 255) << 24);
    }

    private void drawCorridor(GuiGraphics g, long now, int mouseX, int mouseY, float light, int shiftX) {
        ResourceLocation tex = CORRIDOR[figure];
        if (!Vhs.has(tex)) return;
        float cover = Math.max(width / (float) BG_W, height / (float) BG_H) * 1.07f;
        float dw = BG_W * cover;
        float dh = BG_H * cover;
        // Камера дышит, как в руках, и чуть-чуть следует за мышью.
        double s = now / 1000.0;
        float sx = (float) (Math.sin(s * 0.43) * 3.0 + Math.sin(s * 1.13 + 2.0) * 1.1) - (mouseX / (float) Math.max(1, width) - 0.5f) * 8f;
        float sy = (float) (Math.sin(s * 0.61 + 1.0) * 2.0 + Math.sin(s * 1.7) * 0.6) - (mouseY / (float) Math.max(1, height) - 0.5f) * 5f;
        g.pose().pushPose();
        g.pose().translate((width - dw) / 2f + sx + shiftX, (height - dh) / 2f + sy, 0f);
        g.pose().scale(cover, cover, 1f);
        // Серый кадр окрашивается в выцветшую кость.
        RenderSystem.setShaderColor(1.0f * light, 0.96f * light, 0.87f * light, 1f);
        g.blit(tex, 0, 0, 0f, 0f, BG_W, BG_H, BG_W, BG_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private void drawTitle(GuiGraphics g) {
        if (textAlpha <= 0.01f) return;
        int cx = width / 2;
        int y = titleY();
        float scale = titleScale();
        Vhs.ghostCentered(g, Vhs.title(Vhs.tr("bh.menu.recovered")), cx, y - 14, alpha(150) | Vhs.ASH, 1f, 0f);
        Vhs.ghostCentered(g, titleText(), cx, y, alpha(235) | Vhs.BONE, scale, 0.35f);
        Vhs.ghostCentered(g, Vhs.title(omen), cx, y + Math.round(9 * scale) + 6, alpha(220) | Vhs.DRIED_BLOOD, 1f, 0.25f);
    }

    /** Служебные надписи камеры по углам и строка версии внизу. */
    private void drawOsd(GuiGraphics g, long now) {
        if (textAlpha <= 0.01f) return;
        int corner = MenuOsd.draw(g, width, height, textAlpha, Vhs.tr("bh.menu.tape"));

        String version = "BODYCAM HORROR " + modVersion() + "  FORGE " + ForgeVersion.getVersion()
                + "  MC " + SharedConstants.getCurrentVersion().getName();
        // Ванильная строка об авторских правах на языке игры.
        String copyright = Vhs.tr("title.credits").toUpperCase(java.util.Locale.ROOT);
        int room = width - 2 * (12 + corner + 12);
        int soot = alpha(150) | Vhs.SOOT;
        int menuBottom = menuTop() + menuHeight();
        // Строки внизу рисуются, только если им хватает места и рядом с датой, и под меню.
        if (Vhs.width(version) <= room && menuBottom <= height - 36) {
            Vhs.plain(g, version, (width - Vhs.width(version)) / 2, height - 33, soot);
        }
        if (Vhs.width(copyright) <= room && menuBottom <= height - 25) {
            Vhs.plain(g, copyright, (width - Vhs.width(copyright)) / 2, height - 22, soot);
        }
    }

    private float textAlpha() {
        return textAlpha;
    }

    private int alpha(int max) {
        return Mth.clamp((int) (max * textAlpha), 4, 255) << 24;
    }

    private static String modVersion() {
        return ModList.get().getModContainerById(BodycamHorror.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("");
    }

    // ---------- свет ----------

    private void updateLights(long now) {
        if (nextStutterAt == 0) nextStutterAt = now + 7000 + rnd.nextInt(5000);
        if (now >= nextStutterAt && now >= blackoutUntil) {
            stutterUntil = now + 450 + rnd.nextInt(650);
            blackoutUntil = stutterUntil + 250 + rnd.nextInt(500);
            toggleAt = now;
            stutterOn = false;
            nextStutterAt = blackoutUntil + 9000 + rnd.nextInt(13000);
            pendingFigure = nextFigure();
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LAMP_BUZZ.get(), 0.9f + rnd.nextFloat() * 0.2f, 0.22f));
            }
        }
        // Пока темно, в коридоре всё меняется.
        if (pendingFigure >= 0 && now >= stutterUntil) {
            figure = pendingFigure;
            pendingFigure = -1;
        }
    }

    /** Кто стоит в коридоре, когда свет вернётся: обычно никого, иногда в двери, редко ближе. */
    private int nextFigure() {
        return switch (figure) {
            case 0 -> rnd.nextInt(2) == 0 ? 1 : 0;
            case 1 -> rnd.nextInt(3) == 0 ? 2 : 0;
            default -> 0;
        };
    }

    private float lightLevel(long now) {
        if (now < stutterUntil) {
            if (now >= toggleAt) {
                stutterOn = !stutterOn;
                toggleAt = now + 30 + rnd.nextInt(110);
            }
            return stutterOn ? 0.75f : 0.1f;
        }
        if (now < blackoutUntil) return 0f;
        if (now > steadyDipUntil) {
            steadyDip = rnd.nextInt(14) == 0 ? 0.06f + rnd.nextFloat() * 0.1f : 0f;
            steadyDipUntil = now + 60 + rnd.nextInt(140);
        }
        return 0.84f + (float) Math.sin(now / 370.0) * 0.02f - steadyDip;
    }

    private void updateTear(long now) {
        if (nextTearAt == 0) nextTearAt = now + 4000 + rnd.nextInt(6000);
        if (now >= nextTearAt) {
            tearUntil = now + 70 + rnd.nextInt(120);
            tearH = 4 + rnd.nextInt(Math.max(1, height / 14));
            tearY = rnd.nextInt(Math.max(1, height - tearH));
            tearShift = (rnd.nextBoolean() ? 1 : -1) * (3 + rnd.nextInt(9));
            nextTearAt = now + 5000 + rnd.nextInt(9000);
        }
    }
}
