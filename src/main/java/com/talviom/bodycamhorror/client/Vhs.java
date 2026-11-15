package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Клиент: общий стиль VHS. Шрифт bodycamhorror:vhs, полосы развёртки, шум, смещение цветов.
 * Если какой-то текстуры или шрифта нет, рисуется простой запасной вариант, а игра не падает.
 */
public final class Vhs {
    public static final ResourceLocation FONT = rl("vhs");
    public static final ResourceLocation SCANLINES = rl("textures/gui/scanlines.png");
    public static final ResourceLocation NOISE = rl("textures/gui/noise.png");

    private static final Map<ResourceLocation, Boolean> PRESENT = new HashMap<>();
    private static final Random RND = new Random();

    private Vhs() {}

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(BodycamHorror.MOD_ID, path);
    }

    /** Есть ли ресурс в загруженных паках (результат запоминается). */
    public static boolean has(ResourceLocation res) {
        return PRESENT.computeIfAbsent(res, r -> Minecraft.getInstance().getResourceManager().getResource(r).isPresent());
    }

    public static boolean hasFont() {
        return has(rl("font/vhs.json"));
    }

    /** Текст шрифтом VHS (или обычным, если шрифта нет). */
    public static Component text(String s) {
        Component c = Component.literal(s);
        return hasFont() ? c.copy().withStyle(st -> st.withFont(FONT)) : c;
    }

    public static int width(String s) {
        return Minecraft.getInstance().font.width(text(s));
    }

    /** Текст с разъехавшимися красным и голубым каналами, как на старой плёнке. */
    public static void chroma(GuiGraphics g, String s, int x, int y, int color, float scale) {
        Font font = Minecraft.getInstance().font;
        Component c = text(s);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        int alpha = color & 0xFF000000;
        g.drawString(font, c, -1, 0, (alpha == 0 ? 0x90000000 : (int) ((alpha >>> 24) * 0.55f) << 24) | 0xFF2040, false);
        g.drawString(font, c, 1, 0, (alpha == 0 ? 0x90000000 : (int) ((alpha >>> 24) * 0.55f) << 24) | 0x20E0FF, false);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    /** То же, но по центру относительно cx. */
    public static void chromaCentered(GuiGraphics g, String s, int cx, int y, int color, float scale) {
        int w = Math.round(width(s) * scale);
        chroma(g, s, cx - w / 2, y, color, scale);
    }

    public static void plain(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text(s), x, y, color, false);
    }

    /** Полосы развёртки поверх области. */
    public static void scanlines(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        if (alpha <= 0f) return;
        if (has(SCANLINES)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            for (int yy = y; yy < y + h; yy += 4) {
                for (int xx = x; xx < x + w; xx += 256) {
                    int bw = Math.min(256, x + w - xx);
                    int bh = Math.min(4, y + h - yy);
                    g.blit(SCANLINES, xx, yy, 0f, 0f, bw, bh, 256, 4);
                }
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        } else {
            int a = (int) (alpha * 70) << 24;
            for (int yy = y; yy < y + h; yy += 3) g.fill(x, yy, x + w, yy + 1, a);
        }
    }

    /** Шум плёнки: текстура со случайным сдвигом каждый кадр. */
    public static void noise(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        if (alpha <= 0f) return;
        if (has(NOISE)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            int ox = RND.nextInt(128);
            int oy = RND.nextInt(128);
            for (int yy = y - oy; yy < y + h; yy += 128) {
                for (int xx = x - ox; xx < x + w; xx += 128) {
                    int x0 = Math.max(xx, x);
                    int y0 = Math.max(yy, y);
                    int x1 = Math.min(xx + 128, x + w);
                    int y1 = Math.min(yy + 128, y + h);
                    if (x1 <= x0 || y1 <= y0) continue;
                    g.blit(NOISE, x0, y0, (float) (x0 - xx), (float) (y0 - yy), x1 - x0, y1 - y0, 128, 128);
                }
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        } else {
            int dots = (int) (w * h / 60f * alpha);
            for (int i = 0; i < dots; i++) {
                int px = x + RND.nextInt(Math.max(1, w));
                int py = y + RND.nextInt(Math.max(1, h));
                int c = RND.nextBoolean() ? 0x30FFFFFF : 0x30000000;
                g.fill(px, py, px + 1, py + 1, c);
            }
        }
    }

    /** Бегущая светлая полоса, как при плохом трекинге. */
    public static void trackingBar(GuiGraphics g, int w, int h) {
        long now = System.currentTimeMillis();
        int y = (int) ((now / 9) % (h + 80)) - 40;
        g.fill(0, y, w, y + 3, 0x16FFFFFF);
        g.fill(0, y + 3, w, y + 14, 0x0CFFFFFF);
    }

    /** Нарисовать кусок текстуры с прозрачностью. */
    public static void blitAlpha(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                                 float u, float v, int uw, int vh, int texW, int texH, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
