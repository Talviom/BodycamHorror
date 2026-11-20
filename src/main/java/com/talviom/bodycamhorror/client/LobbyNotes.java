package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Клиент: записка с управлением справа посередине, пока матч не идёт (лобби, ожидание хоста).
 * Друзьям на тесте не нужно спрашивать клавиши: фонарик, снаряжение, щитки и генератор (F из TACZ),
 * перезарядка, настройки голосового чата, и в двух строках цель раунда.
 * Клавиши берутся из настроек управления, так что переназначенные показываются как есть.
 * Выключается настройкой hud.lobbyNotes.
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LobbyNotes {
    private static final int LINE = 11;

    private record Row(String fallback, String action, String... nameParts) {}

    private static final Row[] ROWS = {
            new Row(null, "bh.notes.flashlight", "key.bodycamhorror.flashlight"),
            new Row(null, "bh.notes.gear", "key.bodycamhorror.inventory"),
            new Row("F", "bh.notes.interact", "tacz", "interact"),
            new Row("R", "bh.notes.reload", "tacz", "reload"),
            new Row(null, "bh.notes.radio", "key.bodycamhorror.radio"),
            new Row(null, "bh.notes.voice", "key.voice_chat"),
    };

    private static KeyMapping[] found;
    private static long visibleSince;
    private static boolean logged;

    private LobbyNotes() {}

    @SubscribeEvent
    public static void onLobbyNotesRender(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldShow(mc)) {
            visibleSince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (visibleSince == 0) visibleSince = now;
        float in = Mth.clamp((now - visibleSince - 400) / 900f, 0f, 1f);
        if (in <= 0f) return;
        draw(e.getGuiGraphics(), mc, e.getWindow().getGuiScaledWidth(), e.getWindow().getGuiScaledHeight(), in);
    }

    private static boolean shouldShow(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui || mc.options.renderDebug) return false;
        if (ClientState.inMatch) return false;
        if (mc.gameMode != null) {
            GameType mode = mc.gameMode.getPlayerMode();
            if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) return false;
        }
        try {
            return BhConfig.LOBBY_NOTES.get();
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc, int w, int h, float in) {
        if (found == null) found = lookup(mc);
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < ROWS.length; i++) {
            String key = label(found[i], ROWS[i].fallback());
            if (key != null) rows.add(new String[]{key, Vhs.tr(ROWS[i].action())});
        }
        if (!logged) {
            logged = true;
            StringBuilder sb = new StringBuilder();
            for (String[] r : rows) sb.append(sb.length() == 0 ? "" : ", ").append(r[0]).append(' ').append(r[1]);
            BodycamHorror.LOGGER.info("[BH lobby] field notes shown: {}", sb);
        }

        int keyW = 0, actW = 0, goalW = 0;
        for (String[] r : rows) {
            keyW = Math.max(keyW, Vhs.width(Vhs.title("[" + r[0] + "]")));
            actW = Math.max(actW, Vhs.width(Vhs.title(r[1])));
        }
        String[] goal = Vhs.trList("bh.notes.goal");
        for (String s : goal) goalW = Math.max(goalW, Vhs.width(Vhs.title(s)));
        int blockW = Math.max(keyW + 6 + actW, goalW);
        int blockH = LINE * (rows.size() + goal.length) + 18;
        int x = w - 10 - blockW;
        int y = Math.max(46, h / 2 - blockH / 2 - 10);

        // Тусклая подложка, чтобы текст читался и на светлом полу лобби.
        g.fill(x - 6, y - 5, w - 4, y + blockH + 1, ((int) (0x70 * in)) << 24);
        Vhs.ghost(g, Vhs.title(Vhs.tr("bh.notes.title")), x, y, a(220, in) | Vhs.BONE, 1f, 0.15f);
        y += LINE + 5;
        for (String[] r : rows) {
            g.drawString(mc.font, Vhs.title("[" + r[0] + "]"), x, y, a(220, in) | Vhs.BONE, false);
            g.drawString(mc.font, Vhs.title(r[1]), x + keyW + 6, y, a(190, in) | Vhs.ASH, false);
            y += LINE;
        }
        y += 6;
        for (String s : goal) {
            g.drawString(mc.font, Vhs.title(s), x, y, a(170, in) | Vhs.ASH, false);
            y += LINE;
        }
    }

    /** Клавиши ищем один раз: по точному имени или по частям имени (у TACZ и голосового чата свои имена). */
    private static KeyMapping[] lookup(Minecraft mc) {
        KeyMapping[] out = new KeyMapping[ROWS.length];
        for (int i = 0; i < ROWS.length; i++) {
            String[] parts = ROWS[i].nameParts();
            for (KeyMapping k : mc.options.keyMappings) {
                String name = k.getName().toLowerCase(Locale.ROOT);
                boolean match = parts.length == 1 ? name.equals(parts[0]) : containsAll(name, parts);
                if (match) {
                    out[i] = k;
                    break;
                }
            }
        }
        return out;
    }

    /** Надпись клавиши по имени или частям имени (для других подсказок, например у щитка); null, если не назначена. */
    static String keyLabel(String fallback, String... parts) {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            String name = k.getName().toLowerCase(Locale.ROOT);
            if (parts.length == 1 ? name.equals(parts[0]) : containsAll(name, parts)) return label(k, fallback);
        }
        return fallback;
    }

    private static boolean containsAll(String name, String[] parts) {
        for (String p : parts) if (!name.contains(p)) return false;
        return true;
    }

    /** Надпись клавиши: из настроек управления, без неё запасная; неназначенную не показываем. */
    private static String label(KeyMapping k, String fallback) {
        if (k == null) return fallback;
        if (k.isUnbound()) return null;
        // Буквы и цифры берём по коду клавиши, а не по раскладке: на русской раскладке O стала бы «Щ»,
        // а в шрифте печатной машинки нет кириллицы (вместо буквы был пустой квадрат).
        com.mojang.blaze3d.platform.InputConstants.Key key = k.getKey();
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
            int v = key.getValue();
            if ((v >= 'A' && v <= 'Z') || (v >= '0' && v <= '9')) return String.valueOf((char) v);
        }
        Component c = k.getTranslatedKeyMessage();
        return c.getString().toUpperCase(Locale.ROOT);
    }

    /** Альфа для текста: не меньше 8, иначе шрифт Minecraft рисует почти прозрачный текст непрозрачным. */
    private static int a(int max, float alpha) {
        return Math.max(8, (int) (max * alpha)) << 24;
    }
}
