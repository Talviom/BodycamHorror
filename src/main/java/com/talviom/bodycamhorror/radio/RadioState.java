package com.talviom.bodycamhorror.radio;

import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MatchManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Снимок состояния матча для рации. Голосовой чат работает в своём потоке,
 * поэтому он читает только этот снимок, а не игровые объекты напрямую.
 * Класс не зависит от Simple Voice Chat и безопасен, даже если тот не установлен.
 */
public final class RadioState {
    public record Entry(BhTeam team, Vec3 pos) {}

    private static volatile Map<UUID, Entry> alive = Map.of();
    private static final Map<UUID, Long> JAM_UNTIL_MS = new java.util.concurrent.ConcurrentHashMap<>();

    private RadioState() {}

    /** Вызывается из серверного потока раз в несколько тиков. */
    public static void update(MinecraftServer server, MatchManager match) {
        if (!match.running()) {
            if (!alive.isEmpty()) alive = Map.of();
            return;
        }
        Map<UUID, Entry> next = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BhTeam team = match.teamOf(p);
            if (team != null && !p.isSpectator() && p.isAlive()) next.put(p.getUUID(), new Entry(team, p.position()));
        }
        alive = next;
    }

    public static Map<UUID, Entry> alive() {
        return alive;
    }

    /**
     * Помехи в рации одного игрока на заданное время: его плохо слышно, и он плохо слышит.
     * Остальная команда между собой говорит как обычно, чужой страх им не мешает.
     */
    public static void jam(UUID player, int ticks) {
        long now = System.currentTimeMillis();
        JAM_UNTIL_MS.values().removeIf(until -> until < now);
        JAM_UNTIL_MS.put(player, now + ticks * 50L);
    }

    public static boolean isJammed(UUID player) {
        Long until = JAM_UNTIL_MS.get(player);
        return until != null && System.currentTimeMillis() < until;
    }
}
