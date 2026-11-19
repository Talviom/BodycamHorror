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

    /** Строка отчёта на языке хозяина мира (на выделенном сервере английский). */
    private static String tr(String key, Object... args) {
        return net.minecraft.network.chat.Component.translatable(key, args).getString();
    }

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
            lines.add(t.title().getString() + ": " + (names.isEmpty() ? "-" : String.join(", ", names)));
        }
    }

    public static void roundStart(MinecraftServer server, int round) {
        if (!active) return;
        roundStartTick = server.getTickCount();
        lines.add("");
        lines.add(tr("bh.report.round", round));
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
            if (!held.isEmpty()) by = tr("bh.report.with", by, held.getHoverName().getString());
        } else if (killer instanceof LivingEntity le) {
            by = le.getType().getDescription().getString();
        } else {
            by = cause(src.getMsgId());
        }
        stat(victim, 1);
        event(server, by.startsWith("(") ? tr("bh.report.died", name(victim), by) : tr("bh.report.killed", name(victim), by));
    }

    /** Причина смерти без убийцы человеческими словами (в скобках: это не «убит кем-то»). */
    private static String cause(String msgId) {
        return switch (msgId) {
            case "genericKill", "outOfWorld" -> tr("bh.report.cause.command");
            case "fall" -> tr("bh.report.cause.fall");
            case "inFire", "onFire", "lava" -> tr("bh.report.cause.fire");
            case "drown" -> tr("bh.report.cause.drown");
            case "inWall" -> tr("bh.report.cause.wall");
            case "explosion", "explosion.player" -> tr("bh.report.cause.explosion");
            case "starve" -> tr("bh.report.cause.starve");
            case "magic", "indirectMagic" -> tr("bh.report.cause.magic");
            default -> "(" + msgId + ")";
        };
    }

    public static void tapePickedUp(MinecraftServer server, ServerPlayer p) {
        if (!active) return;
        stat(p, 2);
        event(server, tr("bh.report.tape", name(p)));
    }

    public static void switchUsed(MinecraftServer server, ServerPlayer p, net.minecraft.network.chat.Component result) {
        // В отчёт идут только настоящие переключения (bh.switch.power_* и generator_*), не «подожди».
        if (!active || !(result.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)
                || !(tc.getKey().startsWith("bh.switch.power") || tc.getKey().startsWith("bh.switch.generator"))) return;
        stat(p, 3);
        event(server, name(p) + ": " + result.getString().toLowerCase(java.util.Locale.ROOT));
    }

    /** Раз в секунду: пик страха у живых игроков. */
    public static void tick(MinecraftServer server) {
        if (!active || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!MatchManager.INSTANCE.isAlive(p)) continue;
            peakFear.merge(name(p), FearSystem.INSTANCE.fear(p), Math::max);
        }
    }

    public static void roundEnd(MinecraftServer server, BhTeam winner, net.minecraft.network.chat.Component how, int alpha, int bravo) {
        if (!active) return;
        int sec = (server.getTickCount() - roundStartTick) / 20;
        lines.add(tr("bh.report.round_line", clock(sec),
                winner == null ? tr("bh.report.draw") : tr("bh.report.round_won", winner.title().getString()),
                how.getString(), alpha, bravo));
    }

    /** Конец матча: пишем файл. */
    public static void matchEnd(MinecraftServer server, net.minecraft.network.chat.Component result) {
        if (!active) return;
        active = false;
        List<String> out = new ArrayList<>();
        out.add(tr("bh.report.title") + (test ? tr("bh.report.test") : ""));
        out.add(tr("bh.report.length", startedAt.format(HUMAN), clock((server.getTickCount() - matchStartTick) / 20)));
        out.add(tr("bh.report.result", result.getString()));
        out.add("");
        out.addAll(lines);
        out.add("");
        out.add(tr("bh.report.players"));
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
