package com.talviom.bodycamhorror.match;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Точки карты (спавны команд и точки лута), сохраняются вместе с миром. */
public class MapData extends SavedData {
    private static final String NAME = "bodycamhorror_map";

    private final Map<BhTeam, List<BlockPos>> spawns = new EnumMap<>(BhTeam.class);
    private final List<BlockPos> lootPoints = new ArrayList<>();

    public MapData() {
        for (BhTeam t : BhTeam.values()) spawns.put(t, new ArrayList<>());
    }

    public static MapData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MapData::load, MapData::new, NAME);
    }

    public List<BlockPos> spawns(BhTeam team) {
        return spawns.get(team);
    }

    public List<BlockPos> lootPoints() {
        return lootPoints;
    }

    public static MapData load(CompoundTag tag) {
        MapData d = new MapData();
        for (BhTeam t : BhTeam.values()) {
            readList(tag.getList("spawns_" + t.id, Tag.TAG_COMPOUND), d.spawns.get(t));
        }
        readList(tag.getList("loot", Tag.TAG_COMPOUND), d.lootPoints);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (BhTeam t : BhTeam.values()) tag.put("spawns_" + t.id, writeList(spawns.get(t)));
        tag.put("loot", writeList(lootPoints));
        return tag;
    }

    private static void readList(ListTag list, List<BlockPos> out) {
        for (int i = 0; i < list.size(); i++) out.add(NbtUtils.readBlockPos(list.getCompound(i)));
    }

    private static ListTag writeList(List<BlockPos> in) {
        ListTag list = new ListTag();
        for (BlockPos p : in) list.add(NbtUtils.writeBlockPos(p));
        return list;
    }
}
