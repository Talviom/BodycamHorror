package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.time.LocalDateTime;

/** Клиент: служебные надписи камеры по углам экранов меню, как на старой кассете (CAM, REC, дата, время). */
public final class MenuOsd {
    private MenuOsd() {}

    /**
     * @param alpha прозрачность 0..1
     * @param tape  вторая строка под CAM 01 (например TAPE 07), или null
     * @return ширина самой длинной нижней надписи (чтобы раскладка внизу их не задевала)
     */
    public static int draw(GuiGraphics g, int width, int height, float alpha, String tape) {
        if (alpha <= 0.01f) return 0;
        int dim = a(170, alpha) | Vhs.ASH;
        Vhs.plain(g, Vhs.tr("bh.osd.cam"), 12, 12, dim);
        if (tape != null) Vhs.plain(g, tape, 12, 23, a(120, alpha) | Vhs.SOOT);
        String rec = "REC";
        int rx = width - 12 - Vhs.width(rec);
        Vhs.plain(g, rec, rx, 12, a(200, alpha) | Vhs.BONE);
        if ((System.currentTimeMillis() / 700) % 2 == 0) {
            Vhs.plain(g, "●", rx - Vhs.width("● "), 12, a(230, alpha) | Vhs.DRIED_BLOOD);
        }
        String date = date();
        String time = time();
        Vhs.plain(g, date, 12, height - 22, dim);
        Vhs.plain(g, time, width - 12 - Vhs.width(time), height - 22, dim);
        return Math.max(Vhs.width(date), Vhs.width(time));
    }

    /** Дата из прошлого: сегодняшние месяц и число, но 1998 год. */
    public static String date() {
        LocalDateTime t = LocalDateTime.now();
        String[] months = Vhs.trList("bh.osd.months");
        String month = months.length == 12 ? months[t.getMonthValue() - 1] : String.valueOf(t.getMonthValue());
        return Vhs.tr("bh.osd.date", month, String.format("%02d", t.getDayOfMonth()));
    }

    public static String time() {
        LocalDateTime t = LocalDateTime.now();
        // На русской камере часы 24-часовые, на английской с AM и PM.
        if ("true".equals(Vhs.tr("bh.osd.clock24"))) {
            return String.format("%d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
        }
        int hour = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return String.format("%d:%02d:%02d %s", hour, t.getMinute(), t.getSecond(), t.getHour() < 12 ? "AM" : "PM");
    }

    static int a(int max, float alpha) {
        return Mth.clamp((int) (max * alpha), 4, 255) << 24;
    }
}
