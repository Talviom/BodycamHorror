package com.talviom.bodycamhorror.match;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.loot.LootSpawner;
import com.talviom.bodycamhorror.util.ItemSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.*;

/**
 * Серверная логика матча: команды, раунды, счёт, наблюдение после смерти.
 * Один матч на сервер, карта — обычный мир (overworld).
 */
public final class MatchManager {
    public static final MatchManager INSTANCE = new MatchManager();

    private final Map<UUID, BhTeam> teams = new HashMap<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, Vec3> frozenAt = new HashMap<>();
    private final EnumMap<BhTeam, Integer> score = new EnumMap<>(BhTeam.class);

    private MatchState state = MatchState.LOBBY;
    private int ticksLeft;
    private int round;

    private MatchManager() {
        resetScore();
    }

    public MatchState state() {
        return state;
    }

    public boolean running() {
        return state != MatchState.LOBBY;
    }

    public BhTeam teamOf(ServerPlayer p) {
        return teams.get(p.getUUID());
    }

    public int score(BhTeam t) {
        return score.get(t);
    }

    public int round() {
        return round;
    }

    public int secondsLeft() {
        return Math.max(0, ticksLeft / 20);
    }

    // ---------- команды ----------

    public void setTeam(MinecraftServer server, ServerPlayer p, BhTeam team) {
        teams.put(p.getUUID(), team);
        Scoreboard sb = server.getScoreboard();
        sb.addPlayerToTeam(p.getScoreboardName(), scoreboardTeam(sb, team));
    }

    public void removeFromTeam(MinecraftServer server, ServerPlayer p) {
        teams.remove(p.getUUID());
        alive.remove(p.getUUID());
        server.getScoreboard().removePlayerFromTeam(p.getScoreboardName());
    }

    private PlayerTeam scoreboardTeam(Scoreboard sb, BhTeam team) {
        PlayerTeam t = sb.getPlayerTeam(team.scoreboardName());
        if (t == null) t = sb.addPlayerTeam(team.scoreboardName());
        t.setColor(team.color);
        t.setAllowFriendlyFire(false);
        t.setSeeFriendlyInvisibles(true);
        t.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        t.setDeathMessageVisibility(Team.Visibility.NEVER);
        return t;
    }

    // ---------- жизненный цикл матча ----------

    /** @return текст ошибки или null, если матч запущен */
    public String start(MinecraftServer server) {
        if (running()) return "Матч уже идёт";
        ServerLevel level = server.overworld();
        MapData map = MapData.get(level);
        for (BhTeam t : BhTeam.values()) {
            if (map.spawns(t).isEmpty()) return "Нет точек спавна для команды " + t.displayName + " (/bh spawn add " + t.id + ")";
            if (onlineMembers(server, t).isEmpty()) return "В команде " + t.displayName + " нет игроков (/bh team " + t.id + " <игрок>)";
        }
        resetScore();
        round = 0;
        startRound(server);
        return null;
    }

    public void stop(MinecraftServer server) {
        if (!running()) return;
        state = MatchState.LOBBY;
        alive.clear();
        frozenAt.clear();
        for (ServerPlayer p : participants(server)) {
            p.setGameMode(GameType.ADVENTURE);
            p.setCamera(p);
        }
        broadcastTitle(server, Component.literal("Матч остановлен"), Component.empty());
    }

    private void startRound(MinecraftServer server) {
        round++;
        ServerLevel level = server.overworld();
        MapData map = MapData.get(level);

        LootSpawner.clearGroundItems(level);
        LootSpawner.spawn(level, map.lootPoints());

        // Смена сторон: каждые N раундов команды меняются точками спавна.
        int swapAfter = BhConfig.SWAP_SIDES_AFTER_ROUNDS.get();
        boolean swapped = swapAfter > 0 && ((round - 1) / swapAfter) % 2 == 1;

        alive.clear();
        frozenAt.clear();
        for (BhTeam t : BhTeam.values()) {
            List<ServerPlayer> members = onlineMembers(server, t);
            List<BlockPos> spawns = new ArrayList<>(map.spawns(swapped ? t.other() : t));
            Collections.shuffle(spawns);
            for (int i = 0; i < members.size(); i++) {
                ServerPlayer p = members.get(i);
                BlockPos s = spawns.get(i % spawns.size());
                resetPlayer(p);
                p.teleportTo(level, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), p.getXRot());
                alive.add(p.getUUID());
                frozenAt.put(p.getUUID(), p.position());
            }
        }

