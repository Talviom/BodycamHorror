package com.talviom.bodycamhorror.flashlight;

import com.talviom.bodycamhorror.HorrorConfig;
import com.talviom.bodycamhorror.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Нагрудный фонарик с батарейкой.
 *
 * Луч идёт от правого плеча вдоль взгляда: сервер ставит цепочку невидимых блоков света
 * (bodycamhorror:beam_light, убирают себя сами). Вдоль луча свет слабый, у стены, в которую упёрся луч, пятно поярче,
 * и чем дальше стена, тем пятно тусклее. Луч обрывается на первой стене, сквозь стены не светит.
 * Такой свет видят все игроки, поэтому включённый фонарик выдаёт владельца.
 *
 * Батарейка садится, пока фонарик включён, и заряжается, пока выключен.
 * Ниже 15 % луч мигает, на нуле фонарик выключается сам.
 */
public final class Flashlight {
    private static final float LOW_BATTERY = 0.15f;
    private static final float MIN_TO_TURN_ON = 0.03f;

    private record Spot(ServerLevel level, BlockPos pos) {}

    private static final Set<UUID> ON = new HashSet<>();
    private static final Set<UUID> LIT = new HashSet<>();
    private static final Map<UUID, Float> CHARGE = new HashMap<>();
    private static final Map<UUID, Long> BLACKOUT_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> FLICKER_UNTIL = new HashMap<>();
    /** Блоки света, которые поставил фонарик, и их уровень. */
    private static final Map<Spot, Integer> PLACED = new HashMap<>();
    private static final Random RND = new Random();
    private static long tick;

    private Flashlight() {}

