package com.talviom.bodycamhorror.tenant;

import com.talviom.bodycamhorror.horror.FearSystem;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.sabotage.LampBlock;
import com.talviom.bodycamhorror.sabotage.SabotageData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Правила Жильца: зоны света, кто рядом, лимиты атак и утаскиваний. Числа из документа
 * «Монстр «Жилец»: механика и баланс» и решений пользователя от 2026-11-18.
 */
public final class TenantRules {
    /** Вокруг горящей лампы он не ходит. Свет фонарика зоной не считается. 0.9: 3 блока вместо 7. */
    public static final double LIGHT_ZONE = 3.0;
    public static final double ALLY_RANGE = 10.0;
    public static final float TAKE_FEAR = 60f;
    /** 0.9: отдых между нападениями 25 с вместо 40. */
    public static final int ATTACK_COOLDOWN_TICKS = 25 * 20;
    public static final int ATTACKS_PER_ROUND = 4;

    private record Count(int round, int attacks, long lastAttack, boolean taken) {}

    private static final Map<UUID, Count> COUNTS = new HashMap<>();

    private TenantRules() {}

    public static boolean litNear(ServerLevel level, Vec3 pos, double radius) {
        double r2 = radius * radius;
        for (BlockPos lamp : SabotageData.get(level).lamps) {
            if (Vec3.atCenterOf(lamp).distanceToSqr(pos) > r2) continue;
            BlockState s = level.getBlockState(lamp);
            if (s.getBlock() instanceof LampBlock && s.getValue(LampBlock.LIT)) return true;
        }
        return false;
    }

    /** Ближайшая тёмная точка карты (из точек лута и кассеты), вне зоны света. null, если тёмных нет. */
    @javax.annotation.Nullable
    public static BlockPos darkSpot(ServerLevel level, Vec3 from) {
        com.talviom.bodycamhorror.match.MapData map = com.talviom.bodycamhorror.match.MapData.get(level);
        java.util.List<BlockPos> pts = new java.util.ArrayList<>(map.lootPoints());
        pts.addAll(map.tapePoints());
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : pts) {
            if (litNear(level, Vec3.atCenterOf(p), LIGHT_ZONE)) continue;
            double d = Vec3.atBottomCenterOf(p).distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** 0.9: случайная тёмная точка карты в радиусе (для патруля). null, если таких нет. */
    @javax.annotation.Nullable
    public static BlockPos randomDarkSpot(ServerLevel level, Vec3 from, double range, net.minecraft.util.RandomSource rnd) {
        com.talviom.bodycamhorror.match.MapData map = com.talviom.bodycamhorror.match.MapData.get(level);
        java.util.List<BlockPos> all = new java.util.ArrayList<>(map.lootPoints());
        all.addAll(map.tapePoints());
        java.util.List<BlockPos> pts = new java.util.ArrayList<>();
        for (BlockPos p : all) {
            if (Vec3.atBottomCenterOf(p).distanceToSqr(from) > range * range) continue;
            if (litNear(level, Vec3.atCenterOf(p), LIGHT_ZONE)) continue;
            pts.add(p);
        }
        return pts.isEmpty() ? null : pts.get(rnd.nextInt(pts.size()));
    }

    /** Союзники: одна команда. Вне матча союзником считается любой другой игрок. */
    public static boolean sameSide(ServerPlayer a, ServerPlayer b) {
        MatchManager m = MatchManager.INSTANCE;
        BhTeam ta = m.teamOf(a);
        return ta == null || ta == m.teamOf(b);
    }

    /** Союзник ближе 10 блоков. */
    public static boolean allyNear(ServerPlayer p) {
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || o.isSpectator() || !o.isAlive() || !sameSide(p, o)) continue;
            if (o.distanceToSqr(p) <= ALLY_RANGE * ALLY_RANGE) return true;
        }
        return false;
    }

    /** Последний живой в своей команде (во время матча). */
    public static boolean lastAlive(ServerPlayer p) {
        MatchManager m = MatchManager.INSTANCE;
        BhTeam team = m.teamOf(p);
        if (!m.running() || team == null) return false;
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o != p && m.teamOf(o) == team && m.isAlive(o)) return false;
        }
        return true;
    }

    private static Count count(ServerPlayer p) {
        int round = MatchManager.INSTANCE.round();
        Count c = COUNTS.get(p.getUUID());
        if (c == null || c.round() != round) c = new Count(round, 0, -100000, false);
        COUNTS.put(p.getUUID(), c);
        return c;
    }

    /** Можно нападать: не чаще раза в 25 с, не больше 4 за раунд, не в перестрелке. */
    public static boolean canAttack(ServerPlayer p, long now) {
        Count c = count(p);
        return c.attacks() < ATTACKS_PER_ROUND && now - c.lastAttack() >= ATTACK_COOLDOWN_TICKS
                && !FearSystem.INSTANCE.inCombat(p);
    }

    public static void attacked(ServerPlayer p, long now) {
        Count c = count(p);
        COUNTS.put(p.getUUID(), new Count(c.round(), c.attacks() + 1, now, c.taken()));
    }

    /** Утащить можно одного, напуганного, ещё не утащенного в этом раунде и не последнего живого. */
    public static boolean canTake(ServerPlayer p) {
        return !allyNear(p) && FearSystem.INSTANCE.fear(p) >= TAKE_FEAR && !count(p).taken() && !lastAlive(p);
    }

    public static void taken(ServerPlayer p) {
        Count c = count(p);
        COUNTS.put(p.getUUID(), new Count(c.round(), c.attacks(), c.lastAttack(), true));
    }

    public static void clear() {
        COUNTS.clear();
    }
}
