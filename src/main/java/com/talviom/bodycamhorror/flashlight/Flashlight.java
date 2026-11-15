package com.talviom.bodycamhorror.flashlight;

import com.talviom.bodycamhorror.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Свет фонарика. Сервер ставит невидимый блок света туда, куда смотрит игрок,
 * и убирает его со старого места. Такой свет видят все игроки, и он работает с шейдерами.
 * Включённый фонарик выдаёт владельца: пятно света видно издалека.
 */
public final class Flashlight {
    private static final double RANGE = 14.0;
    private static final int LIGHT_LEVEL = 13;

    private record Spot(ServerLevel level, BlockPos pos) {}

    private static final Set<UUID> ON = new HashSet<>();
    private static final Map<UUID, Spot> SPOTS = new HashMap<>();
    private static final Map<UUID, Long> BLACKOUT_UNTIL = new HashMap<>();
    private static long tick;

    private Flashlight() {}

    public static boolean hasFlashlight(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModBlocks.FLASHLIGHT.get())) return true;
        }
        return false;
    }

    public static void toggle(ServerPlayer p) {
        if (!hasFlashlight(p)) {
            p.displayClientMessage(Component.literal("Нет фонарика"), true);
            return;
        }
        UUID id = p.getUUID();
        boolean on = !ON.remove(id);
        if (on) ON.add(id);
        SoundEvent click = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("minecraft:block.lever.click"));
        if (click != null) p.serverLevel().playSound(null, p.blockPosition(), click, SoundSource.PLAYERS, 0.35f, on ? 1.6f : 1.3f);
        p.displayClientMessage(Component.literal(on ? "Фонарик включён" : "Фонарик выключен"), true);
    }

    /** Фонарик игрока гаснет на заданное время (событие хоррор-режиссёра). */
    public static void blackout(ServerPlayer p, int ticks) {
        BLACKOUT_UNTIL.put(p.getUUID(), tick + ticks);
    }

    public static void tick(MinecraftServer server) {
        tick++;
        if (tick % 2 != 0) return;

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            online.add(id);
            Spot wanted = wantedSpot(p);
            Spot current = SPOTS.get(id);
            if (same(current, wanted)) continue;
            if (wanted != null) {
                place(wanted);
                SPOTS.put(id, wanted);
            } else {
                SPOTS.remove(id);
            }
            if (current != null) removeIfUnused(current);
        }

        // Игроки, которые вышли: убрать их свет.
        for (Iterator<Map.Entry<UUID, Spot>> it = SPOTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Spot> e = it.next();
            if (online.contains(e.getKey())) continue;
            Spot spot = e.getValue();
            it.remove();
            removeIfUnused(spot);
        }
    }

    /** Убрать весь свет фонариков (остановка сервера). */
    public static void clearAll() {
        Set<Spot> spots = new HashSet<>(SPOTS.values());
        SPOTS.clear();
        for (Spot s : spots) removeIfUnused(s);
    }

    private static Spot wantedSpot(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!ON.contains(id) || p.isSpectator() || !p.isAlive() || !hasFlashlight(p)) return null;
        Long blackout = BLACKOUT_UNTIL.get(id);
        if (blackout != null) {
            if (tick < blackout) return null;
            BLACKOUT_UNTIL.remove(id);
        }

        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(look.scale(0.6));

        // Ищем свободный блок: сначала у цели, потом на шаг ближе к игроку.
        for (int i = 0; i < 4; i++) {
            BlockPos pos = BlockPos.containing(target);
            if (canHoldLight(level, pos)) return new Spot(level, pos);
            target = target.subtract(look);
        }
        return null;
    }

    private static boolean canHoldLight(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        // Свой блок света можно занять повторно. Чужой (поставленный строителем карты) не трогаем.
        return state.is(Blocks.LIGHT) && SPOTS.containsValue(new Spot(level, pos));
    }

    private static void place(Spot spot) {
        if (spot.level().getBlockState(spot.pos()).isAir()) {
            spot.level().setBlock(spot.pos(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), 3);
        }
    }

    private static void removeIfUnused(Spot spot) {
        if (SPOTS.containsValue(spot)) return;
        if (spot.level().getBlockState(spot.pos()).is(Blocks.LIGHT)) {
            spot.level().setBlock(spot.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static boolean same(Spot a, Spot b) {
        if (a == null || b == null) return a == b;
        return a.level() == b.level() && a.pos().equals(b.pos());
    }
}
