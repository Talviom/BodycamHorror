package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.net.BannerPacket;
import com.talviom.bodycamhorror.net.BannerPacket.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Клиент: заставки раунда вместо ванильных титулов. Стиль «найденная плёнка»:
 * почти чёрный кадр, плёночное зерно, тёмные края, неровно дышащая яркость,
 * выцветший текст печатной машинкой с бледным двойником. Никаких неоновых цветов
 * и цветных сдвигов: только кость, пепел и запёкшаяся кровь.
 *
 * Пока игроки заморожены перед раундом, экран почти чёрный, на нём номер раунда, счёт,
 * своя команда и тихий обратный отсчёт. Иногда на долю секунды проступает едва заметная фраза.
 * На старте чернота медленно уходит. Итог раунда и матча тем же стилем поверх затемнения.
 */
public final class Banners {
    private static final ResourceLocation PLATE = Vhs.rl("textures/gui/banner_plate.png");
    private static final int PLATE_W = 256;
    private static final int PLATE_H = 64;

    /** Фразы, которые проступают на мгновение во время заморозки. */
    private static final String[] WHISPERS = {
            "DON'T LOOK BACK", "IT HEARS YOU", "STAY IN THE LIGHT", "YOU ARE NOT ALONE", "COUNT YOUR TEAM"
    };

