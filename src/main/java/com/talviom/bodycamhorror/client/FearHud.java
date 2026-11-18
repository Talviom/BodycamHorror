package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Клиент: сердце страха и батарейка фонарика в левом верхнем углу, под ними реплики персонажа.
 * Сердце бьётся тем чаще и сильнее, чем выше страх (от 60 до 180 ударов в минуту).
 */
public final class FearHud {
    private static final ResourceLocation HEART = Vhs.rl("textures/gui/heart.png");
    /** Анатомическое сердце: 4 кадра 48x48 в одну строку (tools/assets/make_gui.py). */
    private static final int HEART_FRAME = 48;
    private static final ResourceLocation BATTERY = Vhs.rl("textures/gui/battery.png");
    /** Батарея: 5 кадров 24x24 (пусто, 25, 50, 75, 100 %), на экране 20x20. */
    private static final int BATTERY_FRAME = 24;
    private static final int BATTERY_SIZE = 20;
    private static final long COMMENT_MS = 3500;

    private FearHud() {}

    static void draw(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // Левый верхний угол: внизу слева сердце и реплики закрывал чат.
        int x = 10;
        int bottom = 10 + 36;

        if (ClientState.inMatch) {
            int size = drawHeart(g, x, bottom);
            drawComment(g, x, bottom + 6 + 9);
            x += size + 6;
        }
        if (ClientState.hasFlashlight) drawBattery(g, x, bottom - BATTERY_SIZE);
    }

    /** @return сколько места заняло сердце по ширине */
    private static int drawHeart(GuiGraphics g, int x, int bottom) {
        float fear = ClientState.fear;
        float bpm = 60f + fear * 1.2f;
        float period = 60000f / bpm;
        float phase = (System.currentTimeMillis() % (long) period) / period;

        // Кадры: 0 покой, 1 малый удар, 2 большой удар, 3 паника.
        int frame;
        float bump;
        if (phase < 0.12f) {
            frame = fear >= 85f ? 3 : 2;
            bump = 1f;
        } else if (phase < 0.26f) {
            frame = 1;
            bump = 0.5f;
        } else {
            frame = 0;
            bump = 0f;
        }
        float k = fear / 100f;
        int base = 26 + Math.round(k * 8);
        int size = base + Math.round(bump * (2 + k * 4));
        float alpha = 0.45f + 0.55f * Mth.clamp(k * 2f, 0f, 1f);
        int y = bottom - size;
        int dx = x + (base + 6 - size) / 2;

        if (Vhs.has(HEART)) {
            Vhs.blitAlpha(g, HEART, dx, y, size, size, frame * HEART_FRAME, 0, HEART_FRAME, HEART_FRAME, HEART_FRAME * 4, HEART_FRAME, alpha);
        } else {
            float scale = size / 9f;
            int a = (int) (alpha * 255) << 24;
            g.pose().pushPose();
            g.pose().translate(dx, y, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(Minecraft.getInstance().font, "♥", 0, 0, a | 0xC62828, false);
            g.pose().popPose();
        }
        return base + 6;
    }

    private static void drawComment(GuiGraphics g, int x, int y) {
        long age = System.currentTimeMillis() - ClientState.commentAt;
        if (ClientState.comment.isEmpty() || age > COMMENT_MS) return;
        float a = age < 300 ? age / 300f : age > COMMENT_MS - 600 ? (COMMENT_MS - age) / 600f : 1f;
        int alpha = Mth.clamp((int) (a * 230), 8, 230) << 24;
        // Чем сильнее страх, тем заметнее дрожит реплика.
        float shake = ClientState.fear >= 85f ? 1.0f : ClientState.fear >= 60f ? 0.5f : 0f;
        Vhs.ghost(g, Vhs.title(ClientState.comment), x, y - 9, alpha | Vhs.BONE, 1f, shake);
    }

    private static void drawBattery(GuiGraphics g, int x, int y) {
        float b = ClientState.battery;
        boolean low = b < 0.15f;
        boolean on = ClientState.flashlightOn;
        // На слабом заряде значок мигает.
        if (on && low && (System.currentTimeMillis() / 250) % 2 == 0) return;
        int frame = b <= 0.02f ? 0 : b < 0.375f ? 1 : b < 0.625f ? 2 : b < 0.875f ? 3 : 4;
        float alpha = on ? 1f : 0.5f;
        if (Vhs.has(BATTERY)) {
            Vhs.blitAlpha(g, BATTERY, x, y, BATTERY_SIZE, BATTERY_SIZE, frame * BATTERY_FRAME, 0,
                    BATTERY_FRAME, BATTERY_FRAME, BATTERY_FRAME * 5, BATTERY_FRAME, alpha);
        } else {
            int a = (int) (alpha * 255) << 24;
            int col = low ? 0xE53935 : 0xE0E0E0;
            g.fill(x, y + 4, x + 13, y + 5, a | col);
            g.fill(x, y + 11, x + 13, y + 12, a | col);
            g.fill(x, y + 4, x + 1, y + 12, a | col);
            g.fill(x + 12, y + 4, x + 13, y + 12, a | col);
            g.fill(x + 13, y + 6, x + 15, y + 10, a | col);
            int bars = frame;
            for (int i = 0; i < bars; i++) g.fill(x + 2 + i * 3, y + 6, x + 4 + i * 3, y + 10, a | col);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
