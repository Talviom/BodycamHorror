package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Клиент: сердце страха в левом верхнем углу (под ним реплики персонажа), а в левом нижнем углу
 * пробирка с кровью (здоровье, HealthHud) и рядом лежачая батарейка фонарика со значком фонарика.
 * Сердце бьётся тем чаще и сильнее, чем выше страх (от 60 до 180 ударов в минуту).
 */
public final class FearHud {
    private static final ResourceLocation HEART = Vhs.rl("textures/gui/heart.png");
    /** Анатомическое сердце: 4 кадра 48x48 в одну строку (tools/assets/make_gui.py). */
    private static final int HEART_FRAME = 48;
    /** Лежачая батарейка: три слоя 40x18 (корпус, заряд серым, стекло и ржавчина), tools/assets/make_hud.py. */
    private static final ResourceLocation BATTERY = Vhs.rl("textures/gui/battery_cell.png");
    private static final int BATTERY_TEX_W = 40;
    private static final int BATTERY_TEX_H = 18;
    /** Заряд в текстуре: столбцы 4..32. */
    private static final int CHARGE_X = 4;
    private static final int CHARGE_W = 29;
    /** Значок фонарика: три кадра 24x18 (выключен, светит, сел). */
    private static final ResourceLocation TORCH = Vhs.rl("textures/gui/torch.png");
    private static final int TORCH_TEX_W = 24;
    /** На экране в масштабе пробирки (HealthHud.HUD_SCALE): батарейка 34x15, фонарик 20x15 пикселей интерфейса. */
    private static final int BATTERY_W = Math.round(BATTERY_TEX_W * HealthHud.HUD_SCALE);
    private static final int BATTERY_H = Math.round(BATTERY_TEX_H * HealthHud.HUD_SCALE);
    private static final int MARGIN = 8;
    /** Насколько поднять нижний угол, пока открыт чат (строка ввода внизу экрана). */
    private static final int CHAT_LIFT = 14;
    private static final long COMMENT_MS = 3500;

    private FearHud() {}

    static void draw(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // Левый верхний угол: сердце страха и под ним реплики.
        if (ClientState.inMatch) {
            int heartBottom = 10 + 36;
            drawHeart(g, 10, heartBottom);
            drawComment(g, 10, heartBottom + 6 + 9);
        }
        // Левый нижний угол: пробирка, рядом внизу батарейка и фонарик.
        int bottom = h - MARGIN - (mc.screen instanceof ChatScreen ? CHAT_LIFT : 0);
        int x = MARGIN;
        if (HealthHud.drawHealth(g, x, bottom)) x += HealthHud.WIDTH + 7;
        if (ClientState.hasFlashlight) drawBattery(g, x, bottom - BATTERY_H);
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
        boolean dead = b <= 0.02f;
        // На слабом заряде мигает остаток заряда.
        boolean blink = on && low && (System.currentTimeMillis() / 250) % 2 == 0;
        float alpha = on ? 1f : 0.55f;
        if (!Vhs.has(BATTERY)) {
            int a = (int) (alpha * 255) << 24;
            g.fill(x, y, x + BATTERY_W - 2, y + BATTERY_H, a | Vhs.SOOT);
            g.fill(x + BATTERY_W - 2, y + 3, x + BATTERY_W, y + BATTERY_H - 3, a | Vhs.ASH);
            if (!blink) g.fill(x + 1, y + 1, x + 1 + Math.round((BATTERY_W - 4) * b), y + BATTERY_H - 1,
                    a | (low ? Vhs.DRIED_BLOOD : Vhs.BONE));
            return;
        }
        int sheetW = BATTERY_TEX_W * 3;
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(HealthHud.HUD_SCALE, HealthHud.HUD_SCALE, 1f);
        Vhs.blitAlpha(g, BATTERY, 0, 0, BATTERY_TEX_W, BATTERY_TEX_H, 0, 0, BATTERY_TEX_W, BATTERY_TEX_H, sheetW, BATTERY_TEX_H, alpha);
        int fill = Math.round(CHARGE_W * Mth.clamp(b, 0f, 1f));
        if (fill > 0 && !blink) {
            int rgb = low ? Vhs.DRIED_BLOOD : Vhs.BONE;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 0.85f * alpha);
            g.blit(BATTERY, CHARGE_X, 0, fill, BATTERY_TEX_H, BATTERY_TEX_W + CHARGE_X, 0, fill, BATTERY_TEX_H, sheetW, BATTERY_TEX_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
        Vhs.blitAlpha(g, BATTERY, 0, 0, BATTERY_TEX_W, BATTERY_TEX_H, BATTERY_TEX_W * 2, 0, BATTERY_TEX_W, BATTERY_TEX_H, sheetW, BATTERY_TEX_H, alpha);
        if (Vhs.has(TORCH)) {
            int frame = dead ? 2 : on ? 1 : 0;
            Vhs.blitAlpha(g, TORCH, BATTERY_TEX_W + 6, 0, TORCH_TEX_W, BATTERY_TEX_H, frame * TORCH_TEX_W, 0,
                    TORCH_TEX_W, BATTERY_TEX_H, TORCH_TEX_W * 3, BATTERY_TEX_H, dead ? 0.8f : alpha);
        }
        g.pose().popPose();
    }
}
