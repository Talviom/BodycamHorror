package com.talviom.bodycamhorror.sabotage;

import com.talviom.bodycamhorror.HorrorConfig;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.match.MatchState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Логика питания: какие лампы горят, переключение щитков и генератора, мигание света. */
public final class Sabotage {
    /** Кратковременное отключение ламп вокруг точки (мигание от хоррор-режиссёра). */
    private record Pulse(BlockPos center, long radiusSq, long from, long to) {}

    private static final Map<BlockPos, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final List<Pulse> PULSES = new ArrayList<>();
    private static boolean needsRecompute;
    private static int lastActivePulses;

    private Sabotage() {}

    // ---------- учёт блоков ----------

    public static void registerLamp(ServerLevel level, BlockPos pos) {
        SabotageData d = SabotageData.get(level);
        if (d.lamps.add(pos.immutable())) d.setDirty();
        needsRecompute = true;
    }

    public static void unregisterLamp(ServerLevel level, BlockPos pos) {
        SabotageData d = SabotageData.get(level);
        if (d.lamps.remove(pos)) d.setDirty();
    }

    public static void registerSwitch(ServerLevel level, BlockPos pos) {
        SabotageData d = SabotageData.get(level);
        if (d.switches.add(pos.immutable())) d.setDirty();
        needsRecompute = true;
    }

    public static void unregisterSwitch(ServerLevel level, BlockPos pos) {
        SabotageData d = SabotageData.get(level);
        if (d.switches.remove(pos)) d.setDirty();
        COOLDOWN_UNTIL.remove(pos);
        needsRecompute = true;
    }

    // ---------- переключение ----------

    /** @return сообщение для игрока, который нажал на выключатель */
    public static String tryToggle(ServerLevel level, BlockPos pos, BlockState state, PowerSwitchBlock block) {
        MatchState st = MatchManager.INSTANCE.state();
        if (st == MatchState.PREP || st == MatchState.ROUND_END) return "Сейчас нельзя";

        long now = level.getGameTime();
        Long until = COOLDOWN_UNTIL.get(pos);
        if (until != null && now < until) return "Подождите " + ((until - now) / 20 + 1) + " с";

        boolean on = !state.getValue(PowerSwitchBlock.POWERED);
        level.setBlock(pos, state.setValue(PowerSwitchBlock.POWERED, on), 3);
        level.updateNeighborsAt(pos, block);
        COOLDOWN_UNTIL.put(pos.immutable(), now + HorrorConfig.SWITCH_COOLDOWN_SECONDS.get() * 20L);
        needsRecompute = true;

        // Громкий звук: переключение выдаёт позицию того, кто это сделал.
        if (block.global) {
            play(level, pos, on ? "bodycamhorror:generator_up" : "bodycamhorror:generator_down", 4.0f, 1.0f);
        } else {
            play(level, pos, on ? "bodycamhorror:breaker_on" : "bodycamhorror:breaker_off", 2.0f, 1.0f);
        }

        if (block.global) return on ? "Генератор запущен" : "Генератор заглушен";
        return on ? "Питание включено" : "Питание отключено";
    }

    /** Перед каждым раундом: всё включено, кулдауны и мигания сброшены. */
    public static void resetAll(ServerLevel level) {
        SabotageData d = SabotageData.get(level);
        for (BlockPos pos : d.switches) {
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (s.getBlock() instanceof PowerSwitchBlock sw && !s.getValue(PowerSwitchBlock.POWERED)) {
                level.setBlock(pos, s.setValue(PowerSwitchBlock.POWERED, true), 3);
                level.updateNeighborsAt(pos, sw);
            }
        }
        COOLDOWN_UNTIL.clear();
        PULSES.clear();
        lastActivePulses = 0;
        recompute(level);
    }

    // ---------- мигание ----------

    /**
     * Лампы вокруг точки несколько раз мигают и ненадолго гаснут.
     * @return false, если рядом нет ни одной лампы
     */
    public static boolean flickerAround(ServerLevel level, BlockPos center, int radius) {
        long r2 = (long) radius * radius;
        boolean any = false;
        for (BlockPos lamp : SabotageData.get(level).lamps) {
            if (lamp.distSqr(center) <= r2) {
                any = true;
                break;
            }
        }
        if (!any) return false;

        RandomSource rnd = level.getRandom();
        long t = level.getGameTime();
        int blinks = 2 + rnd.nextInt(3);
        for (int i = 0; i < blinks; i++) {
            int off = 2 + rnd.nextInt(5);
            PULSES.add(new Pulse(center.immutable(), r2, t, t + off));
            t += off + 2 + rnd.nextInt(5);
        }
        PULSES.add(new Pulse(center.immutable(), r2, t, t + 15 + rnd.nextInt(35)));
        play(level, center, "bodycamhorror:lamp_buzz", 1.0f, 0.9f + rnd.nextFloat() * 0.2f);
        return true;
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean changed = PULSES.removeIf(p -> p.to() <= now);
        int active = 0;
        for (Pulse p : PULSES) if (p.from() <= now) active++;
        if (active != lastActivePulses) changed = true;
        lastActivePulses = active;
        if (changed || needsRecompute) recompute(level);
    }

    // ---------- расчёт света ----------

    public static void recompute(ServerLevel level) {
        needsRecompute = false;
        SabotageData d = SabotageData.get(level);
        long now = level.getGameTime();

        boolean globalOff = false;
        List<BlockPos> offBreakers = new ArrayList<>();
        boolean dirty = false;
        for (Iterator<BlockPos> it = d.switches.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof PowerSwitchBlock sw)) {
                it.remove();
                dirty = true;
                continue;
            }
            if (s.getValue(PowerSwitchBlock.POWERED)) continue;
            if (sw.global) globalOff = true;
            else offBreakers.add(pos);
        }

        long r = HorrorConfig.BREAKER_RADIUS.get();
        long r2 = r * r;
        for (Iterator<BlockPos> it = d.lamps.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof LampBlock)) {
                it.remove();
                dirty = true;
                continue;
            }
            boolean lit = !globalOff;
            if (lit) {
                for (BlockPos b : offBreakers) {
                    if (b.distSqr(pos) <= r2) {
                        lit = false;
                        break;
                    }
                }
            }
            if (lit) {
                for (Pulse p : PULSES) {
                    if (p.from() <= now && now < p.to() && p.center().distSqr(pos) <= p.radiusSq()) {
                        lit = false;
                        break;
                    }
                }
            }
            if (s.getValue(LampBlock.LIT) != lit) level.setBlock(pos, s.setValue(LampBlock.LIT, lit), 3);
        }
        if (dirty) d.setDirty();
    }

    private static void play(ServerLevel level, BlockPos pos, String soundId, float volume, float pitch) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
        if (sound != null) level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
