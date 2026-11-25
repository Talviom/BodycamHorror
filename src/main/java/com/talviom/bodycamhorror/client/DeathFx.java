package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.SpectateCyclePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Клиент: при смерти экран гаснет как старый ЭЛТ-монитор.
 * <ol>
 *   <li>Картинка схлопывается в тусклую полоску цвета кости, полоска в точку (0,4 с), щелчок выключения.</li>
 *   <li>Полторы секунды черноты: звук игры почти пропадает, REC гаснет.</li>
 *   <li>Секунда помех с бледным «НЕТ СИГНАЛА», из них проступает камера союзника (в матче)
 *       или экран SIGNAL LOST (вне матча).</li>
 * </ol>
 * У тех, кто смотрел камеру погибшего, то же самое коротко (около 1 с). Если раунд начался раньше,
 * чем эффект закончился, чернота обрывается коротким «включением».
 * Наблюдение в матче: оверлей «ТРАНСЛЯЦИЯ» с зерном поверх камеры союзника, ЛКМ и ПКМ переключают союзников;
 * без живых союзников свободный полёт с мелким «НЕТ СИГНАЛА».
 * Выключатель: hud.deathEffect в клиентском конфиге.
 */
public final class DeathFx {
    private static final Random RND = new Random();

    private static long startAt;
    private static boolean cam;
    private static boolean active;
    private static boolean staticPlayed;
    private static long cutAt;

    private static final long CUT_MS = 300;

    private DeathFx() {}

    private static long collapseMs() { return cam ? 250 : 400; }
    private static long blackMs() { return cam ? 350 : 1500; }
    private static long staticMs() { return cam ? 500 : 1000; }
    private static long totalMs() { return collapseMs() + blackMs() + staticMs(); }

