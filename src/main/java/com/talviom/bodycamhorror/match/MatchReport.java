package com.talviom.bodycamhorror.match;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.horror.FearSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервер (хост): короткий текстовый отчёт о каждом матче, чтобы после теста с друзьями разобрать баланс.
 * Кто в какой команде, чем кончился каждый раунд и за сколько, кто кого убил и чем, кто поднимал кассету,
 * кто щёлкал щитками и генератором, пик страха у каждого игрока.
 * Файл пишется в конце матча (и при /bh stop): папка игры/bodycamhorror/match_reports/match_ДАТА_ВРЕМЯ.txt.
 * Отчёт на английском, как и всё в игре.
 */
public final class MatchReport {
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final List<String> lines = new ArrayList<>();
    /** Имя игрока: убийства, смерти, подборы кассеты, переключения щитков и генератора. */
    private static final Map<String, int[]> stats = new LinkedHashMap<>();
    private static final Map<String, Float> peakFear = new LinkedHashMap<>();
    private static LocalDateTime startedAt;
    private static int roundStartTick;
    private static int matchStartTick;
    private static boolean active;
    private static boolean test;

    private MatchReport() {}

    public static void matchStart(MinecraftServer server, boolean testMatch) {
        lines.clear();
        stats.clear();
        peakFear.clear();
        startedAt = LocalDateTime.now();
        matchStartTick = server.getTickCount();
        active = true;
        test = testMatch;
        for (BhTeam t : BhTeam.values()) {
            List<String> names = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (MatchManager.INSTANCE.teamOf(p) == t) {
                    names.add(name(p));
                    stats.putIfAbsent(name(p), new int[4]);
                }
            }
            lines.add(t.displayName + ": " + (names.isEmpty() ? "-" : String.join(", ", names)));
        }
    }

    public static void roundStart(MinecraftServer server, int round) {
        if (!active) return;
        roundStartTick = server.getTickCount();
        lines.add("");
        lines.add("ROUND " + round);
    }

    /** Конец заморозки: время раунда в отчёте считается отсюда. */
    public static void live(MinecraftServer server) {
        if (active) roundStartTick = server.getTickCount();
    }

    public static void kill(MinecraftServer server, ServerPlayer victim, DamageSource src) {
        if (!active) return;
        Entity killer = src.getEntity();
        String by;
        if (killer instanceof ServerPlayer kp) {
            by = name(kp);
            if (kp != victim) stat(kp, 0);
            ItemStack held = kp.getMainHandItem();
            if (!held.isEmpty()) by += " with " + held.getHoverName().getString();
        } else if (killer instanceof LivingEntity le) {
            by = le.getType().getDescription().getString();
        } else {
            by = src.getMsgId();
        }
        stat(victim, 1);
        event(server, name(victim) + " killed by " + by);
    }

    public static void tapePickedUp(MinecraftServer server, ServerPlayer p) {
        if (!active) return;
        stat(p, 2);
        event(server, name(p) + " picked up the tape");
    }

    public static void switchUsed(MinecraftServer server, ServerPlayer p, String result) {
        if (!active || !(result.startsWith("Power") || result.startsWith("Generator"))) return;
        stat(p, 3);
        event(server, name(p) + ": " + result.toLowerCase());
    }

    /** Раз в секунду: пик страха у живых игроков. */
    public static void tick(MinecraftServer server) {
        if (!active || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!MatchManager.INSTANCE.isAlive(p)) continue;
            peakFear.merge(name(p), FearSystem.INSTANCE.fear(p), Math::max);
        }
    }

    public static void roundEnd(MinecraftServer server, BhTeam winner, String how, int alpha, int bravo) {
        if (!active) return;
        int sec = (server.getTickCount() - roundStartTick) / 20;
        lines.add(String.format("  %s  %s %s  Score %d:%d", clock(sec),
                winner == null ? "Draw." : winner.displayName + " won.", how, alpha, bravo));
    }

    /** Конец матча: пишем файл. */
    public static void matchEnd(MinecraftServer server, String result) {
        if (!active) return;
        active = false;
        List<String> out = new ArrayList<>();
        out.add("BODYCAM HORROR - MATCH REPORT" + (test ? " (TEST MATCH)" : ""));
        out.add(startedAt.format(HUMAN) + ", " + clock((server.getTickCount() - matchStartTick) / 20) + " long");
        out.add("Result: " + result);
        out.add("");
        out.addAll(lines);
        out.add("");
        out.add("PLAYERS (kills / deaths / tape pickups / switches / peak fear)");
        for (Map.Entry<String, int[]> e : stats.entrySet()) {
            int[] s = e.getValue();
            out.add(String.format("  %-16s %d / %d / %d / %d / %.0f", e.getKey(), s[0], s[1], s[2], s[3],
                    peakFear.getOrDefault(e.getKey(), 0f)));
        }
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("bodycamhorror").resolve("match_reports");
            Files.createDirectories(dir);
            Path file = dir.resolve("match_" + startedAt.format(FILE) + ".txt");
            Files.write(file, out, StandardCharsets.UTF_8);
            BodycamHorror.LOGGER.info("[BH report] match report saved to {}", file);
        } catch (Exception ex) {
            BodycamHorror.LOGGER.warn("[BH report] could not save the match report: {}", ex.toString());
        }
    }

    private static void event(MinecraftServer server, String text) {
        int sec = Math.max(0, (server.getTickCount() - roundStartTick) / 20);
        lines.add("  " + clock(sec) + "  " + text);
    }

    private static void stat(ServerPlayer p, int index) {
        stats.computeIfAbsent(name(p), k -> new int[4])[index]++;
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().getName();
    }

    private static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
