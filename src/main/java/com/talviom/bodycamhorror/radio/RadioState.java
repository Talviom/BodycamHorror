package com.talviom.bodycamhorror.radio;

import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MatchManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
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
    private static final Map<BhTeam, Long> JAM_UNTIL_MS = new EnumMap<>(BhTeam.class);

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

    /** Помехи в рации команды на заданное время. */
    public static void jam(BhTeam team, int ticks) {
        synchronized (JAM_UNTIL_MS) {
            JAM_UNTIL_MS.put(team, System.currentTimeMillis() + ticks * 50L);
        }
    }

    public static boolean isJammed(BhTeam team) {
        synchronized (JAM_UNTIL_MS) {
            Long until = JAM_UNTIL_MS.get(team);
            return until != null && System.currentTimeMillis() < until;
        }
    }
}