    /** Начать эффект: свой (cam = false) или короткий, когда погас союзник, за которым смотришь. */
    public static void start(boolean camOnly) {
        if (!BhConfig.DEATH_EFFECT.get()) return;
        Minecraft mc = Minecraft.getInstance();
        // Свой эффект важнее: короткий не перебивает идущий полный.
        if (camOnly && active && !cam) return;
        cam = camOnly;
        startAt = System.currentTimeMillis();
        active = true;
        staticPlayed = false;
        cutAt = 0;
        if (!camOnly) mc.getSoundManager().stop();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CRT_OFF.get(), camOnly ? 1.15f : 1f, camOnly ? 0.45f : 0.9f));
    }

    /** Идёт эффект (HUD камеры и REC не рисуются). */
    public static boolean hidesHud() {
        return active;
    }

    /** Переключать союзников можно, только когда проступила картинка. */
    private static boolean blocksInput() {
        return active && System.currentTimeMillis() - startAt < collapseMs() + blackMs();
    }

    /** Чернота после смерти: игра почти беззвучна, слышно только наши щелчок и помехи. */
    private static boolean muting() {
        if (!active || cam || cutAt > 0) return false;
        return System.currentTimeMillis() - startAt < collapseMs() + blackMs();
    }

    private static void tick() {
        if (!active) return;
        long now = System.currentTimeMillis();
        long t = now - startAt;
        Minecraft mc = Minecraft.getInstance();
        // Новый раунд начался раньше конца эффекта (игрок снова не наблюдатель): короткое «включение».
        if (cutAt == 0 && !cam && ClientState.inMatch && mc.player != null && !mc.player.isSpectator() && t > 400) {
            cutAt = now;
        }
        if (cutAt > 0) {
            if (now - cutAt > CUT_MS) active = false;
            return;
        }
        if (!staticPlayed && t >= collapseMs() + blackMs()) {
            staticPlayed = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DEATH_STATIC.get(), 1f, cam ? 0.3f : 0.55f));
        }
        if (t > totalMs()) active = false;
    }

    // ---------- рисование ----------

    /** Слои: схлопывание и чернота выше заставки раунда, помехи ниже (итоги раунда проступают сквозь них). */
    private static final float TOP_Z = 900f;
    private static final float UNDER_BANNER_Z = 700f;

    private static void drawOverlay(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (active) {
            draw(g, w, h);
        } else if (ClientState.matchSpectator && mc.player.isSpectator() && !mc.options.hideGui) {
            drawSpectator(g, mc, w, h);
        }
    }

    /** Эффект поверх экрана смерти вне матча (вызывает BhDeathScreen). */
    public static void drawOverScreen(GuiGraphics g, int w, int h) {
        if (active) draw(g, w, h);
    }

    private static void draw(GuiGraphics g, int w, int h) {
        long now = System.currentTimeMillis();
        long t = now - startAt;
        if (cutAt > 0) {
            layer(g, TOP_Z, () -> drawPowerOn(g, w, h, (now - cutAt) / (float) CUT_MS));
            return;
        }
        long c = collapseMs();
        long b = blackMs();
        if (t < c) {
            layer(g, TOP_Z, () -> drawCollapse(g, w, h, t / (float) c));
        } else if (t < c + b) {
            layer(g, TOP_Z, () -> drawBlack(g, w, h, t - c));
        } else {
            float s = Mth.clamp((t - c - b) / (float) staticMs(), 0f, 1f);
            layer(g, UNDER_BANNER_Z, () -> drawStatic(g, w, h, s));
        }
    }

    private static void layer(GuiGraphics g, float z, Runnable r) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, z);
        try {
            r.run();
        } finally {
            g.pose().popPose();
        }
    }

    /** Схлопывание: сначала по высоте в полоску, потом по ширине в точку. Полоска светится тусклой костью. */
    private static void drawCollapse(GuiGraphics g, int w, int h, float p) {
        int cx = w / 2;
        int cy = h / 2;
        if (p < 0.65f) {
            float q = p / 0.65f;
            float k = (1f - q) * (1f - q);
            int half = Math.max(1, Math.round(h / 2f * k));
            g.fill(0, 0, w, cy - half, 0xFF000000);
            g.fill(0, cy + half, w, h, 0xFF000000);
            int glow = Math.round((0.12f + 0.5f * q) * 255f);
            g.fill(0, cy - half, w, cy + half, (glow << 24) | Vhs.BONE);
        } else {
            float q = (p - 0.65f) / 0.35f;
            g.fill(0, 0, w, h, 0xFF000000);
            int half = Math.max(1, Math.round(w / 2f * (1f - q) * (1f - q)));
            int a = Math.round((0.8f - 0.2f * q) * 255f);
            g.fill(cx - half, cy - 1, cx + half, cy + 1, (a << 24) | Vhs.BONE);
            g.fill(cx - half / 3, cy - 1, cx + half / 3, cy + 1, (Math.min(255, a + 30) << 24) | Vhs.BONE);
        }
    }

    /** Чернота, первые доли секунды в центре ещё тлеет точка. */
    private static void drawBlack(GuiGraphics g, int w, int h, long t) {
        g.fill(0, 0, w, h, 0xFF000000);
        float dot = 1f - Mth.clamp(t / 450f, 0f, 1f);
        if (dot > 0f) {
            int a = Math.round(dot * dot * 200f);
            g.fill(w / 2 - 1, h / 2 - 1, w / 2 + 1, h / 2 + 1, (a << 24) | Vhs.BONE);
        }
    }

    /** Помехи: чернота редеет, зерно и рваные полосы, бледное «НЕТ СИГНАЛА». s от 0 до 1. */
    private static void drawStatic(GuiGraphics g, int w, int h, float s) {
        float fade = 1f - s;
        g.fill(0, 0, w, h, Math.round(255f * fade * fade) << 24);
        Vhs.noise(g, 0, 0, w, h, 0.55f * fade + 0.1f);
        Vhs.noise(g, 0, 0, w, h, 0.35f * fade);
        speckle(g, w, h, fade);
        Random r = new Random(System.currentTimeMillis() / 45);
        int bars = Math.round(6 + 18 * fade);
        for (int i = 0; i < bars; i++) {
            int y = r.nextInt(Math.max(1, h));
            int bh = 1 + r.nextInt(3);
            int alpha = Math.round((20 + r.nextInt(70)) * fade);
            g.fill(0, y, w, y + bh, (alpha << 24) | (r.nextBoolean() ? Vhs.BONE : Vhs.ASH));
        }
        int rolling = (int) ((System.currentTimeMillis() / 5) % (h + 60)) - 60;
        g.fill(0, rolling, w, rolling + 30, (Math.round(30 * fade) << 24) | Vhs.BONE);
        if (!cam && fade > 0.05f) {
            int a = Math.max(8, Math.round(150 * fade));
            Vhs.ghostCentered(g, Vhs.title(Vhs.tr("bh.deathfx.no_signal")), w / 2, h / 2 - 8, (a << 24) | Vhs.ASH, 2f, 0.4f);
        }
    }

    /** Крупное зерно помех: точки кости и сажи, гуще в начале. */
    private static void speckle(GuiGraphics g, int w, int h, float fade) {
        Random r = new Random(System.nanoTime());
        int dots = Math.round(w * h / 18f * fade);
        for (int i = 0; i < dots; i++) {
            int x = r.nextInt(Math.max(1, w));
            int y = r.nextInt(Math.max(1, h));
            int a = 40 + r.nextInt(140);
            g.fill(x, y, x + 1, y + 1, (a << 24) | (r.nextInt(3) == 0 ? 0x000000 : Vhs.BONE));
        }
    }

    /** Новый раунд: из полоски экран быстро раскрывается обратно, с короткой вспышкой. */
    private static void drawPowerOn(GuiGraphics g, int w, int h, float p) {
        p = Mth.clamp(p, 0f, 1f);
        int cy = h / 2;
        int half = Math.max(1, Math.round(h / 2f * p * p));
        g.fill(0, 0, w, cy - half, 0xFF000000);
        g.fill(0, cy + half, w, h, 0xFF000000);
        int glow = Math.round((1f - p) * 0.5f * 255f);
        g.fill(0, cy - half, w, cy + half, (glow << 24) | Vhs.BONE);
        Vhs.noise(g, 0, cy - half, w, half * 2, 0.4f * (1f - p));
    }

    /** Наблюдение в матче: зерно и подписи «чужой камеры» или свободного полёта. */
    private static void drawSpectator(GuiGraphics g, Minecraft mc, int w, int h) {
        Entity camera = mc.getCameraEntity();
        boolean feed = camera instanceof Player && camera != mc.player;
        layer(g, 150f, () -> {
            Vhs.noise(g, 0, 0, w, h, feed ? 0.12f : 0.16f);
            if (feed) {
                String label = Vhs.tr("bh.deathfx.feed");
                Vhs.ghostCentered(g, Vhs.title(label), w / 2, h - 24, 0xB0000000 | Vhs.ASH, 1f, 0.15f);
                String hint = Vhs.tr("bh.deathfx.switch");
                Vhs.plain(g, hint, (w - Vhs.width(hint)) / 2, h - 12, 0xB0000000 | Vhs.ASH);
            }
        });
        // Свободный полёт: «НЕТ СИГНАЛА» слева вверху, под «CAM 01» рамки итогов раунда (внизу её рамка закрывала).
        if (!feed) {
            layer(g, 850f, () -> Vhs.ghost(g, Vhs.title(Vhs.tr("bh.deathfx.no_signal")), 10, 24,
                    0xB0000000 | Vhs.ASH, 1.5f, 0.2f));
        }
    }

    /** Свободный полёт погибшего в матче: своей камеры нет, REC и имя не рисуются. */
    static boolean freeFlight(Minecraft mc) {
        return mc.player != null && ClientState.matchSpectator && mc.player.isSpectator() && mc.getCameraEntity() == mc.player;
    }

    /** Имя камеры: в наблюдении за союзником его, иначе своё. Для правого верхнего угла (ClientHud). */
    static String cameraName(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (mc.player != null && mc.player.isSpectator() && camera instanceof Player p && p != mc.player) {
            return p.getGameProfile().getName();
        }
        return mc.player == null ? "" : mc.player.getGameProfile().getName();
    }

    // ---------- события ----------

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class DeathFxForgeEvents {
        @SubscribeEvent
        public static void onDeathFxClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) tick();
        }

        /** Чернота: новые звуки игры не играют, кроме наших щелчка и помех. */
        @SubscribeEvent
        public static void onDeathFxSound(PlaySoundEvent e) {
            if (!muting()) return;
            SoundInstance s = e.getSound();
            if (s == null) return;
            String path = s.getLocation().getPath();
            if (BodycamHorror.MOD_ID.equals(s.getLocation().getNamespace())
                    && (path.equals("crt_off") || path.equals("death_static"))) return;
            e.setSound(null);
        }

        /** Погибший в матче: ЛКМ следующий союзник, ПКМ предыдущий. Ванильное наблюдение по клику отключено. */
        @SubscribeEvent
        public static void onDeathFxClick(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !mc.player.isSpectator() || !ClientState.matchSpectator) return;
            if (!e.isAttack() && !e.isUseItem()) return;
            e.setCanceled(true);
            e.setSwingHand(false);
            if (blocksInput()) return;
            BhNetwork.CHANNEL.sendToServer(new SpectateCyclePacket(e.isAttack() ? 1 : -1));
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class DeathFxModEvents {
        @SubscribeEvent
        public static void registerDeathFxOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("bodycam_death", (gui, g, partialTick, width, height) -> drawOverlay(g, width, height));
        }
    }
}
