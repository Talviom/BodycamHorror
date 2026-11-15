package com.talviom.bodycamhorror.sabotage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** Где на карте стоят лампы и выключатели. Сохраняется вместе с миром. */
public class SabotageData extends SavedData {
    private static final String NAME = "bodycamhorror_sabotage";

    public final Set<BlockPos> lamps = new HashSet<>();
    public final Set<BlockPos> switches = new HashSet<>();

    public static SabotageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SabotageData::load, SabotageData::new, NAME);
    }

    public static SabotageData load(CompoundTag tag) {
        SabotageData d = new SabotageData();
        for (long l : tag.getLongArray("lamps")) d.lamps.add(BlockPos.of(l));
        for (long l : tag.getLongArray("switches")) d.switches.add(BlockPos.of(l));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("lamps", lamps.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("switches", switches.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }
}
