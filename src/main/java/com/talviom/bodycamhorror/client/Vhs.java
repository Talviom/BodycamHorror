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
 * Клиент: общий стиль «найденной плёнки». Шрифт камеры bodycamhorror:vhs для REC и таймкода,
 * шрифт печатной машинки bodycamhorror:horror для заголовков, плёночное зерно, тёмные края, мерцание.
 * Если какой-то текстуры или шрифта нет, рисуется простой запасной вариант, а игра не падает.
 */
public final class Vhs {
    public static final ResourceLocation FONT = rl("vhs");
    public static final ResourceLocation NOISE = rl("textures/gui/noise.png");
    /** Шрифт заголовков: потёртая печатная машинка (bodycamhorror:horror). */
    public static final ResourceLocation TITLE_FONT = rl("horror");
    public static final ResourceLocation VIGNETTE = rl("textures/gui/vignette.png");

    /** Палитра хоррора: выцветшая кость, пепел, запёкшаяся кровь. */
    public static final int BONE = 0xC4BCAA;
    public static final int ASH = 0x787266;
    public static final int SOOT = 0x4E4A42;
    public static final int DRIED_BLOOD = 0x7A1612;

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

    /** Строка из lang-файла мода (ru_ru, en_us) по ключу, с подстановкой %s. */
    public static String tr(String key, Object... args) {
        return net.minecraft.client.resources.language.I18n.get(key, args);
    }

    /** Список строк из lang-файла: одна строка, элементы через «|». */
    public static String[] trList(String key) {
        return tr(key).split("\\|");
    }

    /** Название команды по id из пакета (ALPHA, BRAVO) на языке игры, заглавными. */
    public static String teamName(String id) {
        return tr("bh.team." + id.toLowerCase(java.util.Locale.ROOT)).toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean hasFont() {
        return has(rl("font/vhs.json"));
    }

    /** Текст шрифтом VHS (или обычным, если шрифта нет). */
    public static Component text(String s) {
        Component c = Component.literal(s);
        return hasFont() ? c.copy().withStyle(st -> st.withFont(FONT)) : c;
    }

    /** Заголовок шрифтом печатной машинки (или VHS, если его нет). */
    public static Component title(String s) {
        if (has(rl("font/horror.json"))) return Component.literal(s).withStyle(st -> st.withFont(TITLE_FONT));
        return text(s);
    }

    public static int width(Component c) {
        return Minecraft.getInstance().font.width(c);
    }

    /**
     * Текст с бледным «двойником»: та же надпись того же цвета, прозрачная и чуть сдвинутая,
     * медленно плавает. Без цветных каналов: так выглядит старая затёртая плёнка, а не киберпанк.
     * @param shake сила дрожи в пикселях (0 = без дрожи)
     */
    public static void ghost(GuiGraphics g, Component c, int x, int y, int color, float scale, float shake) {
        Font font = Minecraft.getInstance().font;
        int a = color >>> 24;
        if (a == 0) a = 255;
        int rgb = color & 0xFFFFFF;
        long now = System.currentTimeMillis();
        float gx = (float) Math.sin(now / 900.0) * 1.2f + 1.0f;
        float gy = (float) Math.cos(now / 1300.0) * 0.6f;
        float sx = shake > 0 ? (RND.nextFloat() - 0.5f) * shake : 0f;
        float sy = shake > 0 ? (RND.nextFloat() - 0.5f) * shake : 0f;
        g.pose().pushPose();
        g.pose().translate(x + sx, y + sy, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, c, Math.round(gx), Math.round(gy), (Math.max(4, (int) (a * 0.22f)) << 24) | rgb, false);
        g.drawString(font, c, 0, 0, (Math.max(4, a) << 24) | rgb, false);
        g.pose().popPose();
    }

    public static void ghostCentered(GuiGraphics g, Component c, int cx, int y, int color, float scale, float shake) {
        int w = Math.round(width(c) * scale);
        ghost(g, c, cx - w / 2, y, color, scale, shake);
    }

    /** Тёмные края кадра: свет есть только в центре. */
    public static void vignette(GuiGraphics g, int w, int h, float strength) {
        if (strength <= 0f) return;
        if (has(VIGNETTE)) {
            blitAlpha(g, VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256, Math.min(1f, strength));
            return;
        }
        int a = Math.min(255, (int) (strength * 220));
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int ai = (int) (a * k * k);
            if (ai <= 0) continue;
            int color = ai << 24;
            int bx = i * (w / 5) / steps;
            int by = i * (h / 4) / steps;
            int bx2 = (i + 1) * (w / 5) / steps;
            int by2 = (i + 1) * (h / 4) / steps;
            g.fill(bx, 0, bx2, h, color);
            g.fill(w - bx2, 0, w - bx, h, color);
            g.fill(bx2, by, w - bx2, by2, color);
            g.fill(bx2, h - by2, w - bx2, h - by, color);
        }
    }

    private static long flickerUntil;
    private static int flickerAlpha;

    /** Неровное дыхание яркости, как у старой лампы: мелкие провалы и редкие глубокие. */
    public static void flicker(GuiGraphics g, int w, int h) {
        long now = System.currentTimeMillis();
        if (now > flickerUntil) {
            boolean deep = RND.nextInt(40) == 0;
            flickerAlpha = deep ? 90 + RND.nextInt(60) : RND.nextInt(22);
            flickerUntil = now + (deep ? 60 + RND.nextInt(60) : 70 + RND.nextInt(120));
        }
        if (flickerAlpha > 0) g.fill(0, 0, w, h, flickerAlpha << 24);
    }

    public static int width(String s) {
        return Minecraft.getInstance().font.width(text(s));
    }

    public static void plain(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text(s), x, y, color, false);
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
