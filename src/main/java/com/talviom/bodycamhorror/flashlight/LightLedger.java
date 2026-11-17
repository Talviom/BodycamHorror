package com.talviom.bodycamhorror.flashlight;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Список невидимых блоков света, которые сейчас стоят в мире из-за фонариков.
 * Сохраняется вместе с миром. Если сервер упал или игру закрыли без нормальной остановки,
 * при следующем запуске эти блоки убираются, и на карте не остаётся вечных светящихся пятен.
 */
public final class LightLedger extends SavedData {
    private static final String NAME = "bodycamhorror_flashlight";

    private final Set<Long> positions = new HashSet<>();

    public static LightLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LightLedger::load, LightLedger::new, NAME);
    }

    private static LightLedger load(CompoundTag tag) {
        LightLedger ledger = new LightLedger();
        for (long p : tag.getLongArray("lights")) ledger.positions.add(p);
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("lights", positions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    /** Записать, какие блоки света стоят в этом мире прямо сейчас. */
    public void replace(Set<Long> now) {
        if (positions.equals(now)) return;
        positions.clear();
        positions.addAll(now);
        setDirty();
    }

    /** Запуск сервера: убрать свет, оставшийся с прошлого раза. @return сколько блоков убрано */
    public int sweep(ServerLevel level) {
        int removed = 0;
        for (long p : positions) {
            BlockPos pos = BlockPos.of(p);
            if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        if (!positions.isEmpty()) {
            positions.clear();
            setDirty();
        }
        return removed;
    }
}
