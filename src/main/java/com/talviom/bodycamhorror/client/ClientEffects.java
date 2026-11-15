package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.HorrorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Клиент: эффекты экрана. Здоровье через красные края, виньетка, помехи бодикама.
 *
 * Важно: вложенные классы-подписчики и их методы должны называться не так, как в ClientHud.
 * Шина событий Forge даёт обработчику имя из простого имени класса и имени метода,
 * и два одинаковых имени в одном пакете склеиваются в один обработчик (игра падает при запуске).
 */
public final class ClientEffects {
    private static int glitchTicks;
    private static float glitchStrength;

    private ClientEffects() {}

    public static void startGlitch(int ticks, float strength) {
        glitchTicks = Math.max(glitchTicks, ticks);
        glitchStrength = strength;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class FxForgeEvents {
        @SubscribeEvent
        public static void onFxClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END && glitchTicks > 0) glitchTicks--;
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class FxModEvents {
        @SubscribeEvent
        public static void registerFxOverlays(RegisterGuiOverlaysEvent e) {
            e.registerBelowAll("bodycam_fx", (gui, g, partialTick, width, height) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.options.hideGui) return;

                if (HorrorConfig.VIGNETTE.get()) drawEdges(g, width, height, 0x000000, 0.55f);

                if (HorrorConfig.HEALTH_FEEDBACK.get() && !mc.player.isCreative() && !mc.player.isSpectator()) {
                    float missing = 1f - mc.player.getHealth() / mc.player.getMaxHealth();
                    if (missing > 0.05f) {
                        float pulse = missing > 0.6f
                                ? 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 180.0)
                                : 1f;
                        drawEdges(g, width, height, 0x8B0000, Math.min(0.75f, missing * 0.8f) * pulse);
                    }
                }

                if (glitchTicks > 0 && HorrorConfig.GLITCH.get()) drawGlitch(g, width, height);
            });
        }
    }

    /** Затемнение или покраснение краёв экрана. */
    private static void drawEdges(GuiGraphics g, int w, int h, int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        int full = (a << 24) | rgb;
        int band = h / 4;
        g.fillGradient(0, 0, w, band, full, rgb);
        g.fillGradient(0, h - band, w, h, rgb, full);

        int side = w / 6;
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int ai = (int) (a * k * k);
            if (ai <= 0) continue;
            int color = (ai << 24) | rgb;
            int x1 = i * side / steps;
            int x2 = (i + 1) * side / steps;
            g.fill(x1, 0, x2, h, color);
            g.fill(w - x2, 0, w - x1, h, color);
        }
    }

    /** Помехи: случайные полосы, бегущая полоса и редкие вспышки. */
    private static void drawGlitch(GuiGraphics g, int w, int h) {
        long now = System.currentTimeMillis();
        Random r = new Random(now / 40);
        int bars = (int) (8 + 30 * glitchStrength);
        for (int i = 0; i < bars; i++) {
            int y = r.nextInt(Math.max(1, h));
            int bh = 1 + r.nextInt(3);
            int x = r.nextInt(Math.max(1, w));
            int bw = 20 + r.nextInt(Math.max(1, w / 2));
            int alpha = 30 + r.nextInt(90);
            int rgb = r.nextBoolean() ? 0xFFFFFF : 0x000000;
            g.fill(x, y, Math.min(w, x + bw), y + bh, (alpha << 24) | rgb);
        }
        int rolling = (int) ((now / 6) % (h + 60)) - 60;
        g.fill(0, rolling, w, rolling + 40, 0x22FFFFFF);
        if (r.nextInt(6) == 0) g.fill(0, 0, w, h, 0x18FFFFFF);
    }
}