    public static boolean hasFlashlight(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModBlocks.FLASHLIGHT.get())) return true;
        }
        return false;
    }

    /** Фонарик включён (даже если луч сейчас мигнул). */
    public static boolean isOn(ServerPlayer p) {
        return ON.contains(p.getUUID());
    }

    /** Луч сейчас реально светит. */
    public static boolean isLit(ServerPlayer p) {
        return LIT.contains(p.getUUID());
    }

    /** Заряд от 0 до 1. */
    public static float charge(ServerPlayer p) {
        return CHARGE.getOrDefault(p.getUUID(), 1f);
    }

    /** Новый раунд: фонарик выключен, батарейка полная. */
    public static void resetPlayer(ServerPlayer p) {
        UUID id = p.getUUID();
        ON.remove(id);
        CHARGE.put(id, 1f);
        BLACKOUT_UNTIL.remove(id);
        FLICKER_UNTIL.remove(id);
    }

    /** Новая батарейка: заряд фонарика сразу полный. */
    public static void refill(ServerPlayer p) {
        CHARGE.put(p.getUUID(), 1f);
    }

    public static void toggle(ServerPlayer p) {
        if (!hasFlashlight(p)) {
            p.displayClientMessage(Component.literal("No flashlight"), true);
            return;
        }
        UUID id = p.getUUID();
        boolean on = !ON.contains(id);
        if (on && charge(p) < MIN_TO_TURN_ON) {
            p.displayClientMessage(Component.literal("Battery is dead"), true);
            click(p, 0.9f);
            return;
        }
        if (on) ON.add(id);
        else ON.remove(id);
        click(p, on ? 1.6f : 1.3f);
    }

    /** Фонарик игрока гаснет на заданное время (событие хоррор-режиссёра). */
    public static void blackout(ServerPlayer p, int ticks) {
        BLACKOUT_UNTIL.put(p.getUUID(), tick + ticks);
    }

    public static void tick(MinecraftServer server) {
        tick++;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(p.getUUID());
            updateBattery(p);
        }
        ON.retainAll(online);
        if (tick % 2 != 0) return;

        Map<Spot, Integer> wanted = new HashMap<>();
        LIT.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!beamActive(p)) continue;
            LIT.add(p.getUUID());
            addBeam(p, wanted);
        }
        apply(wanted);
    }

    /** Убрать весь свет фонариков (остановка сервера). */
    public static void clearAll() {
        apply(new HashMap<>());
        ON.clear();
        LIT.clear();
    }

    // ---------- батарейка ----------

    private static void updateBattery(ServerPlayer p) {
        UUID id = p.getUUID();
        float c = CHARGE.getOrDefault(id, 1f);
        if (ON.contains(id)) {
            if (!hasFlashlight(p) || p.isSpectator() || !p.isAlive()) {
                ON.remove(id);
                return;
            }
            c -= 1f / (HorrorConfig.BATTERY_SECONDS.get() * 20f);
            if (c <= 0f) {
                c = 0f;
                ON.remove(id);
                p.displayClientMessage(Component.literal("Battery is dead"), true);
                click(p, 0.8f);
            }
        } else if (c < 1f) {
            c = Math.min(1f, c + 1f / (HorrorConfig.RECHARGE_SECONDS.get() * 20f));
        }
        CHARGE.put(id, c);
    }

    private static boolean beamActive(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!ON.contains(id) || p.isSpectator() || !p.isAlive()) return false;
        Long blackout = BLACKOUT_UNTIL.get(id);
        if (blackout != null) {
            if (tick < blackout) return false;
            BLACKOUT_UNTIL.remove(id);
        }
        // Слабая батарейка: луч то и дело гаснет на мгновение, и чем меньше заряд, тем чаще.
        float c = charge(p);
        Long flicker = FLICKER_UNTIL.get(id);
        if (flicker != null && tick < flicker) return false;
        if (c < LOW_BATTERY) {
            float chance = 0.04f + 0.25f * (1f - c / LOW_BATTERY);
            if (RND.nextFloat() < chance) {
                FLICKER_UNTIL.put(id, tick + 2 + RND.nextInt(6));
                return false;
            }
        }
        return true;
    }

    // ---------- луч ----------

    private static void addBeam(ServerPlayer p, Map<Spot, Integer> out) {
        ServerLevel level = p.serverLevel();
        double range = HorrorConfig.BEAM_RANGE.get();
        Vec3 look = p.getLookAngle();
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        // Фонарик закреплён на правом плече, чуть ниже глаз.
        Vec3 origin = p.getEyePosition().add(right.scale(0.35)).add(0, -0.35, 0);

        Vec3 end = origin.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        boolean hitWall = hit.getType() != HitResult.Type.MISS;
        double dist = hitWall ? hit.getLocation().distanceTo(origin) : range;
        boolean weak = charge(p) < LOW_BATTERY;

        // Вдоль луча: слабая подсветка, гаснет к концу.
        for (double d = 2.0; d < dist - 1.0; d += 2.5) {
            int lvl = Math.round(Mth.lerp((float) (d / range), 8f, 5f));
            put(out, level, origin.add(look.scale(d)), look, weak ? lvl - 2 : lvl);
        }
        // Пятно у стены: близко ярче, далеко тусклее. В пустоте луч просто растворяется.
        int spot = hitWall ? Mth.clamp((int) Math.round(12 - dist * 0.5), 7, 12) : 5;
        put(out, level, origin.add(look.scale(Math.max(0.8, dist - 0.4))), look, weak ? spot - 3 : spot);
    }

    private static void put(Map<Spot, Integer> out, ServerLevel level, Vec3 at, Vec3 look, int lvl) {
        if (lvl <= 0) return;
        lvl = Math.min(15, lvl);
        Vec3 pos = at;
        for (int i = 0; i < 3; i++) {
            BlockPos bp = BlockPos.containing(pos);
            Spot s = new Spot(level, bp);
            if (canHoldLight(s)) {
                out.merge(s, lvl, Math::max);
                return;
            }
            pos = pos.subtract(look.scale(0.5));
        }
    }

    private static boolean canHoldLight(Spot s) {
        BlockState state = s.level().getBlockState(s.pos());
        // Свой блок света можно занять повторно. Чужой (minecraft:light строителя карты) не трогаем.
        return state.isAir() || state.is(ModBlocks.BEAM_LIGHT.get());
    }

    /** Для /bh debug: сколько блоков света числится за фонариками и сколько из них реально стоит в мире. */
    public static String debugLights() {
        int present = 0;
        for (Spot s : PLACED.keySet()) {
            if (s.level().getBlockState(s.pos()).is(ModBlocks.BEAM_LIGHT.get())) present++;
        }
        return present + "/" + PLACED.size();
    }

    /** Для проверки: забыть все фонарики, не убирая свет, как будто сервер упал и запустился заново. */
    public static void debugForget() {
        PLACED.clear();
        ON.clear();
        LIT.clear();
    }

    /** Светит ли сюда сейчас чей-то фонарик (для самоуборки BeamLightBlock). */
    static boolean ownsLight(ServerLevel level, BlockPos pos) {
        return PLACED.containsKey(new Spot(level, pos));
    }

    private static void apply(Map<Spot, Integer> wanted) {
        applyBlocks(wanted);
    }

    private static void applyBlocks(Map<Spot, Integer> wanted) {
        for (Iterator<Map.Entry<Spot, Integer>> it = PLACED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Spot, Integer> e = it.next();
            if (wanted.containsKey(e.getKey())) continue;
            Spot s = e.getKey();
            if (s.level().getBlockState(s.pos()).is(ModBlocks.BEAM_LIGHT.get())) {
                s.level().setBlock(s.pos(), Blocks.AIR.defaultBlockState(), 3);
            }
            it.remove();
        }
        for (Map.Entry<Spot, Integer> e : wanted.entrySet()) {
            Spot s = e.getKey();
            int lvl = e.getValue();
            Integer have = PLACED.get(s);
            if (have != null && have == lvl && s.level().getBlockState(s.pos()).is(ModBlocks.BEAM_LIGHT.get())) continue;
            if (!canHoldLight(s)) continue;
            s.level().setBlock(s.pos(), ModBlocks.BEAM_LIGHT.get().defaultBlockState().setValue(BeamLightBlock.LEVEL, lvl), 3);
            PLACED.put(s, lvl);
        }
    }

    private static void click(ServerPlayer p, float pitch) {
        SoundEvent click = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("minecraft:block.lever.click"));
        if (click != null) p.serverLevel().playSound(null, p.blockPosition(), click, SoundSource.PLAYERS, 0.35f, pitch);
    }
}
