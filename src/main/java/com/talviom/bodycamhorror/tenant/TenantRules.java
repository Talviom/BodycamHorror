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
 * Правила Жильца: зоны света, кто рядом, лимиты скримеров и утаскиваний. Числа из документа
 * «Монстр «Жилец»: механика и баланс».
 */
public final class TenantRules {
    /** Вокруг горящей лампы он не ходит. Свет фонарика зоной не считается. */
    public static final double LIGHT_ZONE = 7.0;
    public static final double ALLY_RANGE = 10.0;
    public static final float TAKE_FEAR = 60f;
    public static final int SCARE_COOLDOWN_TICKS = 40 * 20;
    public static final int SCARES_PER_ROUND = 4;

    private record Count(int round, int scares, long lastScare, boolean taken) {}

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

    /** Союзник ближе 10 блоков. Вне матча союзником считается любой другой живой игрок. */
    public static boolean allyNear(ServerPlayer p) {
        MatchManager m = MatchManager.INSTANCE;
        BhTeam team = m.teamOf(p);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || o.isSpectator() || !o.isAlive()) continue;
            if (team != null && m.teamOf(o) != team) continue;
            if (o.distanceToSqr(p) <= ALLY_RANGE * ALLY_RANGE) return true;
        }
        return false;
    }

    private static Count count(ServerPlayer p) {
        int round = MatchManager.INSTANCE.round();
        Count c = COUNTS.get(p.getUUID());
        if (c == null || c.round() != round) c = new Count(round, 0, -100000, false);
        COUNTS.put(p.getUUID(), c);
        return c;
    }

    public static boolean canScare(ServerPlayer p, long now) {
        Count c = count(p);
        return c.scares() < SCARES_PER_ROUND && now - c.lastScare() >= SCARE_COOLDOWN_TICKS;
    }

    public static void scared(ServerPlayer p, long now) {
        Count c = count(p);
        COUNTS.put(p.getUUID(), new Count(c.round(), c.scares() + 1, now, c.taken()));
    }

    /** Утащить можно одного, напуганного, ещё не утащенного в этом раунде. */
    public static boolean canTake(ServerPlayer p) {
        return !allyNear(p) && FearSystem.INSTANCE.fear(p) >= TAKE_FEAR && !count(p).taken();
    }

    public static void clear() {
        COUNTS.clear();
    }
}