        state = MatchState.PREP;
        ticksLeft = BhConfig.PREP_SECONDS.get() * 20;
        broadcastTitle(server, Component.literal("Раунд " + round), scoreLine());
    }

    private void goLive(MinecraftServer server) {
        state = MatchState.LIVE;
        frozenAt.clear();
        ticksLeft = BhConfig.ROUND_SECONDS.get() * 20;
        broadcastTitle(server, Component.empty(), Component.literal("Вперёд"));
    }

    private void endRound(MinecraftServer server, BhTeam winner) {
        state = MatchState.ROUND_END;
        ticksLeft = BhConfig.ROUND_END_SECONDS.get() * 20;
        if (winner != null) score.merge(winner, 1, Integer::sum);

        Component title = winner == null
                ? Component.literal("Ничья")
                : Component.literal("Раунд за командой " + winner.displayName).withStyle(winner.color);
        broadcastTitle(server, title, scoreLine());

        if (winner != null && score.get(winner) >= BhConfig.ROUNDS_TO_WIN.get()) {
            broadcastTitle(server, Component.literal("Победа: " + winner.displayName).withStyle(winner.color), scoreLine());
            state = MatchState.LOBBY;
            for (ServerPlayer p : participants(server)) {
                p.setGameMode(GameType.ADVENTURE);
                p.setCamera(p);
            }
        }
    }

    // ---------- события ----------

    public void tick(MinecraftServer server) {
        if (!running()) return;
        ticksLeft--;

        switch (state) {
            case PREP -> {
                holdFrozenPlayers(server);
                if (ticksLeft % 20 == 0 && ticksLeft > 0) {
                    actionBarAll(server, Component.literal("Старт через " + secondsLeft()));
                }
                if (ticksLeft <= 0) goLive(server);
            }
            case LIVE -> {
                if (ticksLeft <= 0) endRound(server, timeoutWinner());
            }
            case ROUND_END -> {
                if (ticksLeft <= 0) startRound(server);
            }
            default -> {}
        }
    }

    /**
     * Вызывается вместо смерти игрока во время матча.
     * @return true, если смерть обработана матчем (ванильную смерть надо отменить)
     */
    public boolean onPlayerKilled(MinecraftServer server, ServerPlayer victim) {
        if (!running() || !alive.contains(victim.getUUID())) return false;
        alive.remove(victim.getUUID());

        // Снаряжение падает на землю: его можно подобрать.
        victim.getInventory().dropAll();
        victim.setHealth(victim.getMaxHealth());
        victim.setGameMode(GameType.SPECTATOR);
        spectateTeammate(server, victim);

        if (state == MatchState.LIVE) checkRoundOver(server);
        return true;
    }

    public void onPlayerLeft(MinecraftServer server, ServerPlayer p) {
        if (alive.remove(p.getUUID()) && state == MatchState.LIVE) checkRoundOver(server);
    }

    public void onPlayerJoined(ServerPlayer p) {
        if (running()) p.setGameMode(GameType.SPECTATOR);
    }

    public boolean damageBlocked() {
        return state == MatchState.PREP || state == MatchState.ROUND_END;
    }

    // ---------- вспомогательное ----------

    private void checkRoundOver(MinecraftServer server) {
        int a = aliveCount(server, BhTeam.ALPHA);
        int b = aliveCount(server, BhTeam.BRAVO);
        if (a == 0 && b == 0) endRound(server, null);
        else if (a == 0) endRound(server, BhTeam.BRAVO);
        else if (b == 0) endRound(server, BhTeam.ALPHA);
    }

    private BhTeam timeoutWinner() {
        // Пока нет цели на карте: по истечении времени побеждает команда, у которой больше живых.
        long a = alive.stream().filter(id -> teams.get(id) == BhTeam.ALPHA).count();
        long b = alive.stream().filter(id -> teams.get(id) == BhTeam.BRAVO).count();
        if (a == b) return null;
        return a > b ? BhTeam.ALPHA : BhTeam.BRAVO;
    }

    private int aliveCount(MinecraftServer server, BhTeam t) {
        int n = 0;
        for (UUID id : alive) {
            if (teams.get(id) == t && server.getPlayerList().getPlayer(id) != null) n++;
        }
        return n;
    }

    private void spectateTeammate(MinecraftServer server, ServerPlayer dead) {
        BhTeam team = teams.get(dead.getUUID());
        for (UUID id : alive) {
            if (teams.get(id) != team) continue;
            ServerPlayer mate = server.getPlayerList().getPlayer(id);
            if (mate != null) {
                dead.setCamera(mate);
                return;
            }
        }
    }

    private void holdFrozenPlayers(MinecraftServer server) {
        for (Map.Entry<UUID, Vec3> e : frozenAt.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            Vec3 at = e.getValue();
            if (p.position().distanceToSqr(at) > 0.04) {
                p.teleportTo(p.serverLevel(), at.x, at.y, at.z, p.getYRot(), p.getXRot());
            }
        }
    }

    private void resetPlayer(ServerPlayer p) {
        p.setGameMode(GameType.ADVENTURE);
        p.setCamera(p);
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        p.setRemainingFireTicks(0);
        p.fallDistance = 0;
        for (String spec : BhConfig.START_KIT.get()) {
            ItemStack stack = ItemSpec.parse(spec);
            if (!stack.isEmpty()) p.getInventory().add(stack);
        }
    }

    private List<ServerPlayer> onlineMembers(MinecraftServer server, BhTeam t) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (teams.get(p.getUUID()) == t) out.add(p);
        }
        return out;
    }

    private List<ServerPlayer> participants(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (teams.containsKey(p.getUUID())) out.add(p);
        }
        return out;
    }

    private void resetScore() {
        for (BhTeam t : BhTeam.values()) score.put(t, 0);
    }

    public Component scoreLine() {
        return Component.literal(BhTeam.ALPHA.displayName + " " + score.get(BhTeam.ALPHA)).withStyle(BhTeam.ALPHA.color)
                .append(Component.literal("  :  ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal(score.get(BhTeam.BRAVO) + " " + BhTeam.BRAVO.displayName).withStyle(BhTeam.BRAVO.color));
    }

    private void broadcastTitle(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private void actionBarAll(MinecraftServer server, Component msg) {
        for (ServerPlayer p : participants(server)) p.displayClientMessage(msg, true);
    }
}