    /** Строка запёкшейся кровью под счётом, своя на каждый раунд. */
    private static final String[] OMENS = {
            "STAY TOGETHER.", "DON'T LOOK BACK.", "COUNT YOUR FRIENDS.", "IT HEARD THAT.",
            "KEEP THE LIGHTS ON.", "SOMETHING CAME WITH YOU.", "DON'T WANDER OFF."
    };
    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};

    private static BannerPacket current;
    private static long shownAt;
    private static long durationMs;
    private static long whisperAt;
    private static long whisperUntil;
    private static String whisper = "";
    private static int whisperX;
    private static int whisperY;
    private static final Random RND = new Random();

    private Banners() {}

    public static void show(BannerPacket m) {
        current = m;
        shownAt = System.currentTimeMillis();
        durationMs = Math.max(500, m.ticks * 50L);
        whisperAt = shownAt + 2500 + RND.nextInt(3000);
        whisperUntil = 0;
    }

    /** Идёт ли сейчас тёмная заставка перед раундом (HUD под ней можно не рисовать). */
    public static boolean darkening() {
        return current != null && current.kind == Kind.ROUND_START
                && System.currentTimeMillis() - shownAt < durationMs + 1000;
    }

    static void draw(GuiGraphics g, int w, int h) {
        BannerPacket m = current;
        if (m == null) return;
        long now = System.currentTimeMillis();
        long t = now - shownAt;
        long fadeMs = m.kind == Kind.GO ? 1800 : 600;
        if (t > durationMs + fadeMs) {
            current = null;
            return;
        }
        float fadeOut = t > durationMs ? 1f - (t - durationMs) / (float) fadeMs : 1f;
        float fadeIn = Mth.clamp(t / 900f, 0f, 1f);

        // Затемнение кадра.
        float dark = switch (m.kind) {
            case ROUND_START -> 0.95f;
            case GO -> 0.95f * Math.max(0f, 1f - t / 1800f);
            case STOPPED -> 0.6f;
            default -> 0.55f;
        };
        dark *= fadeOut;
        if (dark > 0.01f) g.fill(0, 0, w, h, (int) (dark * 255) << 24);

        float fx = Mth.clamp(fadeOut, 0f, 1f);
        Vhs.noise(g, 0, 0, w, h, 0.16f * fx);
        Vhs.vignette(g, w, h, 0.9f * fx);
        Vhs.flicker(g, w, h);

        if (m.kind == Kind.GO) {
            // Без вспышек: чернота медленно уходит, внизу тихо проступает одно слово.
            float a = t < 400 ? t / 400f : Math.max(0f, 1f - (t - 400) / 1400f);
            int alpha = Mth.clamp((int) (200 * a), 4, 200) << 24;
            Vhs.ghostCentered(g, Vhs.title("STAY CLOSE"), w / 2, h - 60, alpha | Vhs.BONE, 1.5f, 0f);
            return;
        }

        int alpha = Mth.clamp((int) (235 * fx * fadeIn), 4, 235) << 24;
        int bone = alpha | Vhs.BONE;
        int ash = alpha | Vhs.ASH;
        int blood = alpha | Vhs.DRIED_BLOOD;

        drawOsd(g, w, h, now, fx);

        int cx = w / 2;
        int py = h / 2 - PLATE_H / 2 - 12;
        if (Vhs.has(PLATE)) {
            Vhs.blitAlpha(g, PLATE, (w - PLATE_W) / 2, py, PLATE_W, PLATE_H, 0, 0, PLATE_W, PLATE_H, PLATE_W, PLATE_H, 0.9f * fx * fadeIn);
        }

        String title;
        int titleColor = bone;
        switch (m.kind) {
            case ROUND_START -> title = "ROUND " + m.round;
            case ROUND_WON -> title = m.team.isEmpty() ? "ROUND OVER" : "ROUND WON";
            case ROUND_LOST -> { title = "ROUND LOST"; titleColor = blood; }
            case DRAW -> title = "NO ONE MADE IT";
            case MATCH_WON -> title = "YOU SURVIVED";
            case MATCH_LOST -> { title = "SIGNAL LOST"; titleColor = blood; }
            default -> title = "TAPE STOPPED";
        }
        // Заголовок чуть дрожит, и тем сильнее, чем хуже итог.
        float shake = m.kind == Kind.ROUND_LOST || m.kind == Kind.MATCH_LOST ? 1.2f : 0.4f;
        Vhs.ghostCentered(g, Vhs.title(title), cx, py + 14, titleColor, 2.5f, shake);

        if (m.kind != Kind.STOPPED) {
            String mine = m.team.isEmpty() ? "ALPHA" : m.team;
            String theirs = m.team.isEmpty() ? "BRAVO" : (m.team.equals("ALPHA") ? "BRAVO" : "ALPHA");
            Component score = Vhs.title(mine + "   " + m.scoreMine + "  -  " + m.scoreTheirs + "   " + theirs);
            Vhs.ghostCentered(g, score, cx, py + 46, ash, 1f, 0f);
        }

        if (m.kind == Kind.ROUND_START) {
            String omen = OMENS[Math.floorMod(m.round * 7 + 3, OMENS.length)];
            Vhs.ghostCentered(g, Vhs.title(omen), cx, py + 60, blood, 1f, 0.3f);
            int below = py + PLATE_H + 16;
            if (!m.team.isEmpty()) Vhs.ghostCentered(g, Vhs.title("YOU ARE " + m.team), cx, below, ash, 1f, 0f);
            if (m.swapped) Vhs.ghostCentered(g, Vhs.title("SIDES SWAPPED"), cx, below + 12, blood, 1f, 0f);
            long leftMs = Math.max(0, durationMs - t);
            int secs = (int) Math.ceil(leftMs / 1000.0);
            Vhs.ghostCentered(g, Vhs.title(String.valueOf(secs)), cx, h - 48, ash, 2f, 0.3f);
            drawWhisper(g, w, h, now, fx);
        }
    }

    /** Тусклая разметка камеры по углам: CAM 01, запись и дата, как на старой кассете. */
    private static void drawOsd(GuiGraphics g, int w, int h, long now, float fx) {
        int a = Mth.clamp((int) (170 * fx), 4, 170) << 24;
        int dim = a | Vhs.ASH;
        Vhs.plain(g, "CAM 01", 12, 12, dim);
        boolean blink = (now / 700) % 2 == 0;
        String rec = "REC";
        int rx = w - 12 - Vhs.width(rec);
        Vhs.plain(g, rec, rx, 12, dim);
        if (blink) Vhs.plain(g, "\u25CF", rx - Vhs.width("\u25CF "), 12, a | Vhs.DRIED_BLOOD);
        // Дата из прошлого и живые часы: запись будто сделана на старую камеру.
        java.time.LocalDateTime t = java.time.LocalDateTime.now();
        int hour = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        String date = MONTHS[t.getMonthValue() - 1] + " " + String.format("%02d", t.getDayOfMonth()) + " 1998";
        String time = String.format("%d:%02d:%02d %s", hour, t.getMinute(), t.getSecond(), t.getHour() < 12 ? "AM" : "PM");
        Vhs.plain(g, date, 12, h - 22, dim);
        Vhs.plain(g, time, w - 12 - Vhs.width(time), h - 22, dim);
    }

    /** Едва заметная фраза на долю секунды в случайном месте кадра. */
    private static void drawWhisper(GuiGraphics g, int w, int h, long now, float fx) {
        if (whisperUntil == 0 && now >= whisperAt) {
            whisper = WHISPERS[RND.nextInt(WHISPERS.length)];
            whisperX = w / 6 + RND.nextInt(Math.max(1, w * 2 / 3));
            whisperY = h / 6 + RND.nextInt(Math.max(1, h * 2 / 3));
            whisperUntil = now + 90 + RND.nextInt(70);
        }
        if (whisperUntil != 0 && now < whisperUntil) {
            int a = Mth.clamp((int) (38 * fx), 4, 38) << 24;
            Vhs.ghostCentered(g, Vhs.title(whisper), whisperX, whisperY, a | Vhs.BONE, 1f, 0f);
        }
    }
}
