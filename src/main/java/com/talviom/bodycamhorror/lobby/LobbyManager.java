package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.objective.TapeObjective;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Лобби: комната, куда игрок попадает при входе в мир и куда всех возвращает после матча.
 * На площадках Alpha и Bravo игрок сам выбирает команду, хост запускает матч командой /bh start.
 * Если лобби на карте нет (/bh lobby build или /bh lobby set), ничего не происходит.
 */
public final class LobbyManager {
    public static final LobbyManager INSTANCE = new LobbyManager();

    /** Насколько далеко от точки лобби действует защита от урона. */
    public static final int SAFE_RADIUS = 24;
    private static final double PAD_RADIUS = 1.6;

    private int returnIn;
    private long tick;

    private LobbyManager() {}

    public static BlockPos lobby(MinecraftServer server) {
        return MapData.get(server.overworld()).lobby();
    }

    /** Через {@code ticks} тиков вернуть всех в лобби (после итогов матча). */
    public void scheduleReturn(int ticks) {
        returnIn = Math.max(1, ticks);
    }

    public void tick(MinecraftServer server) {
        tick++;
        MatchManager match = MatchManager.INSTANCE;
        if (match.running()) {
            returnIn = 0;
            return;
        }
        if (returnIn > 0 && --returnIn == 0) sendAllToLobby(server);
        if (tick % 10 == 0) checkPads(server);
    }

    public void onPlayerJoined(ServerPlayer p) {
        if (!MatchManager.INSTANCE.running()) sendToLobby(p);
    }

    public void sendAllToLobby(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendToLobby(p);
    }

    /**
     * Перенести игрока в лобби без снаряжения. Тех, кто строит в творческом режиме, не трогаем.
     * @return false, если лобби на карте нет
     */
    public boolean sendToLobby(ServerPlayer p) {
        ServerLevel level = p.server.overworld();
        BlockPos at = MapData.get(level).lobby();
        if (at == null) return false;
        if (p.isCreative()) return true;
        p.setGameMode(GameType.ADVENTURE);
        p.setCamera(p);
        TapeObjective.removeTapes(p);
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.setRemainingFireTicks(0);
        p.fallDistance = 0;
        Flashlight.resetPlayer(p);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        return true;
    }

    public static boolean inLobby(ServerPlayer p) {
        BlockPos at = MapData.get(p.server.overworld()).lobby();
        if (at == null || p.level() != p.server.overworld()) return false;
        return p.blockPosition().distSqr(at) <= (long) SAFE_RADIUS * SAFE_RADIUS;
    }

    /** Кто стоит на площадке команды, тот в этой команде. */
    private void checkPads(MinecraftServer server) {
        MapData map = MapData.get(server.overworld());
        if (map.lobby() == null) return;
        MatchManager match = MatchManager.INSTANCE;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.isCreative() || !inLobby(p)) continue;
            for (BhTeam t : BhTeam.values()) {
                BlockPos pad = map.lobbyPad(t);
                if (pad == null || !onPad(p, pad)) continue;
                if (match.teamOf(p) != t) {
                    match.setTeam(server, p, t);
                    p.displayClientMessage(TapeObjective.text("YOU ARE " + t.displayName.toUpperCase(), TapeObjective.BONE), true);
                }
            }
            if (tick % 40 == 0 && !onAnyPad(p, map)) {
                p.displayClientMessage(TapeObjective.text(status(server), TapeObjective.ASH), true);
            }
        }
    }

    private static boolean onPad(ServerPlayer p, BlockPos pad) {
        double dx = p.getX() - (pad.getX() + 0.5);
        double dz = p.getZ() - (pad.getZ() + 0.5);
        return dx * dx + dz * dz <= PAD_RADIUS * PAD_RADIUS && Math.abs(p.getY() - pad.getY()) < 2;
    }

    private static boolean onAnyPad(ServerPlayer p, MapData map) {
        for (BhTeam t : BhTeam.values()) {
            BlockPos pad = map.lobbyPad(t);
            if (pad != null && onPad(p, pad)) return true;
        }
        return false;
    }

    private static String status(MinecraftServer server) {
        int a = 0;
        int b = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BhTeam t = MatchManager.INSTANCE.teamOf(p);
            if (t == BhTeam.ALPHA) a++;
            else if (t == BhTeam.BRAVO) b++;
        }
        return "ALPHA " + a + "  ·  BRAVO " + b + "  ·  STEP ON A PAD. THE HOST STARTS WITH /BH START";
    }
}
