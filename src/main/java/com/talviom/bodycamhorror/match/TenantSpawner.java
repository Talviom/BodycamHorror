package com.talviom.bodycamhorror.match;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.tenant.ModEntities;
import com.talviom.bodycamhorror.tenant.TenantEntity;
import com.talviom.bodycamhorror.tenant.TenantRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Жилец в матче (выбор пользователя): один на раунд, просыпается через tenantWakeSeconds (30 с) после старта
 * в самом тёмном месте карты, подальше от обоих спавнов. Когда свет выключен (щиток, генератор), лампы его
 * не держат, поэтому без света он смелее (см. TenantRules.litNear). В конце раунда и матча исчезает
 * (только тот, кого поставил матч).
 */
public final class TenantSpawner {
    /** Не ближе этого к любому спавну (если на карте есть такие точки). */
    private static final double MIN_SPAWN_DIST = 14.0;

    private static long wakeAt = -1;
    private static boolean spawned;
    /** Только тех Жильцов, что поставил матч: поставленных командой /bh tenant spawn (проверки, съёмка) не трогаем. */
    private static final java.util.Set<java.util.UUID> OURS = new java.util.HashSet<>();

    private TenantSpawner() {}

    public static void onGoLive(MinecraftServer server) {
        removeAll(server);
        TenantRules.clear();
        spawned = false;
        int secs = BhConfig.TENANT_WAKE_SECONDS.get();
        wakeAt = secs > 0 ? server.getTickCount() + secs * 20L : -1;
    }

    public static void tick(MinecraftServer server) {
        if (spawned || wakeAt < 0 || server.getTickCount() < wakeAt) return;
        spawned = true;
        ServerLevel level = server.overworld();
        BlockPos at = darkestFarPoint(level);
        if (at == null) {
            BodycamHorror.LOGGER.warn("[BH tenant] no place to wake the Tenant (map has no loot or tape points)");
            return;
        }
        TenantEntity t = ModEntities.TENANT.get().create(level);
        if (t == null) return;
        t.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0);
        t.setHome(at);
        level.addFreshEntity(t);
        OURS.add(t.getUUID());
        BodycamHorror.LOGGER.info("[BH tenant] the Tenant woke at {} (block light {})", at.toShortString(),
                level.getBrightness(LightLayer.BLOCK, at));
    }

    public static void onRoundEnd(MinecraftServer server) {
        wakeAt = -1;
        spawned = false;
        removeAll(server);
    }

    public static void removeAll(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (TenantEntity t : new ArrayList<>(level.getEntities(ModEntities.TENANT.get(), e -> OURS.contains(e.getUUID())))) t.discard();
        OURS.clear();
    }

    /** Из точек лута и кассеты: подальше от спавнов, среди таких самая тёмная, при равенстве самая далёкая. */
    static BlockPos darkestFarPoint(ServerLevel level) {
        MapData map = MapData.get(level);
        List<BlockPos> candidates = new ArrayList<>(map.lootPoints());
        candidates.addAll(map.tapePoints());
        if (candidates.isEmpty()) return null;
        List<BlockPos> spawns = new ArrayList<>();
        for (BhTeam t : BhTeam.values()) spawns.addAll(map.spawns(t));

        List<BlockPos> far = new ArrayList<>();
        for (BlockPos c : candidates) if (minDist(c, spawns) >= MIN_SPAWN_DIST) far.add(c);
        if (far.isEmpty()) far = candidates;

        BlockPos best = null;
        int bestLight = Integer.MAX_VALUE;
        double bestDist = -1;
        for (BlockPos c : far) {
            int light = level.getBrightness(LightLayer.BLOCK, c);
            double d = minDist(c, spawns);
            if (light < bestLight || (light == bestLight && d > bestDist)) {
                best = c;
                bestLight = light;
                bestDist = d;
            }
        }
        return best;
    }

    private static double minDist(BlockPos p, List<BlockPos> spawns) {
        double m = Double.MAX_VALUE;
        for (BlockPos s : spawns) m = Math.min(m, Math.sqrt(p.distSqr(s)));
        return spawns.isEmpty() ? 0 : m;
    }
}
