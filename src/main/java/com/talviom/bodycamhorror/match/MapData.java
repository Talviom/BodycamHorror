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

/**
 * Точки карты, сохраняются вместе с миром: спавны команд, точки лута,
 * выходы сторон и точки, где появляется кассета, лобби.
 * Выход и спавн принадлежат стороне карты: после смены сторон команда спавнится
 * и выносит кассету на другой стороне.
 */
public class MapData extends SavedData {
    private static final String NAME = "bodycamhorror_map";

    private final Map<BhTeam, List<BlockPos>> spawns = new EnumMap<>(BhTeam.class);
    private final List<BlockPos> lootPoints = new ArrayList<>();
    private final Map<BhTeam, BlockPos> exits = new EnumMap<>(BhTeam.class);
    private final List<BlockPos> tapePoints = new ArrayList<>();
    private BlockPos lobby;
    private final Map<BhTeam, BlockPos> lobbyPads = new EnumMap<>(BhTeam.class);
    /** Логово Жильца: здесь он просыпается каждый раунд (null: самая тёмная точка, как раньше). */
    private BlockPos tenantHome;
    /** Лобби 1.0: точки тренировки и пульта (kit, console, tape, exit, breaker, emergency, flicker, radio, min, max). */
    private final Map<String, BlockPos> lobbyPoints = new java.util.HashMap<>();

    public MapData() {
        for (BhTeam t : BhTeam.values()) spawns.put(t, new ArrayList<>());
    }

    public static MapData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MapData::load, MapData::new, NAME);
    }

    public List<BlockPos> spawns(BhTeam team) {
        return spawns.get(team);
    }

    /** Точки лута идут зеркальными парами (0-1, 2-3, ...): в обе точки пары кладётся одно и то же. */
    private boolean pairedLoot;

    public boolean pairedLoot() {
        return pairedLoot;
    }

    public void setPairedLoot(boolean paired) {
        pairedLoot = paired;
        setDirty();
    }

    public List<BlockPos> lootPoints() {
        return lootPoints;
    }

    /** Выход стороны карты (null, если не задан). */
    public BlockPos exit(BhTeam side) {
        return exits.get(side);
    }

    public void setExit(BhTeam side, BlockPos pos) {
        if (pos == null) exits.remove(side);
        else exits.put(side, pos.immutable());
        setDirty();
    }

    public List<BlockPos> tapePoints() {
        return tapePoints;
    }

    /** Точка лобби: сюда попадают при входе в мир и после матча (null, если лобби нет). */
    public BlockPos lobby() {
        return lobby;
    }

    public void setLobby(BlockPos pos) {
        lobby = pos == null ? null : pos.immutable();
        setDirty();
    }

    /** Площадка в лобби: кто на ней стоит, попадает в эту команду. */
    public BlockPos lobbyPad(BhTeam team) {
        return lobbyPads.get(team);
    }

    public void setLobbyPad(BhTeam team, BlockPos pos) {
        if (pos == null) lobbyPads.remove(team);
        else lobbyPads.put(team, pos.immutable());
        setDirty();
    }

    @javax.annotation.Nullable
    public BlockPos tenantHome() {
        return tenantHome;
    }

    public void setTenantHome(@javax.annotation.Nullable BlockPos pos) {
        tenantHome = pos == null ? null : pos.immutable();
        setDirty();
    }

    @javax.annotation.Nullable
    public BlockPos lobbyPoint(String id) {
        return lobbyPoints.get(id);
    }

    public void setLobbyPoint(String id, @javax.annotation.Nullable BlockPos pos) {
        if (pos == null) lobbyPoints.remove(id);
        else lobbyPoints.put(id, pos.immutable());
        setDirty();
    }

    public void clearLobbyPoints() {
        lobbyPoints.clear();
        setDirty();
    }

    public static MapData load(CompoundTag tag) {
        MapData d = new MapData();
        for (BhTeam t : BhTeam.values()) {
            readList(tag.getList("spawns_" + t.id, Tag.TAG_COMPOUND), d.spawns.get(t));
        }
        readList(tag.getList("loot", Tag.TAG_COMPOUND), d.lootPoints);
        readList(tag.getList("tape", Tag.TAG_COMPOUND), d.tapePoints);
        for (BhTeam t : BhTeam.values()) {
            if (tag.contains("exit_" + t.id)) d.exits.put(t, NbtUtils.readBlockPos(tag.getCompound("exit_" + t.id)));
            if (tag.contains("pad_" + t.id)) d.lobbyPads.put(t, NbtUtils.readBlockPos(tag.getCompound("pad_" + t.id)));
        }
        if (tag.contains("lobby")) d.lobby = NbtUtils.readBlockPos(tag.getCompound("lobby"));
        d.pairedLoot = tag.getBoolean("paired_loot");
        if (tag.contains("tenant_home")) d.tenantHome = NbtUtils.readBlockPos(tag.getCompound("tenant_home"));
        CompoundTag lp = tag.getCompound("lobby_points");
        for (String k : lp.getAllKeys()) d.lobbyPoints.put(k, NbtUtils.readBlockPos(lp.getCompound(k)));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (BhTeam t : BhTeam.values()) tag.put("spawns_" + t.id, writeList(spawns.get(t)));
        tag.put("loot", writeList(lootPoints));
        tag.put("tape", writeList(tapePoints));
        for (BhTeam t : BhTeam.values()) {
            BlockPos e = exits.get(t);
            if (e != null) tag.put("exit_" + t.id, NbtUtils.writeBlockPos(e));
            BlockPos pad = lobbyPads.get(t);
            if (pad != null) tag.put("pad_" + t.id, NbtUtils.writeBlockPos(pad));
        }
        if (lobby != null) tag.put("lobby", NbtUtils.writeBlockPos(lobby));
        tag.putBoolean("paired_loot", pairedLoot);
        if (tenantHome != null) tag.put("tenant_home", NbtUtils.writeBlockPos(tenantHome));
        CompoundTag lp = new CompoundTag();
        for (Map.Entry<String, BlockPos> e : lobbyPoints.entrySet()) lp.put(e.getKey(), NbtUtils.writeBlockPos(e.getValue()));
        tag.put("lobby_points", lp);
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
