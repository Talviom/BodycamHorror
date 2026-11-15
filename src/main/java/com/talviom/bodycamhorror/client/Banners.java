package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.net.BannerPacket;
import com.talviom.bodycamhorror.net.BannerPacket.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Клиент: заставки раунда в стиле VHS вместо ванильных титулов.
 * Пока игроки заморожены перед раундом, экран почти чёрный, на нём табличка с номером раунда,
 * счётом и обратным отсчётом. На старте чернота быстро уходит, мелькает «▶ PLAY».
 * Итог раунда и матча показывается такой же табличкой поверх слегка затемнённого экрана.
 */
public final class Banners {
    private static final ResourceLocation PLATE = Vhs.rl("textures/gui/banner_plate.png");
    private static final int PLATE_W = 256;
    private static final int PLATE_H = 64;

    private static BannerPacket current;
    private static long shownAt;
    private static long durationMs;
    private static final Random RND = new Random();

    private Banners() {}

    public static void show(BannerPacket m) {
        current = m;
        shownAt = System.currentTimeMillis();
        durationMs = Math.max(500, m.ticks * 50L);
    }

    /** Идёт ли сейчас тёмная заставка перед раундом (HUD под ней можно не рисовать). */
    public static boolean darkening() {
        return current != null && current.kind == Kind.ROUND_START
                && System.currentTimeMillis() - shownAt < durationMs + 1000;
    }

    static void draw(GuiGraphics g, int w, int h) {
        BannerPacket m = current;
        if (m == null) return;
        long t = System.currentTimeMillis() - shownAt;
        if (t > durationMs + 400) {
            current = null;
            return;
        }
        float fadeOut = t > durationMs ? 1f - (t - durationMs) / 400f : 1f;

        // Затемнение экрана.
        float dark = switch (m.kind) {
            case ROUND_START -> 0.9f;
            case GO -> 0.9f * Math.max(0f, 1f - t / 700f);
            case STOPPED -> 0.5f;
            default -> 0.45f;
        };
        dark *= fadeOut;
        if (dark > 0.01f) g.fill(0, 0, w, h, (int) (dark * 255) << 24);

        float fx = Mth.clamp(fadeOut, 0f, 1f);
        Vhs.noise(g, 0, 0, w, h, 0.10f * fx);
        Vhs.scanlines(g, 0, 0, w, h, 0.5f * fx);
        Vhs.trackingBar(g, w, h);

        int alpha = Mth.clamp((int) (255 * fx), 8, 255) << 24;
        int white = alpha | 0xF2F2F2;
        int grey = alpha | 0xA8A8A8;
        int red = alpha | 0xE53935;
        int green = alpha | 0x8BE08B;

        // Экранное меню камеры в левом верхнем углу.
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String osd = m.kind == Kind.ROUND_START ? "STANDBY" : m.kind == Kind.GO ? "▶ PLAY" : "■ STOP";
        if (m.kind != Kind.ROUND_START || blink) Vhs.chroma(g, osd, 12, 12, white, 1.5f);

        if (m.kind == Kind.GO) {
            // Короткое «PLAY» по центру, табличка не нужна.
            if (t < 1300) Vhs.chromaCentered(g, "▶ PLAY", w / 2, h / 2 - 12, white, 3f);
            return;
        }

        // Лёгкое дрожание кадра, как при плохом трекинге.
        int jitter = RND.nextInt(14) == 0 ? RND.nextInt(5) - 2 : 0;
        int px = (w - PLATE_W) / 2 + jitter;
        int py = h / 2 - PLATE_H / 2 - 10;
        if (Vhs.has(PLATE)) {
            Vhs.blitAlpha(g, PLATE, px, py, PLATE_W, PLATE_H, 0, 0, PLATE_W, PLATE_H, PLATE_W, PLATE_H, fx);
        } else {
            g.fill(px, py, px + PLATE_W, py + PLATE_H, (int) (0xC0 * fx) << 24 | 0x101010);
            g.fill(px, py, px + PLATE_W, py + 1, grey);
            g.fill(px, py + PLATE_H - 1, px + PLATE_W, py + PLATE_H, grey);
        }

        int cx = w / 2 + jitter;
        String title;
        int titleColor = white;
        switch (m.kind) {
            case ROUND_START -> title = "ROUND " + m.round;
            case ROUND_WON -> { title = m.team.isEmpty() ? "ROUND OVER" : "ROUND WON"; titleColor = green; }
            case ROUND_LOST -> { title = "ROUND LOST"; titleColor = red; }
            case DRAW -> title = "DRAW";
            case MATCH_WON -> { title = "VICTORY"; titleColor = green; }
            case MATCH_LOST -> { title = "DEFEAT"; titleColor = red; }
            default -> title = "MATCH STOPPED";
        }
        Vhs.chromaCentered(g, title, cx, py + 12, titleColor, 2.5f);

        if (m.kind != Kind.STOPPED) {
            String mine = m.team.isEmpty() ? "ALPHA" : m.team;
            String theirs = m.team.isEmpty() ? "BRAVO" : (m.team.equals("ALPHA") ? "BRAVO" : "ALPHA");
            String score = mine + "  " + m.scoreMine + " : " + m.scoreTheirs + "  " + theirs;
            Vhs.chromaCentered(g, score, cx, py + 42, grey, 1f);
        }

        if (m.kind == Kind.ROUND_START) {
            int below = py + PLATE_H + 10;
            if (!m.team.isEmpty()) Vhs.chromaCentered(g, "YOU ARE " + m.team, cx, below, grey, 1f);
            if (m.swapped) Vhs.chromaCentered(g, "SIDES SWAPPED", cx, below + 12, red, 1f);
            long leftMs = Math.max(0, durationMs - t);
            int secs = (int) Math.ceil(leftMs / 1000.0);
            Vhs.chromaCentered(g, "STARTS IN " + secs, cx, below + (m.swapped ? 30 : 18), white, 1.5f);
            // Счётчик плёнки внизу слева.
            String counter = String.format("SP  0:00:%02d", secs);
            Vhs.chroma(g, counter, 12, h - 22, white, 1.5f);
        }
    }
}
