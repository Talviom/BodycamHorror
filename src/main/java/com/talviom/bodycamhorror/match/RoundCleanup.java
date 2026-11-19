package com.talviom.bodycamhorror.match;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сервер: уборка карты в начале каждого раунда (вместе с LootSpawner.clearGroundItems).
 * Что остаётся после раунда и откуда оно:
 * - гильзы и выброшенные магазины: сущности TaCZ Tactical Breaching (гильзы лежат вечно, persistentSettledCasings);
 * - раны и кровь на телах: Better Blood Overlay держит раны игроков на сервере, отсюда кровь «течёт» и после смерти;
 * - тела: Ragdollified помнит тела мёртвых на сервере и заново рассылает их подошедшим игрокам.
 * Лужи, брызги, дырки от пуль и сами тела рисуются только у клиентов: их чистит client/RoundCleanupClient
 * по заставке начала раунда (BannerPacket ROUND_START).
 * Все чужие моды трогаем через реестр и рефлексию: если мода нет или он изменился, уборка просто пропускает его.
 */
public final class RoundCleanup {
    private static final Set<ResourceLocation> DEBRIS = Set.of(
            new ResourceLocation("tacz_tactical_breaching", "shell_casing"),
            new ResourceLocation("tacz_tactical_breaching", "dropped_magazine"));

    private RoundCleanup() {}

    public static void run(ServerLevel level) {
        int debris = removeDebris(level);
        int wounds = clearWounds(level);
        int bodies = forgetBodies();
        BodycamHorror.LOGGER.info("[BH cleanup] round start: removed {} casings/magazines, cleared wounds on {} players, forgot {} bodies",
                debris, wounds, bodies);
    }

    /** Гильзы и магазины на земле. */
    public static int removeDebris(ServerLevel level) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if (id != null && DEBRIS.contains(id)) out.add(e);
        }
        out.forEach(Entity::discard);
        return out.size();
    }

    /** Раны Better Blood Overlay на всех игроках (кровь с них больше не капает). */
    private static int clearWounds(ServerLevel level) {
        Method m = method("tailorworks.betterbloodoverlay_1_20_1.BloodHitHandler", "clearEntity", LivingEntity.class);
        if (m == null) return 0;
        int n = 0;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            try {
                m.invoke(null, p);
                n++;
            } catch (Exception ex) {
                warn("BloodHitHandler.clearEntity", ex);
                return n;
            }
        }
        return n;
    }

    /** Тела Ragdollified, которые сервер помнит и рассылает игрокам. */
    private static int forgetBodies() {
        String cls = "com.raiiiden.ragdollified.server.ServerRagdollSyncManager";
        Method remove = method(cls, "remove", int.class);
        if (remove == null) return 0;
        try {
            var f = Class.forName(cls).getDeclaredField("RETAINED");
            f.setAccessible(true);
            List<Integer> ids = new ArrayList<>(((Map<Integer, ?>) f.get(null)).keySet());
            for (int id : ids) remove.invoke(null, id);
            return ids.size();
        } catch (Exception ex) {
            warn("ServerRagdollSyncManager", ex);
            return 0;
        }
    }

    private static Method method(String cls, String name, Class<?>... args) {
        try {
            Method m = Class.forName(cls).getDeclaredMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (ClassNotFoundException ex) {
            return null; // мода нет в сборке
        } catch (Exception ex) {
            warn(cls + "." + name, ex);
            return null;
        }
    }

    private static void warn(String what, Exception ex) {
        BodycamHorror.LOGGER.warn("[BH cleanup] could not use {}: {}", what, ex.toString());
    }
}
