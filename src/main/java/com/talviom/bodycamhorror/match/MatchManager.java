package com.talviom.bodycamhorror.match;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.horror.FearSystem;
import com.talviom.bodycamhorror.lobby.LobbyManager;
import com.talviom.bodycamhorror.objective.TapeObjective;
import com.talviom.bodycamhorror.loot.LootSpawner;
import com.talviom.bodycamhorror.net.BannerPacket;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.util.ItemSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

    /** Метка в данных игрока: наблюдателем его сделал матч, а не администратор. */
    private static final String TAG_SPECTATOR = "bh_match_spectator";

    private MatchState state = MatchState.LOBBY;
    private boolean testMode;
    private int ticksLeft;
    private int round;
    private boolean swapped;

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

    /** Игрок участвует в текущем раунде и ещё жив. */
    public boolean isAlive(ServerPlayer p) {
        return alive.contains(p.getUUID());
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

    /** Сторона карты, на которой команда сейчас спавнится и куда выносит кассету (стороны меняются). */
    public BhTeam sideOf(BhTeam team) {
        return swapped ? team.other() : team;
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
        return start(server, false);
    }

    /**
     * @param test тестовый режим: матч можно запустить одному, пустая команда не мешает старту.
     *             Раунд тогда заканчивается по таймеру, по смерти или командой /bh skip.
     * @return текст ошибки или null, если матч запущен
     */
    public String start(MinecraftServer server, boolean test) {
        if (running()) return "A match is already running";
        ServerLevel level = server.overworld();
        MapData map = MapData.get(level);
        int players = 0;
        for (BhTeam t : BhTeam.values()) {
            if (map.spawns(t).isEmpty()) return "No spawn points for " + t.displayName + " (/bh spawn add " + t.id + ")";
            int n = onlineMembers(server, t).size();
            if (n == 0 && !test) return t.displayName + " has no players (/bh team " + t.id + " <player>). To test alone: /bh start test";
            players += n;
        }
        if (players == 0) return "No players in any team (/bh team alpha <player>)";
        testMode = test;
        resetScore();
        round = 0;
        MatchReport.matchStart(server, test);
        startRound(server);
        return null;
    }

    public void stop(MinecraftServer server) {
        if (!running()) return;
        MatchReport.matchEnd(server, "Stopped (/bh stop) at " + score(BhTeam.ALPHA) + ":" + score(BhTeam.BRAVO));
        state = MatchState.LOBBY;
        alive.clear();
        frozenAt.clear();
        TapeObjective.INSTANCE.onMatchEnd(server);
        releaseAll(server);
        broadcastBanner(server, BannerPacket.Kind.STOPPED, 60);
        LobbyManager.INSTANCE.scheduleReturn(60);
    }

    private void startRound(MinecraftServer server) {
        round++;
        MatchReport.roundStart(server, round);
        ServerLevel level = server.overworld();
        MapData map = MapData.get(level);

        LootSpawner.clearGroundItems(level);
        if (map.pairedLoot()) LootSpawner.spawnPaired(level, map.lootPoints());
        else LootSpawner.spawn(level, map.lootPoints());

        // Смена сторон: каждые N раундов команды меняются точками спавна.
        int swapAfter = BhConfig.SWAP_SIDES_AFTER_ROUNDS.get();
        boolean wasSwapped = swapped;
        swapped = swapAfter > 0 && ((round - 1) / swapAfter) % 2 == 1;
        boolean justSwapped = swapped != wasSwapped && round > 1;

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

        // Кассета появляется в случайной точке (если на карте есть выходы и точки кассеты).
        TapeObjective.INSTANCE.onRoundStart(server);

        state = MatchState.PREP;
        ticksLeft = BhConfig.PREP_SECONDS.get() * 20;
        // Экран затемнён, пока игроки заморожены: заставка VHS с номером раунда и счётом.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BhTeam t = teams.get(p.getUUID());
            BhNetwork.sendBanner(p, banner(BannerPacket.Kind.ROUND_START, t, justSwapped, ticksLeft));
        }
    }

    private void goLive(MinecraftServer server) {
        state = MatchState.LIVE;
        frozenAt.clear();
        ticksLeft = BhConfig.ROUND_SECONDS.get() * 20;
        broadcastBanner(server, BannerPacket.Kind.GO, 30);
        MatchReport.live(server);
        TapeObjective.INSTANCE.announce(server, round == 1);
        // За время заморозки команда могла остаться без игроков (все вышли).
        if (!testMode) checkRoundOver(server);
    }

    private void endRound(MinecraftServer server, BhTeam winner) {
        endRound(server, winner, null);
    }

    /** @param reason строка в чат всем игрокам, чем кончился раунд (null = без пояснения) */
    private void endRound(MinecraftServer server, BhTeam winner, String reason) {
        // Для отчёта о матче: чем кончился раунд (до того, как ticksLeft перезапишется).
        String how = reason != null ? reason : ticksLeft <= 0 ? "Time ran out."
                : winner == null ? "Both teams fell." : winner.other().displayName + " was wiped out.";
        state = MatchState.ROUND_END;
        TapeObjective.INSTANCE.onRoundEnd();
        ticksLeft = BhConfig.ROUND_END_SECONDS.get() * 20;
        if (winner != null) score.merge(winner, 1, Integer::sum);
        MatchReport.roundEnd(server, winner, how, score(BhTeam.ALPHA), score(BhTeam.BRAVO));

        boolean matchOver = winner != null && score.get(winner) >= BhConfig.ROUNDS_TO_WIN.get();
        int showTicks = matchOver ? 120 : ticksLeft;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BhTeam t = teams.get(p.getUUID());
            BannerPacket.Kind kind;
            if (winner == null) kind = BannerPacket.Kind.DRAW;
            else if (matchOver) kind = t == winner ? BannerPacket.Kind.MATCH_WON : BannerPacket.Kind.MATCH_LOST;
            else kind = t == winner ? BannerPacket.Kind.ROUND_WON : BannerPacket.Kind.ROUND_LOST;
            // Зритель без команды видит итог глазами победителя.
            if (t == null && winner != null) {
                kind = matchOver ? BannerPacket.Kind.MATCH_WON : BannerPacket.Kind.ROUND_WON;
                t = winner;
            }
            BhNetwork.sendBanner(p, banner(kind, t, false, showTicks));
        }

        if (reason != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(TapeObjective.text(reason, TapeObjective.BONE));
            }
        }

        if (matchOver) {
            MatchReport.matchEnd(server, winner.displayName + " won " + score(BhTeam.ALPHA) + ":" + score(BhTeam.BRAVO));
            state = MatchState.LOBBY;
            TapeObjective.INSTANCE.onMatchEnd(server);
            releaseAll(server);
            // Итоги матча видны несколько секунд, потом все возвращаются в лобби.
            LobbyManager.INSTANCE.scheduleReturn(BhConfig.LOBBY_RETURN_SECONDS.get() * 20);
        }
    }

    // ---------- события ----------

    public void tick(MinecraftServer server) {
        if (!running()) return;
        ticksLeft--;
        if (ticksLeft % 10 == 0) holdSpectators(server);

        switch (state) {
            case PREP -> {
                holdFrozenPlayers(server);
                if (ticksLeft <= 0) goLive(server);
            }
            case LIVE -> {
                BhTeam out = TapeObjective.INSTANCE.tick(server);
                if (out != null) endRound(server, out, out.displayName + " got the tape out.");
                else if (ticksLeft <= 0) endRound(server, timeoutWinner(), TapeObjective.INSTANCE.active() ? "No one got the tape out." : null);
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
        FearSystem.INSTANCE.onPlayerDied(server, victim);

        // Снаряжение падает на землю: его можно подобрать.
        victim.getInventory().dropAll();
        victim.setHealth(victim.getMaxHealth());
        makeSpectator(victim);
        spectateTeammate(server, victim);

        if (state == MatchState.LIVE) checkRoundOver(server);
        return true;
    }

    public void onPlayerLeft(MinecraftServer server, ServerPlayer p) {
        TapeObjective.INSTANCE.onPlayerLeft(p);
        frozenAt.remove(p.getUUID());
        if (alive.remove(p.getUUID()) && state == MatchState.LIVE) checkRoundOver(server);
    }

    /**
     * Игрок зашёл на сервер.
     * Матча нет: если прошлый матч оставил его наблюдателем, возвращаем обычный режим.
     * Идёт заморозка и игрок в команде: он успел, ставим его в раунд.
     * Иначе до следующего раунда он наблюдатель (в команде смотрит глазами союзника).
     */
    public void onPlayerJoined(MinecraftServer server, ServerPlayer p) {
        if (!running()) {
            if (p.getPersistentData().getBoolean(TAG_SPECTATOR)) release(p);
            LobbyManager.INSTANCE.onPlayerJoined(p);
            return;
        }
        BhTeam team = teams.get(p.getUUID());
        if (team != null && state == MatchState.PREP) {
            ServerLevel level = server.overworld();
            List<BlockPos> spawns = MapData.get(level).spawns(swapped ? team.other() : team);
            if (!spawns.isEmpty()) {
                BlockPos s = spawns.get(new Random().nextInt(spawns.size()));
                resetPlayer(p);
                p.getPersistentData().remove(TAG_SPECTATOR);
                p.teleportTo(level, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), p.getXRot());
                alive.add(p.getUUID());
                frozenAt.put(p.getUUID(), p.position());
                BhNetwork.sendBanner(p, banner(BannerPacket.Kind.ROUND_START, team, false, Math.max(1, ticksLeft)));
                return;
            }
        }
        makeSpectator(p);
        if (team != null) spectateTeammate(server, p);
    }

    /** Перемотать текущую фазу: заморозка → раунд → пауза → следующий раунд. Для проверки. */
    public String skip(MinecraftServer server) {
        if (!running()) return "No match is running";
        MatchState before = state;
        ticksLeft = 1;
        tick(server);
        return "Skipped: " + before + " -> " + state;
    }

    public boolean testMode() {
        return testMode;
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

    /** Матч делает игрока наблюдателем и помечает его, чтобы потом вернуть обычный режим даже после перезахода. */
    private void makeSpectator(ServerPlayer p) {
        p.setGameMode(GameType.SPECTATOR);
        p.getPersistentData().putBoolean(TAG_SPECTATOR, true);
    }

    private void release(ServerPlayer p) {
        p.setGameMode(GameType.ADVENTURE);
        p.setCamera(p);
        p.getPersistentData().remove(TAG_SPECTATOR);
    }

    /** Конец матча: участники и все, кого матч сделал наблюдателем, снова обычные игроки. */
    private void releaseAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (teams.containsKey(p.getUUID()) || p.getPersistentData().getBoolean(TAG_SPECTATOR)) release(p);
        }
    }

    /**
     * Погибший смотрит только глазами живого союзника: свободно летать по карте и подсказывать нельзя.
     * Если союзник, за которым он смотрел, тоже погиб или вышел, камера переходит к следующему.
     */
    private void holdSpectators(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BhTeam team = teams.get(p.getUUID());
            if (team == null || alive.contains(p.getUUID()) || !p.isSpectator()) continue;
            if (p.getCamera() instanceof ServerPlayer mate && mate != p && alive.contains(mate.getUUID())
                    && teams.get(mate.getUUID()) == team && !mate.hasDisconnected()) continue;
            p.setCamera(p);
            spectateTeammate(server, p);
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
        Flashlight.resetPlayer(p);
        BhTeam team = teams.get(p.getUUID());
        for (String spec : (team == BhTeam.BRAVO ? BhConfig.START_KIT_BRAVO : BhConfig.START_KIT).get()) {
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

    /** Заставка с точки зрения команды {@code team} (null = зритель). */
    private BannerPacket banner(BannerPacket.Kind kind, BhTeam team, boolean justSwapped, int ticks) {
        BhTeam me = team == null ? BhTeam.ALPHA : team;
        return new BannerPacket(kind, round, score.get(me), score.get(me.other()),
                team == null ? "" : team.displayName.toUpperCase(Locale.ROOT), justSwapped, ticks);
    }

    private void broadcastBanner(MinecraftServer server, BannerPacket.Kind kind, int ticks) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BhNetwork.sendBanner(p, banner(kind, teams.get(p.getUUID()), false, ticks));
        }
    }
}
