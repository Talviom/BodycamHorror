package com.talviom.bodycamhorror.tenant;

import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.horror.FearSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Что слышит Жилец. Каждый сигнал оставляет след с весом и радиусом слышимости; след стирается за 15 с.
 * Жилец идёт к самому тяжёлому свежему следу, который он слышит со своего места.
 *
 *   фонарик горит      вес 3, 24 блока, только если Жилец видит игрока
 *   бег                вес 2, 18 блоков (0.9, было 12)
 *   шаги (не крадучись) вес 1, 10 блоков (0.9, было 6)
 *   страх выше 70      вес 1, 5 блоков (сердце)
 *   щиток, генератор   вес 4, вся карта
 */
public final class TenantHearing {
    public static final int FORGET_TICKS = 15 * 20;

    public record Noise(ServerLevel level, Vec3 pos, float weight, double range, long time,
                        @Nullable UUID source, boolean needsSight, String kind) {}

    private static final List<Noise> NOISES = new ArrayList<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

    private TenantHearing() {}

    public static void noise(ServerLevel level, Vec3 pos, float weight, double range, @Nullable ServerPlayer source,
                             boolean needsSight, String kind) {
        NOISES.add(new Noise(level, pos, weight, range, level.getGameTime(), source == null ? null : source.getUUID(), needsSight, kind));
    }

    /** Щиток или генератор переключили: слышно на всей карте. */
    public static void breaker(ServerLevel level, BlockPos pos) {
        noise(level, Vec3.atCenterOf(pos), 4f, 1000, null, false, "breaker");
    }

    public static void clear() {
        NOISES.clear();
        LAST_POS.clear();
    }

    /** Раз в 10 тиков: следы от игроков и стирание старых. */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        NOISES.removeIf(n -> now - n.time() > FORGET_TICKS);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !p.isAlive() || p.isCreative() || p.level() != level) continue;
            Vec3 pos = p.position();
            Vec3 last = LAST_POS.put(p.getUUID(), pos);
            double moved = last == null ? 0 : Math.sqrt(Math.pow(pos.x - last.x, 2) + Math.pow(pos.z - last.z, 2));
            if (Flashlight.isLit(p)) noise(level, pos, 3f, 24, p, true, "flashlight");
            if (p.isSprinting()) noise(level, pos, 2f, 18, p, false, "run");
            else if (moved > 0.6 && !p.isCrouching()) noise(level, pos, 1f, 10, p, false, "steps");
            if (FearSystem.INSTANCE.fear(p) >= 70f) noise(level, pos, 1f, 5, p, false, "heartbeat");
        }
    }

    /** Самый тяжёлый свежий след, который Жилец слышит со своего места. */
    @Nullable
    public static Noise best(TenantEntity tenant, long now) {
        Noise best = null;
        double bestScore = 0;
        for (Iterator<Noise> it = NOISES.iterator(); it.hasNext(); ) {
            Noise n = it.next();
            if (n.level() != tenant.level()) continue;
            double age = now - n.time();
            if (age > FORGET_TICKS) continue;
            if (n.pos().distanceToSqr(tenant.position()) > n.range() * n.range()) continue;
            if (TenantRules.litNear(n.level(), n.pos(), TenantRules.LIGHT_ZONE)) continue;
            if (n.needsSight() && n.source() != null) {
                ServerPlayer p = n.level().getServer().getPlayerList().getPlayer(n.source());
                if (p == null || !tenant.hasLineOfSight(p)) continue;
            }
            double score = n.weight() * (1.0 - age / FORGET_TICKS);
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    public static int count() {
        return NOISES.size();
    }
}
