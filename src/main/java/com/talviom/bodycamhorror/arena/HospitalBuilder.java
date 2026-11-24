package com.talviom.bodycamhorror.arena;

import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import com.talviom.bodycamhorror.sabotage.EmergencyLampBlock;
import com.talviom.bodycamhorror.sabotage.PowerSwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Карта «Больница» (1.0): городская больница 80-х, брошенная в 90-е. Схема согласована с пользователем 24.11.2026.
 *
 * Координаты как на схеме: x с запада на восток 0..88 (забор), z с севера на юг -8..62 (забор), 1 клетка = 1 блок.
 * Мировая точка = base + (x, y, z + 8), то есть base стоит в северо-западном углу забора.
 * Высоты: двор и пол первого этажа y -1 (стоят на y 0), первый этаж воздух 0..3, перекрытие y 4,
 * второй этаж воздух 5..8, крыша y 9, парапет y 10. Морг в полуподвале: пол y -4, стоят на y -3.
 *
 * Корпус 64 x 44 (x 12..76, z 8..52) вокруг двора-колодца (x 26..62, z 22..38). Кольцо коридоров шириной 3 на обоих
 * этажах. Стороны не зеркальные по виду, но все точки матча идут парами при повороте на 180° вокруг (44, 30):
 * (x, z) -> (88 - x, 60 - z). Команда A (запад) в приёмном покое, команда B (восток) на кухне у служебного входа.
 *
 * Строится всё, кроме мебели: её ставит пользователь. Особые комнаты подписаны табличками (текст из lang-файлов),
 * точки лута помечены табличками, места кассеты бочками (после мебели точки кассеты ставятся заново).
 */
public final class HospitalBuilder {
    private static final int CX = 88;   // для отражения: x -> CX - x
    private static final int CZ = 60;   // z -> CZ - z

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // Корпус
    private static final BlockState FACADE = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState FACADE_BAND = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState PLINTH = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState PLINTH_CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState PANEL = Blocks.CYAN_TERRACOTTA.defaultBlockState();
    private static final BlockState PANEL_PEEL = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState BAND = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState PLASTER = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState PLASTER_OLD = Blocks.CALCITE.defaultBlockState();
    private static final BlockState ROOF = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState PARAPET = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState LINO = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState TILE_A = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TILE_B = Blocks.POLISHED_DIORITE.defaultBlockState();
    private static final BlockState WHITE_TILE_A = Blocks.CALCITE.defaultBlockState();
    private static final BlockState WHITE_TILE_B = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState BLOOD = Blocks.RED_TERRACOTTA.defaultBlockState();

    // Двор и забор
    private static final BlockState FENCE = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState FENCE_OLD = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState FENCE_POST = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    // Типы пола комнат
    private static final int LINO_FLOOR = 0, CHECKER = 1, WHITE = 2;
    // Виды дверей
    private static final int DOOR = 0, LOCKED = 1, NONE = 2, OPEN = 3;

    private final ServerLevel level;
    private final BlockPos base;
    private final Random rnd = new Random(1980);
    /** Решётки, стёкла, заборы: в конце им пересчитываются соединения с соседями. */
    private final List<BlockPos> connect = new ArrayList<>();
    private int lampCount;

    /**
     * Комната: внутренний прямоугольник (включительно), вид пола, сторона двери (N, S, W, E) и место двери вдоль стены
     * (-1: посередине), вид двери, ключ таблички (bh.room.&lt;id&gt;, вторая строка bh.room.&lt;id&gt;.2, если line2).
     */
    private record Room(String id, int x1, int z1, int x2, int z2, int floor, char side, int doorAt, int door, boolean line2, String extra) {}

    private HospitalBuilder(ServerLevel level, BlockPos base) {
        this.level = level;
        this.base = base;
    }

    public static Component build(ServerLevel level, BlockPos base) {
        return new HospitalBuilder(level, base).run();
    }

    private Component run() {
        clear();
        ground();
        forest();
        fence();
        shell();
        List<Room> f1 = groundFloorRooms();
        List<Room> f2 = upperFloorRooms();
        carveFloor(f1, -1, 0, 3);
        carveFloor(f2, 4, 5, 8);
        corridors();
        hall();
        serviceStairs();
        shaft();
        holes();
        windows();
        doors(f1, 0);
        doors(f2, 5);
        courtyard();
        descents();
        outbuilding();
        frontYard();
        sideYards();
        ruin();
        lamps();
        emergencyLamps();
        signs(f1, 0);
        signs(f2, 5);
        extraSigns();
        MapData map = points();
        reconnect();
        Component lobby = LobbyBuilder.build(level, p(-30, 0, 20));
        return Component.translatable("bh.cmd.hospital_built", lampCount, map.lootPoints().size(), map.tapePoints().size(), lobby);
    }

    // ---------- земля, лес, забор ----------

    /** Чистит место под карту (вместе с лесом вокруг), чтобы постройка шла на пустом. */
    private void clear() {
        fill(-14, -6, -20, 102, 22, 76, AIR);
    }

    private void ground() {
        fill(-14, -6, -20, 102, -2, 76, STONE);
        for (int x = -14; x <= 102; x++) {
            for (int z = -20; z <= 76; z++) {
                boolean inside = x >= 0 && x <= CX && z >= -8 && z <= 62;
                set(x, -1, z, soil(inside));
            }
        }
    }

    private BlockState soil(boolean inside) {
        int r = rnd.nextInt(100);
        if (!inside) return r < 55 ? Blocks.PODZOL.defaultBlockState() : r < 85 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        if (r < 45) return Blocks.COARSE_DIRT.defaultBlockState();
        if (r < 70) return Blocks.DIRT.defaultBlockState();
        if (r < 85) return Blocks.PODZOL.defaultBlockState();
        if (r < 93) return GRAVEL;
        return Blocks.ROOTED_DIRT.defaultBlockState();
    }

    /** Лес силуэтом за забором: две полосы елей и голых деревьев. Туман прячет его почти целиком. */
    private void forest() {
        for (int z = -18; z <= 74; z += 4) {
            spruce(-4 - rnd.nextInt(2), z + rnd.nextInt(2));
            spruce(-9 - rnd.nextInt(3), z + 2 + rnd.nextInt(2));
            spruce(92 + rnd.nextInt(2), z + rnd.nextInt(2));
            spruce(97 + rnd.nextInt(3), z + 2 + rnd.nextInt(2));
        }
        for (int x = -2; x <= 90; x += 4) {
            spruce(x + rnd.nextInt(2), -12 - rnd.nextInt(2));
            spruce(x + 2 + rnd.nextInt(2), -17 - rnd.nextInt(2));
            spruce(x + rnd.nextInt(2), 66 + rnd.nextInt(2));
            spruce(x + 2 + rnd.nextInt(2), 71 + rnd.nextInt(3));
        }
    }

    private void spruce(int x, int z) {
        if (rnd.nextInt(5) == 0) {
            deadTree(x, z);
            return;
        }
        int h = 8 + rnd.nextInt(5);
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = 0; y < h; y++) set(x, y, z, log);
        for (int y = 3; y <= h; y++) {
            int r = y == h ? 0 : Math.max(1, (h - y) / 3 + ((y & 1) == 0 ? 1 : 0));
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > r + 1 || (dx == 0 && dz == 0 && y < h)) continue;
                    if (get(x + dx, y, z + dz).isAir()) set(x + dx, y, z + dz, leaves);
                }
        }
        set(x, h + 1, z, leaves);
    }

    /** Голое дерево: ствол тёмного дуба и несколько сухих веток. */
    private void deadTree(int x, int z) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        int h = 4 + rnd.nextInt(3);
        for (int y = 0; y < h; y++) set(x, y, z, log);
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int i = 0; i < 3; i++) {
            Direction d = dirs[rnd.nextInt(4)];
            int y = h - 1 - rnd.nextInt(2);
            BlockState branch = log.setValue(RotatedPillarBlock.AXIS, d.getAxis());
            set(x + d.getStepX(), y, z + d.getStepZ(), branch);
            if (rnd.nextBoolean()) set(x + d.getStepX() * 2, y + 1, z + d.getStepZ() * 2, branch);
        }
        set(x, h, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
        connect.add(p(x, h, z));
    }

    /** Бетонный забор 3 блока с решёткой-колючкой поверху, столбы каждые 6 блоков. */
    private void fence() {
        for (int x = 0; x <= CX; x++) {
            fencePost(x, -8, x % 6 == 0);
            fencePost(x, 62, x % 6 == 0);
        }
        for (int z = -8; z <= 62; z++) {
            fencePost(0, z, (z + 8) % 6 == 0);
            fencePost(CX, z, (z + 8) % 6 == 0);
        }
        // Главные ворота на цепи (закрыты): решётка во весь проём, цепь посередине.
        for (int x = 40; x <= 48; x++) {
            for (int y = 0; y <= 3; y++) setC(x, y, 62, BARS);
        }
        set(40, 0, 62, FENCE_POST);
        set(40, 1, 62, FENCE_POST);
        set(40, 2, 62, FENCE_POST);
        set(40, 3, 62, FENCE_POST);
        set(48, 0, 62, FENCE_POST);
        set(48, 1, 62, FENCE_POST);
        set(48, 2, 62, FENCE_POST);
        set(48, 3, 62, FENCE_POST);
        set(44, 1, 61, Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.X));
        // Калитки к выходам A (запад) и B (восток): закрытые деревянные ворота в заборе.
        for (int z = 29; z <= 31; z++) {
            fill(0, 0, z, 0, 3, z, AIR);
            fill(CX, 0, z, CX, 3, z, AIR);
            set(0, 0, z, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
            set(CX, 0, z, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.WEST));
            setC(0, 2, z, BARS);
            setC(CX, 2, z, BARS);
            set(0, 3, z, FENCE);
            set(CX, 3, z, FENCE);
        }
    }

    private void fencePost(int x, int z, boolean post) {
        for (int y = 0; y <= 2; y++) set(x, y, z, post ? FENCE_POST : rnd.nextInt(7) == 0 ? FENCE_OLD : FENCE);
        if (post) set(x, 3, z, FENCE_POST);
        else if (rnd.nextInt(9) != 0) setC(x, 3, z, BARS);
    }

    // ---------- корпус ----------

    /** Сплошной объём корпуса: стены по высоте окрашены (панель, полоса, побелка), пол, перекрытие, крыша, фасад. */
    private void shell() {
        for (int x = 12; x <= 76; x++) {
            for (int z = 8; z <= 52; z++) {
                boolean court = x >= 26 && x <= 62 && z >= 22 && z <= 38;
                if (court) continue;
                set(x, -1, z, LINO);
                for (int y = 0; y <= 3; y++) set(x, y, z, wallAt(y));
                set(x, 4, z, ROOF);
                for (int y = 5; y <= 8; y++) set(x, y, z, wallAt(y - 5));
                set(x, 9, z, ROOF);
                boolean outer = x == 12 || x == 76 || z == 8 || z == 52;
                boolean courtWall = (x == 25 || x == 63) && z >= 21 && z <= 39 || (z == 21 || z == 39) && x >= 25 && x <= 63;
                if (outer || courtWall) {
                    for (int y = -1; y <= 9; y++) set(x, y, z, facadeAt(y));
                    set(x, 10, z, PARAPET);
                }
            }
        }
    }

    private BlockState wallAt(int y) {
        if (y <= 1) return rnd.nextInt(20) == 0 ? PANEL_PEEL : PANEL;
        if (y == 2) return BAND;
        return rnd.nextInt(16) == 0 ? PLASTER_OLD : PLASTER;
    }

    private BlockState facadeAt(int y) {
        if (y <= 0) return rnd.nextInt(5) == 0 ? PLINTH_CRACKED : PLINTH;
        if (y == 4 || y == 9) return FACADE_BAND;
        int r = rnd.nextInt(30);
        if (r == 0) return Blocks.ANDESITE.defaultBlockState();
        if (r == 1) return FACADE_BAND;
        return FACADE;
    }

    private List<Room> groundFloorRooms() {
        List<Room> r = new ArrayList<>();
        // Западное крыло (сторона A): снаружи
        r.add(new Room("xray", 13, 9, 16, 20, WHITE, 'E', -1, DOOR, true, null));
        r.add(new Room("admissions", 13, 22, 16, 38, CHECKER, 'E', 26, OPEN, true, "bh.room.spawn_a"));
        r.add(new Room("storage", 13, 40, 16, 51, LINO_FLOOR, 'E', -1, LOCKED, false, null));
        // Западное крыло: со стороны двора
        r.add(new Room("procedure", 22, 18, 24, 29, WHITE, 'W', -1, DOOR, false, null));
        r.add(new Room("showers", 22, 31, 24, 42, WHITE, 'W', -1, DOOR, false, null));
        // Южное крыло: снаружи (холл отдельно)
        r.add(new Room("pharmacy", 18, 48, 29, 51, LINO_FLOOR, 'N', -1, DOOR, false, null));
        r.add(new Room("registry", 31, 48, 37, 51, LINO_FLOOR, 'N', -1, DOOR, false, null));
        r.add(new Room("cloakroom", 51, 48, 57, 51, LINO_FLOOR, 'N', -1, DOOR, false, null));
        r.add(new Room("archive", 59, 48, 70, 51, LINO_FLOOR, 'N', -1, DOOR, false, null));
        // Южное крыло: со стороны двора
        r.add(new Room("dressing", 26, 40, 37, 42, WHITE, 'S', -1, DOOR, false, null));
        r.add(new Room("linen", 51, 40, 62, 42, LINO_FLOOR, 'S', -1, DOOR, false, null));
        // Восточное крыло (сторона B)
        r.add(new Room("kitchen_store", 72, 9, 75, 20, LINO_FLOOR, 'W', -1, LOCKED, true, null));
        r.add(new Room("kitchen", 72, 22, 75, 38, CHECKER, 'W', 34, OPEN, true, "bh.room.spawn_b"));
        r.add(new Room("utility", 72, 40, 75, 51, LINO_FLOOR, 'W', -1, DOOR, false, null));
        r.add(new Room("canteen", 64, 18, 66, 29, LINO_FLOOR, 'E', -1, DOOR, false, null));
        r.add(new Room("chapel", 64, 31, 66, 42, LINO_FLOOR, 'E', -1, DOOR, false, null));
        // Северное крыло: снаружи
        r.add(new Room("isolation", 18, 9, 29, 12, WHITE, 'S', -1, DOOR, false, null));
        r.add(new Room("sanitary", 31, 9, 37, 12, WHITE, 'S', -1, DOOR, true, null));
        r.add(new Room("vestibule_n", 39, 9, 49, 12, CHECKER, 'S', -1, NONE, true, null));
        r.add(new Room("nurses", 51, 9, 57, 12, LINO_FLOOR, 'S', -1, DOOR, false, null));
        r.add(new Room("security", 59, 9, 70, 12, LINO_FLOOR, 'S', -1, DOOR, true, null));
        // Северное крыло: со стороны двора
        r.add(new Room("lab", 26, 18, 37, 20, WHITE, 'N', -1, DOOR, false, null));
        r.add(new Room("service_stairs", 39, 18, 49, 20, CHECKER, 'N', 40, NONE, true, null));
        r.add(new Room("store", 51, 18, 62, 20, LINO_FLOOR, 'N', -1, DOOR, false, null));
        return r;
    }

    private List<Room> upperFloorRooms() {
        List<Room> r = new ArrayList<>();
        r.add(new Room("ward1", 13, 9, 16, 20, LINO_FLOOR, 'E', -1, DOOR, false, null));
        r.add(new Room("ward2", 13, 22, 16, 29, LINO_FLOOR, 'E', -1, DOOR, false, null));
        r.add(new Room("ward3", 13, 31, 16, 38, LINO_FLOOR, 'E', -1, DOOR, false, null));
        r.add(new Room("ward4", 13, 40, 16, 51, LINO_FLOOR, 'E', -1, DOOR, false, null));
        r.add(new Room("ward5", 22, 18, 24, 29, LINO_FLOOR, 'W', -1, DOOR, true, null));
        r.add(new Room("wc_w", 22, 31, 24, 42, WHITE, 'W', -1, LOCKED, false, null));
        r.add(new Room("doctors", 18, 48, 26, 51, LINO_FLOOR, 'N', -1, DOOR, false, null));
        r.add(new Room("nurse_post", 28, 48, 37, 51, LINO_FLOOR, 'N', -1, OPEN, true, null));
        r.add(new Room("head_doctor", 51, 48, 60, 51, LINO_FLOOR, 'N', -1, DOOR, true, null));
        r.add(new Room("reception", 62, 48, 70, 51, LINO_FLOOR, 'N', -1, DOOR, true, null));
        r.add(new Room("ward6", 26, 40, 37, 42, LINO_FLOOR, 'S', -1, DOOR, false, "bh.room.descent"));
        r.add(new Room("ward7", 51, 40, 62, 42, LINO_FLOOR, 'S', -1, DOOR, false, null));
        r.add(new Room("ward8", 72, 9, 75, 20, LINO_FLOOR, 'W', -1, DOOR, false, null));
        r.add(new Room("ward9", 72, 22, 75, 29, LINO_FLOOR, 'W', -1, DOOR, false, null));
        r.add(new Room("ward10", 72, 31, 75, 38, LINO_FLOOR, 'W', -1, DOOR, false, null));
        r.add(new Room("ward11", 72, 40, 75, 51, LINO_FLOOR, 'W', -1, DOOR, false, null));
        r.add(new Room("wc_e", 64, 18, 66, 29, WHITE, 'E', -1, LOCKED, false, null));
        r.add(new Room("ward12", 64, 31, 66, 42, LINO_FLOOR, 'E', -1, DOOR, true, null));
        r.add(new Room("icu_w", 18, 9, 26, 12, WHITE, 'S', -1, DOOR, false, null));
        r.add(new Room("preop", 28, 9, 37, 12, WHITE, 'S', -1, DOOR, true, null));
        r.add(new Room("operating", 39, 9, 49, 12, WHITE, 'S', -1, DOOR, false, "bh.room.descent"));
        r.add(new Room("sterile", 51, 9, 60, 12, WHITE, 'S', -1, DOOR, false, null));
        r.add(new Room("icu_e", 62, 9, 70, 12, WHITE, 'S', -1, DOOR, true, null));
        r.add(new Room("ward13", 26, 18, 37, 20, LINO_FLOOR, 'N', -1, DOOR, false, null));
        r.add(new Room("service_stairs", 39, 18, 49, 20, CHECKER, 'N', 47, NONE, true, null));
        r.add(new Room("ward14", 51, 18, 62, 20, LINO_FLOOR, 'N', -1, DOOR, false, "bh.room.descent"));
        return r;
    }

    /** Вырезает комнаты этажа: воздух внутри, свой пол. */
    private void carveFloor(List<Room> rooms, int floorY, int y0, int y1) {
        for (Room r : rooms) {
            for (int x = r.x1(); x <= r.x2(); x++) {
                for (int z = r.z1(); z <= r.z2(); z++) {
                    set(x, floorY, z, floorBlock(r.floor(), x, z));
                    for (int y = y0; y <= y1; y++) set(x, y, z, AIR);
                }
            }
        }
    }

    private BlockState floorBlock(int kind, int x, int z) {
        boolean odd = ((x + z) & 1) == 1;
        return switch (kind) {
            case CHECKER -> odd ? TILE_A : TILE_B;
            case WHITE -> odd ? WHITE_TILE_A : WHITE_TILE_B;
            default -> rnd.nextInt(14) == 0 ? Blocks.PACKED_MUD.defaultBlockState() : LINO;
        };
    }

    /** Кольцо коридоров шириной 3 на обоих этажах. */
    private void corridors() {
        int[][] parts = {{18, 14, 20, 46}, {68, 14, 70, 46}, {18, 14, 70, 16}, {18, 44, 70, 46}};
        for (int[] c : parts) {
            for (int x = c[0]; x <= c[2]; x++) {
                for (int z = c[1]; z <= c[3]; z++) {
                    set(x, -1, z, floorBlock(CHECKER, x, z));
                    set(x, 4, z, floorBlock(CHECKER, x, z));
                    for (int y = 0; y <= 3; y++) set(x, y, z, AIR);
                    for (int y = 5; y <= 8; y++) set(x, y, z, AIR);
                }
            }
        }
    }

    // ---------- главный холл, лестницы, лифт, проломы ----------

    /**
     * Главный холл (x 39..49, z 40..51) в два этажа. Сквозь него идёт коридор (z 44..46): на втором этаже это мост
     * с перилами. Главная лестница вдоль западной стены поднимается с юга на север прямо на мост. Южная дорожка
     * второго этажа ведёт к разбитому окну на козырёк. В северную пустоту можно спрыгнуть через разрыв в перилах.
     */
    private void hall() {
        for (int x = 39; x <= 49; x++) {
            for (int z = 40; z <= 51; z++) {
                set(x, -1, z, ((x / 2 + z / 2) & 1) == 0 ? TILE_A : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                for (int y = 0; y <= 8; y++) set(x, y, z, AIR);
                set(x, 4, z, AIR);
            }
        }
        // Арки коридора в боковых стенах холла (оба этажа).
        for (int z = 44; z <= 46; z++) {
            for (int y = 0; y <= 3; y++) {
                set(38, y, z, AIR);
                set(50, y, z, AIR);
            }
            for (int y = 5; y <= 8; y++) {
                set(38, y, z, AIR);
                set(50, y, z, AIR);
            }
        }
        // Пол второго этажа: мост, восточная полоса, южная дорожка, площадка у лифта.
        int[][] walk = {{39, 44, 49, 46}, {48, 47, 49, 49}, {42, 50, 49, 51}, {48, 43, 49, 43}};
        for (int[] w : walk) fill(w[0], 4, w[1], w[2], 4, w[3], ROOF);
        // Под мостом балки: колонны по краям.
        for (int x : new int[]{42, 46}) {
            for (int y = 0; y <= 3; y++) {
                set(x, y, 43, PILLAR);
                set(x, y, 47, PILLAR);
            }
        }
        // Главная лестница: x 39..41, ступени с z 50 (низ) до z 46 (верх, это уже мост).
        for (int k = 0; k <= 4; k++) {
            int z = 50 - k;
            for (int x = 39; x <= 41; x++) {
                for (int y = 0; y < k; y++) set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                set(x, k, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                for (int y = k + 1; y <= k + 3 && y <= 8; y++) set(x, y, z, AIR);
            }
        }
        // Перила: решётка на краю пустоты рядом с полом второго этажа.
        for (int x = 39; x <= 49; x++) {
            for (int z = 40; z <= 51; z++) {
                if (!get(x, 4, z).isAir()) continue;
                if (x <= 41 && z >= 46) continue;   // над лестницей
                if (x >= 48 && z <= 42) continue;   // лифт
                boolean edge = false;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    int nx = x + d.getStepX(), nz = z + d.getStepZ();
                    if (nx < 39 || nx > 49 || nz < 40 || nz > 51) continue;
                    if (!get(nx, 4, nz).isAir() && !(nx <= 41 && nz >= 46)) edge = true;
                }
                if (edge) setC(x, 5, z, BARS);
            }
        }
        // Разрыв в перилах моста над северной пустотой: отсюда прыгают вниз в холл.
        set(44, 5, 43, AIR);
        set(45, 5, 43, AIR);
        // Окошко регистратуры в холл и стойка под ним.
        setC(38, 1, 49, Blocks.GLASS_PANE.defaultBlockState());
        set(39, 0, 49, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        // Главный вход: арка 3 блока в южной стене.
        fill(43, 0, 52, 45, 2, 52, AIR);
        // Дверь из холла во двор-колодец.
        door(44, 0, 39, Blocks.SPRUCE_DOOR, Direction.NORTH);
    }

    /** Служебная лестница (x 39..49, z 18..20): ступени на восток x 41..45, площадка второго этажа x 46..49. */
    private void serviceStairs() {
        for (int k = 0; k <= 4; k++) {
            int x = 41 + k;
            for (int z = 18; z <= 19; z++) {
                for (int y = 0; y < k; y++) set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                set(x, k, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
                for (int y = k + 1; y <= k + 3 && y <= 8; y++) set(x, y, z, AIR);
            }
        }
        // Лестница шириной 2 (z 18..19), вдоль z 20 на первом этаже проход под ней к двери во двор.
        // Над ступенями перекрытия нет; площадка второго этажа x 46..49 и узкий карниз z 20 остаются.
        for (int x = 41; x <= 44; x++) for (int z = 18; z <= 19; z++) set(x, 4, z, AIR);
        for (int z = 18; z <= 19; z++) setC(40, 5, z, BARS);
        for (int x = 41; x <= 44; x++) setC(x, 5, 20, BARS);
        // Дверь во двор-колодец под площадкой.
        door(47, 0, 21, Blocks.SPRUCE_DOOR, Direction.SOUTH);
    }

    /** Шахта лифта в северо-восточном углу холла: 2 x 2, открыта на первом этаже в холл и на втором к мосту. */
    private void shaft() {
        for (int y = 0; y <= 8; y++) {
            for (int z = 40; z <= 42; z++) set(47, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (int x = 47; x <= 49; x++) set(x, y, 42, Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (int x = 48; x <= 49; x++) for (int z = 40; z <= 41; z++) set(x, y, z, AIR);
        }
        for (int x = 48; x <= 49; x++) for (int z = 40; z <= 41; z++) set(x, 4, z, AIR);
        // Внизу обломки кабины.
        set(48, 0, 40, Blocks.COBBLESTONE_SLAB.defaultBlockState());
        set(49, 0, 41, Blocks.IRON_TRAPDOOR.defaultBlockState());
        setC(48, 0, 41, Blocks.CHAIN.defaultBlockState());
        for (int y = 1; y <= 8; y++) setC(49, y, 40, Blocks.CHAIN.defaultBlockState());
        // Проёмы: на первом этаже в холл (x 47, z 41), на втором к площадке (z 42, x 48).
        set(47, 0, 41, AIR);
        set(47, 1, 41, AIR);
        set(48, 5, 42, AIR);
        set(48, 6, 42, AIR);
    }

    /** Проломы в полу второго этажа: палата 5 (запад) и палата 12 (восток), под ними куча обломков. */
    private void holes() {
        for (int[] h : new int[][]{{22, 23}, {CX - 23, CZ - 24}}) {
            int x = h[0], z = h[1];
            fill(x, 4, z, x + 1, 4, z + 1, AIR);
            set(x, 0, z, GRAVEL);
            set(x + 1, 0, z, Blocks.COBBLESTONE_SLAB.defaultBlockState());
            set(x, 0, z + 1, Blocks.COBBLESTONE_SLAB.defaultBlockState());
            set(x + 1, 3, z + 1, Blocks.COBWEB.defaultBlockState());
            // По краю пролома торчат куски перекрытия.
            set(x - 1, 4, z, Blocks.COBBLESTONE.defaultBlockState());
        }
    }

    // ---------- окна, двери ----------

    /** Окна: внешние стены и стены двора. Первый этаж: решётки или доски, второй: серое стекло или пусто. */
    private void windows() {
        for (int z = 10; z <= 50; z += 3) {
            window(12, z, Direction.WEST);
            window(76, z, Direction.EAST);
        }
        for (int x = 14; x <= 74; x += 3) {
            window(x, 8, Direction.NORTH);
            window(x, 52, Direction.SOUTH);
        }
        for (int x = 27; x <= 61; x += 3) {
            window(x, 21, Direction.SOUTH);
            window(x, 39, Direction.NORTH);
        }
        for (int z = 23; z <= 37; z += 3) {
            window(25, z, Direction.EAST);
            window(63, z, Direction.WEST);
        }
    }

    /** Окно в стене (x, z), если за ним комната или коридор (с обеих сторон не стена); out — наружу. */
    private void window(int x, int z, Direction out) {
        int ix = x - out.getStepX(), iz = z - out.getStepZ();
        for (int floor = 0; floor < 2; floor++) {
            int y = floor == 0 ? 1 : 6;
            if (!get(ix, y, iz).isAir()) continue;                 // внутри стена: окна нет
            if (get(x, y, z).isAir() || get(x, y + 1, z).isAir()) continue;   // здесь уже проём (вход, спуск)
            if (floor == 0) {
                if (rnd.nextInt(10) < 3) {
                    set(x, y, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    set(x, y + 1, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                } else {
                    setC(x, y, z, BARS);
                    setC(x, y + 1, z, BARS);
                }
                // Батарея под окном.
                if (get(ix, 0, iz).isAir()) {
                    set(ix, 0, iz, Blocks.IRON_TRAPDOOR.defaultBlockState()
                            .setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, out.getOpposite()));
                }
            } else {
                int r = rnd.nextInt(10);
                BlockState glass = Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState();
                setC(x, y, z, r < 4 ? AIR : glass);
                setC(x, y + 1, z, r < 2 ? AIR : glass);
            }
        }
    }

    private void doors(List<Room> rooms, int y) {
        for (Room r : rooms) {
            int[] d = doorPos(r);
            int x = d[0], z = d[1];
            Direction out = switch (r.side()) {
                case 'N' -> Direction.NORTH;
                case 'S' -> Direction.SOUTH;
                case 'W' -> Direction.WEST;
                default -> Direction.EAST;
            };
            switch (r.door()) {
                case LOCKED -> door(x, y, z, Blocks.IRON_DOOR, out);
                case OPEN -> {
                    set(x, y, z, AIR);
                    set(x, y + 1, z, AIR);
                    int sx = out.getAxis() == Direction.Axis.X ? 0 : 1, sz = out.getAxis() == Direction.Axis.X ? 1 : 0;
                    set(x + sx, y, z + sz, AIR);
                    set(x + sx, y + 1, z + sz, AIR);
                }
                case NONE -> {
                    set(x, y, z, AIR);
                    set(x, y + 1, z, AIR);
                    int sx = out.getAxis() == Direction.Axis.X ? 0 : 1, sz = out.getAxis() == Direction.Axis.X ? 1 : 0;
                    set(x + sx, y, z + sz, AIR);
                    set(x + sx, y + 1, z + sz, AIR);
                }
                default -> door(x, y, z, Blocks.BIRCH_DOOR, out);
            }
        }
        if (y == 0) {
            // Тамбур к хозблоку и выход в переход.
            door(44, 0, 8, Blocks.DARK_OAK_DOOR, Direction.NORTH);
            // Выходы спавнов на улицу: двойные проёмы с сорванными дверями.
            fill(12, 0, 29, 12, 1, 31, AIR);
            fill(76, 0, 29, 76, 1, 31, AIR);
        }
    }

    /** Точка двери в стене между комнатой и коридором. */
    private static int[] doorPos(Room r) {
        int midX = r.doorAt() >= 0 && (r.side() == 'N' || r.side() == 'S') ? r.doorAt() : (r.x1() + r.x2()) / 2;
        int midZ = r.doorAt() >= 0 && (r.side() == 'W' || r.side() == 'E') ? r.doorAt() : (r.z1() + r.z2()) / 2;
        return switch (r.side()) {
            case 'N' -> new int[]{midX, r.z1() - 1};
            case 'S' -> new int[]{midX, r.z2() + 1};
            case 'W' -> new int[]{r.x1() - 1, midZ};
            default -> new int[]{r.x2() + 1, midZ};
        };
    }

    private void door(int x, int y, int z, Block block, Direction facing) {
        BlockState s = block.defaultBlockState().setValue(DoorBlock.FACING, facing);
        set(x, y, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // ---------- двор-колодец, спуски ----------

    private void courtyard() {
        for (int x = 26; x <= 62; x++) {
            for (int z = 22; z <= 38; z++) {
                for (int y = 0; y <= 14; y++) set(x, y, z, AIR);
                int r = rnd.nextInt(100);
                set(x, -1, z, r < 40 ? Blocks.COARSE_DIRT.defaultBlockState() : r < 70 ? Blocks.DIRT.defaultBlockState() : r < 85 ? Blocks.PODZOL.defaultBlockState() : GRAVEL);
            }
        }
        // Дорожки от двух дверей и вокруг клумбы.
        for (int z = 22; z <= 38; z++) set(44, -1, z, Blocks.DIRT_PATH.defaultBlockState());
        for (int x = 40; x <= 48; x++) {
            set(x, -1, 28, Blocks.DIRT_PATH.defaultBlockState());
            set(x, -1, 32, Blocks.DIRT_PATH.defaultBlockState());
        }
        for (int z = 22; z <= 28; z++) set(47, -1, z, Blocks.DIRT_PATH.defaultBlockState());
        // Разбитая клумба посередине.
        for (int x = 41; x <= 47; x++) {
            set(x, 0, 29, Blocks.STONE_BRICK_SLAB.defaultBlockState());
            set(x, 0, 31, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        }
        set(41, 0, 30, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        set(47, 0, 30, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        for (int x = 42; x <= 46; x++) set(x, 0, 30, Blocks.DEAD_BUSH.defaultBlockState());
        for (int[] t : new int[][]{{30, 25}, {36, 35}, {52, 24}, {58, 35}, {29, 33}, {59, 27}}) deadTree(t[0], t[1]);
        for (int[] l : new int[][]{{34, 23}, {54, 37}}) deadLampPost(l[0], l[1]);
        for (int i = 0; i < 16; i++) {
            int x = 27 + rnd.nextInt(35), z = 23 + rnd.nextInt(15);
            if (get(x, 0, z).isAir() && x != 44) set(x, 0, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
        // Брошенная каталка.
        set(38, 0, 27, Blocks.WHITE_CARPET.defaultBlockState());
        set(39, 0, 27, Blocks.WHITE_CARPET.defaultBlockState());
    }

    /**
     * Спуски со второго этажа (только вниз, без урона, если слезать по шагам):
     * юг: окно галереи (x 45) на козырёк над главным входом, с него на мусорный контейнер и на землю;
     * север: окно операционной (x 43) на крышу крытого перехода, с неё на контейнер;
     * двор: разбитое окно палаты 6 (x 31) и палаты 14 (x 57) на контейнеры во дворе-колодце.
     * Подоконники из стекла (осколки хрустят), на козырьке и переходе железные люки (лязгают).
     */
    private void descents() {
        // Козырёк над главным входом.
        fill(41, 4, 53, 47, 4, 55, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        for (int y = 0; y <= 3; y++) {
            set(41, y, 55, PILLAR);
            set(47, y, 55, PILLAR);
        }
        set(45, 5, 52, AIR);
        set(45, 6, 52, AIR);
        set(45, 4, 52, Blocks.GLASS.defaultBlockState());
        set(44, 4, 53, Blocks.GLASS.defaultBlockState());
        set(45, 4, 53, Blocks.GLASS.defaultBlockState());
        for (int x = 42; x <= 46; x++) for (int z = 54; z <= 55; z++) set(x, 5, z, trapdoorFlat());
        dumpster(48, 54);
        // Крытый переход к хозблоку и его крыша.
        for (int z = 5; z <= 7; z++) {
            set(42, -1, z, PLINTH);
            set(46, -1, z, PLINTH);
            for (int y = 0; y <= 3; y++) {
                set(42, y, z, FACADE);
                set(46, y, z, FACADE);
                for (int x = 43; x <= 45; x++) set(x, y, z, AIR);
            }
            for (int x = 43; x <= 45; x++) set(x, -1, z, floorBlock(CHECKER, x, z));
            for (int x = 42; x <= 46; x++) set(x, 4, z, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            setC(42, 1, z, BARS);
            setC(46, 1, z, BARS);
        }
        set(43, 5, 8, AIR);
        set(43, 6, 8, AIR);
        set(43, 4, 8, Blocks.GLASS.defaultBlockState());
        set(43, 4, 7, Blocks.GLASS.defaultBlockState());
        set(44, 4, 7, Blocks.GLASS.defaultBlockState());
        for (int x = 42; x <= 46; x++) for (int z = 5; z <= 6; z++) set(x, 5, z, trapdoorFlat());
        dumpster(40, 5);
        // Окна во двор-колодец: {окно x, z стены, контейнер x, z, осколки x, z}.
        for (int[] w : new int[][]{{31, 39, 31, 37, 30, 36}, {57, 21, 56, 22, 58, 24}}) {
            set(w[0], 5, w[1], AIR);
            set(w[0], 6, w[1], AIR);
            set(w[0], 4, w[1], Blocks.GLASS.defaultBlockState());
            dumpster(w[2], w[3]);
            setC(w[4], 0, w[5], Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
        }
    }

    /** Закрытый железный люк, лежит плашмя: по нему ходят, он лязгает. */
    private static BlockState trapdoorFlat() {
        return Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(TrapDoorBlock.OPEN, false);
    }

    /** Мусорный контейнер 2 x 2, высотой 2 и крышкой из железного люка: верх на 2.2 блока, назад не залезть. */
    private void dumpster(int x, int z) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                set(x + dx, 0, z + dz, Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
                set(x + dx, 1, z + dz, Blocks.WEATHERED_CUT_COPPER.defaultBlockState());
                set(x + dx, 2, z + dz, trapdoorFlat());
            }
        }
    }

    // ---------- хозблок: котельная, морг ----------

    /**
     * Хозблок за корпусом (x 34..56, z -6..4): котельная с генератором и трубой (x 35..41), коридор хозблока
     * (x 43..45), морг в полуподвале (x 47..55, пол y -4, ступени вниз прямо от двери). Логово Жильца в морге.
     */
    private void outbuilding() {
        for (int x = 34; x <= 56; x++) {
            for (int z = -6; z <= 4; z++) {
                boolean wall = x == 34 || x == 56 || z == -6 || z == 4 || x == 42 || x == 46;
                boolean morgue = x >= 47;
                int top = morgue ? 2 : 5;
                int bottom = morgue || x == 46 ? -4 : -1;
                for (int y = bottom; y <= top; y++) {
                    BlockState s;
                    if (y == top) s = ROOF;
                    else if (y == bottom) s = morgue ? floorBlock(WHITE, x, z) : wall ? PLINTH : floorBlock(CHECKER, x, z);
                    else if (wall) s = y <= 0 ? PLINTH : (rnd.nextInt(12) == 0 ? FACADE_BAND : FACADE);
                    else s = AIR;
                    set(x, y, z, s);
                }
                if (wall && !morgue) set(x, top + 1, z, PARAPET);
            }
        }
        // Внутренние стены котельной и морга выкрашены, как в корпусе.
        for (int y = 0; y <= 1; y++) for (int z = -5; z <= 3; z++) set(42, y, z, PANEL);
        // Двери: переход -> коридор хозблока, коридор -> котельная, коридор -> морг, котельная -> улица.
        door(44, 0, 4, Blocks.DARK_OAK_DOOR, Direction.SOUTH);
        door(42, 0, -1, Blocks.DARK_OAK_DOOR, Direction.WEST);
        door(34, 0, -1, Blocks.DARK_OAK_DOOR, Direction.WEST);
        set(46, 0, -1, AIR);
        set(46, 1, -1, AIR);
        // Ступени в морг: x 47..49 на запад вверх, стоят на полу y -4.
        for (int k = 0; k < 3; k++) {
            int x = 47 + k;
            for (int z = -2; z <= 0; z++) {
                for (int y = -3; y < -1 - k; y++) set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                set(x, -1 - k, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        // Морг: дверцы холодильников (открытые железные люки на восточной стене), стол, кровь, окошки у земли.
        for (int z = -5; z <= 3; z++) {
            if (z == -1) continue;
            for (int y = -3; y <= -2; y++) {
                set(55, y, z, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, Direction.WEST));
            }
        }
        for (int x = 49; x <= 53; x += 4) {
            set(x, 0, -6, AIR);
            setC(x, 0, -6, BARS);
            set(x, 0, 4, AIR);
            setC(x, 0, 4, BARS);
        }
        set(52, -4, 1, BLOOD);
        set(53, -4, 1, BLOOD);
        set(52, -4, 2, BLOOD);
        for (int i = 0; i < 4; i++) set(48 + rnd.nextInt(7), 1, -5 + rnd.nextInt(9), Blocks.COBWEB.defaultBlockState());
        // Котельная: два котла, трубы, труба наружу.
        for (int z = -5; z <= -4; z++) {
            set(36, 0, z, Blocks.BLAST_FURNACE.defaultBlockState());
            set(36, 1, z, Blocks.BLAST_FURNACE.defaultBlockState());
        }
        for (int x = 36; x <= 40; x++) set(x, 4, -5, Blocks.POLISHED_BASALT.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        for (int y = 2; y <= 3; y++) set(36, y, -4, Blocks.POLISHED_BASALT.defaultBlockState());
        for (int y = 6; y <= 16; y++) {
            for (int x = 37; x <= 38; x++) for (int z = -3; z <= -2; z++) set(x, y, z, y > 14 && rnd.nextBoolean() ? AIR : Blocks.BRICKS.defaultBlockState());
        }
        for (int x = 37; x <= 38; x++) for (int z = -3; z <= -2; z++) set(x, 5, z, Blocks.BRICKS.defaultBlockState());
        set(35, 0, 1, Blocks.COAL_BLOCK.defaultBlockState());
        set(35, 0, 2, Blocks.COAL_BLOCK.defaultBlockState());
        set(35, 1, 2, Blocks.COAL_BLOCK.defaultBlockState());
    }

    // ---------- улица ----------

    private void frontYard() {
        // Дорожка от ворот к главному входу и площадка у крыльца.
        for (int z = 53; z <= 61; z++) for (int x = 43; x <= 45; x++) set(x, -1, z, GRAVEL);
        fill(41, -1, 53, 47, -1, 55, Blocks.STONE_BRICKS.defaultBlockState());
        for (int x = 42; x <= 46; x++) set(x, 0, 53, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        // Будка охраны у ворот.
        for (int x = 50; x <= 54; x++) {
            for (int z = 55; z <= 59; z++) {
                boolean wall = x == 50 || x == 54 || z == 55 || z == 59;
                set(x, -1, z, LINO);
                for (int y = 0; y <= 2; y++) set(x, y, z, wall ? (y == 0 ? PLINTH : FACADE) : AIR);
                set(x, 3, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            }
        }
        door(50, 0, 57, Blocks.BIRCH_DOOR, Direction.WEST);
        setC(52, 1, 59, Blocks.GLASS_PANE.defaultBlockState());
        setC(54, 1, 57, Blocks.GLASS_PANE.defaultBlockState());
        setC(52, 1, 55, AIR);
        // Голые деревья, кусты, мёртвые фонари.
        for (int[] t : new int[][]{{4, 50}, {10, 58}, {28, 56}, {62, 58}, {80, 52}, {84, 44}, {4, 4}, {10, -4}, {24, -5}, {64, -5}, {78, -3}, {84, 6}, {3, 14}, {85, 16}, {20, 59}, {70, 56}})
            deadTree(t[0], t[1]);
        for (int[] l : new int[][]{{20, 56}, {36, 58}, {60, 54}, {8, 8}, {80, 10}, {6, 44}, {82, 16}, {68, 1}})
            deadLampPost(l[0], l[1]);
        for (int i = 0; i < 60; i++) {
            int x = 1 + rnd.nextInt(CX - 1), z = -7 + rnd.nextInt(69);
            boolean inBuilding = x >= 11 && x <= 77 && z >= 7 && z <= 53;
            boolean inOut = x >= 33 && x <= 57 && z >= -7 && z <= 8;
            if (inBuilding || inOut || !get(x, 0, z).isAir()) continue;
            set(x, 0, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    private void deadLampPost(int x, int z) {
        for (int y = 0; y <= 3; y++) setC(x, y, z, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        set(x, 4, z, Blocks.REDSTONE_LAMP.defaultBlockState());
    }

    /** Запад: скорая у навеса приёмного покоя. Восток: грузовая рампа кухни. Дорожки к калиткам выходов. */
    private void sideYards() {
        for (int x = 1; x <= 11; x++) for (int z = 29; z <= 31; z++) set(x, -1, z, GRAVEL);
        for (int x = 77; x <= 87; x++) for (int z = 29; z <= 31; z++) set(x, -1, z, GRAVEL);
        // Навес у входа приёмного покоя.
        for (int x = 8; x <= 11; x++) for (int z = 24; z <= 36; z++) set(x, 4, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        for (int z : new int[]{24, 36}) for (int y = 0; y <= 3; y++) setC(8, y, z, BARS);
        ambulance();
        // Грузовая рампа (пара скорой по луту).
        fill(80, 0, 27, 85, 1, 33, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        for (int z = 27; z <= 33; z++) set(79, 0, z, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        for (int x = 80; x <= 85; x++) setC(x, 2, 27, Blocks.IRON_BARS.defaultBlockState());
        set(84, 2, 32, Blocks.BARREL.defaultBlockState());
        set(85, 2, 32, Blocks.BARREL.defaultBlockState());
        set(85, 3, 32, Blocks.BARREL.defaultBlockState());
    }

    /**
     * Брошенная скорая («буханка»): кузов 3 x 6 x 3 вдоль z (x 4..6, z 28..33), кабина на юг.
     * Белая терракота, блёклая полоса из красной терракоты, крыша из полублоков кварца, мигалка из меди,
     * лобовое стекло с выбитым куском, фары из железных люков, одно колесо спущено, задняя дверь открыта.
     */
    private void ambulance() {
        BlockState white = Blocks.WHITE_TERRACOTTA.defaultBlockState();
        BlockState stripe = Blocks.RED_TERRACOTTA.defaultBlockState();
        BlockState rust = Blocks.BROWN_TERRACOTTA.defaultBlockState();
        BlockState wheel = Blocks.COAL_BLOCK.defaultBlockState();
        set(4, 0, 29, wheel);
        set(6, 0, 29, wheel);
        set(4, 0, 32, wheel);
        set(6, 0, 32, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());   // спущенное колесо
        for (int x = 4; x <= 6; x++) {
            for (int z = 28; z <= 33; z++) {
                boolean side = x == 4 || x == 6;
                set(x, 1, z, side ? (z == 28 || rnd.nextInt(6) == 0 ? rust : stripe) : white);
                set(x, 2, z, side || z == 33 ? white : AIR);
                set(x, 3, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            }
        }
        // Кабина: лобовое стекло, сиденье, фары, бампер.
        setC(4, 2, 33, Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
        setC(5, 2, 33, AIR);
        setC(6, 2, 33, Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
        setC(4, 2, 31, Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
        setC(6, 2, 31, Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
        set(5, 2, 32, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        for (int x : new int[]{4, 6}) set(x, 1, 34, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, Direction.SOUTH));
        set(5, 0, 34, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        // Носилки внутри и открытая задняя дверь, крест на ней.
        set(5, 2, 29, Blocks.WHITE_CARPET.defaultBlockState());
        set(5, 2, 28, AIR);
        set(6, 2, 28, stripe);
        // Мигалка, не горит.
        set(5, 3, 31, Blocks.OXIDIZED_CUT_COPPER_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
    }

    // ---------- разруха ----------

    /** Паутина по углам, обломки в коридорах, листья у окон, несколько пятен крови. */
    private void ruin() {
        for (int i = 0; i < 140; i++) {
            int x = 13 + rnd.nextInt(63), z = 9 + rnd.nextInt(43);
            int y = rnd.nextBoolean() ? 3 : 8;
            if (get(x, y, z).isAir() && !get(x, y + 1, z).isAir() && touchesWall(x, y, z)) set(x, y, z, Blocks.COBWEB.defaultBlockState());
        }
        for (int i = 0; i < 90; i++) {
            int x = 13 + rnd.nextInt(63), z = 9 + rnd.nextInt(43);
            int y = rnd.nextBoolean() ? 0 : 5;
            if (!get(x, y, z).isAir() || get(x, y - 1, z).isAir()) continue;
            if (!touchesWall(x, y, z)) continue;
            int r = rnd.nextInt(4);
            set(x, y, z, r == 0 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : r == 1 ? Blocks.ANDESITE_SLAB.defaultBlockState()
                    : r == 2 ? Blocks.WHITE_CARPET.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
        }
        // Кровь: изолятор, операционная, у перевязочной. Не больше трёх мест в корпусе.
        set(20, -1, 10, BLOOD);
        set(21, -1, 10, BLOOD);
        set(44, 4, 11, BLOOD);
        set(45, 4, 11, BLOOD);
        set(30, -1, 42, BLOOD);
    }

    private boolean touchesWall(int x, int y, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!get(x + d.getStepX(), y, z + d.getStepZ()).isAir()) return true;
        }
        return false;
    }

    // ---------- свет ----------

    /** Лампы под потолком: коридоры, спавны, холл. Пары при повороте на 180°, как точки лута. */
    private void lamps() {
        BlockState lamp = ModBlocks.LAMP.get().defaultBlockState();
        int[][] half = {{19, 15}, {19, 23}, {19, 31}, {19, 39}, {28, 15}, {36, 15}, {52, 15}, {60, 15},
                {14, 30}, {23, 24}, {24, 10}, {44, 49}, {33, 50}};
        for (int[] h : half) {
            symLamp(h[0], 3, h[1], lamp);
            symLamp(h[0], 8, h[1], lamp);
        }
    }

    private void symLamp(int x, int y, int z, BlockState lamp) {
        if (get(x, y, z).isAir() && !get(x, y + 1, z).isAir()) {
            set(x, y, z, lamp);
            lampCount++;
        }
        int mx = CX - x, mz = CZ - z;
        if ((mx != x || mz != z) && get(mx, y, mz).isAir() && !get(mx, y + 1, mz).isAir()) {
            set(mx, y, mz, lamp);
            lampCount++;
        }
    }

    /** Аварийные лампы: над генератором, на полу в центре холла, под потолком над мостом галереи. */
    private void emergencyLamps() {
        BlockState e = ModBlocks.EMERGENCY_LAMP.get().defaultBlockState();
        set(40, 4, -4, e);
        set(44, 0, 48, e.setValue(EmergencyLampBlock.FLOOR, true));
        set(44, 8, 45, e);
    }

    // ---------- таблички ----------

    /** Табличка у двери каждой комнаты, в коридоре, на высоте глаз. */
    private void signs(List<Room> rooms, int y) {
        for (Room r : rooms) {
            int[] d = doorPos(r);
            Direction out = switch (r.side()) {
                case 'N' -> Direction.NORTH;
                case 'S' -> Direction.SOUTH;
                case 'W' -> Direction.WEST;
                default -> Direction.EAST;
            };
            // Табличка висит на стене снаружи комнаты, рядом с дверью (сдвиг вдоль стены на 2 блока).
            int sx = out.getAxis() == Direction.Axis.X ? 0 : 2, sz = out.getAxis() == Direction.Axis.X ? 2 : 0;
            int x = d[0] + out.getStepX() + sx, z = d[1] + out.getStepZ() + sz;
            if (!get(x, y + 2, z).isAir() || get(d[0] + sx, y + 2, d[1] + sz).isAir()) {
                x = d[0] + out.getStepX() - sx;
                z = d[1] + out.getStepZ() - sz;
                if (!get(x, y + 2, z).isAir() || get(d[0] - sx, y + 2, d[1] - sz).isAir()) {
                    // Снаружи места нет: табличка внутри комнаты над дверью.
                    x = d[0] - out.getStepX();
                    z = d[1] - out.getStepZ();
                    wallSign(x, y + 2, z, out.getOpposite(), "bh.room." + r.id(), r.line2() ? "bh.room." + r.id() + ".2" : null, r.extra());
                    continue;
                }
            }
            wallSign(x, y + 2, z, out, "bh.room." + r.id(), r.line2() ? "bh.room." + r.id() + ".2" : null, r.extra());
        }
    }

    /** Таблички без дверей: холл, хозблок, двор, шахта, проломы, щитки, будка, скорая. */
    private void extraSigns() {
        wallSign(39, 2, 51, Direction.NORTH, "bh.room.hall", null, null);
        wallSign(46, 2, 41, Direction.WEST, "bh.room.shaft", null, null);
        wallSign(41, 2, -5, Direction.SOUTH, "bh.room.boiler", null, "bh.room.generator");
        wallSign(45, 2, -5, Direction.SOUTH, "bh.room.outbuilding", null, null);
        wallSign(45, 2, 3, Direction.NORTH, "bh.room.morgue", "bh.room.morgue.2", "bh.room.tenant");
        wallSign(53, 1, 56, Direction.SOUTH, "bh.room.booth", null, null);
        wallSign(44, 2, 5, Direction.SOUTH, "bh.room.passage", null, null);
        wallSign(18, 2, 40, Direction.EAST, "bh.room.breaker_a", null, null);
        wallSign(70, 2, 20, Direction.WEST, "bh.room.breaker_b", null, null);
        wallSign(44, 7, 51, Direction.NORTH, "bh.room.descent", "bh.room.descent_canopy", null);
        wallSign(44, 7, 9, Direction.SOUTH, "bh.room.descent", "bh.room.descent_roof", null);
        standingSign(23, 5, 25, 8, "bh.room.hole", null);
        standingSign(CX - 23, 5, CZ - 25, 0, "bh.room.hole", null);
        standingSign(2, 0, 28, 4, "bh.room.exit_a", null);
        standingSign(CX - 2, 0, 32, 12, "bh.room.exit_b", null);
        standingSign(9, 0, 30, 4, "bh.room.ambulance", null);
        standingSign(78, 0, 26, 12, "bh.room.ramp", null);
    }

    private void wallSign(int x, int y, int z, Direction facing, String key1, String key2, String key3) {
        if (!get(x, y, z).isAir()) return;
        BlockState s = Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
        set(x, y, z, s);
        text(x, y, z, key1, key2, key3);
    }

    private void standingSign(int x, int y, int z, int rotation, String key1, Object arg) {
        if (!get(x, y, z).isAir()) return;
        set(x, y, z, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
        BlockEntity be = level.getBlockEntity(p(x, y, z));
        if (be instanceof SignBlockEntity sign) {
            Component line = arg == null ? Component.translatable(key1) : Component.translatable(key1, arg);
            SignText t = new SignText().setColor(DyeColor.LIGHT_GRAY).setMessage(1, line);
            sign.setText(t, true);
            sign.setChanged();
        }
    }

    private void text(int x, int y, int z, String key1, String key2, String key3) {
        BlockEntity be = level.getBlockEntity(p(x, y, z));
        if (!(be instanceof SignBlockEntity sign)) return;
        SignText t = new SignText().setColor(DyeColor.LIGHT_GRAY).setMessage(key2 == null && key3 == null ? 1 : 0, Component.translatable(key1));
        if (key2 != null) t = t.setMessage(1, Component.translatable(key2));
        if (key3 != null) t = t.setMessage(key2 == null ? 1 : 2, Component.translatable(key3));
        sign.setText(t, true);
        sign.setChanged();
    }

    // ---------- точки матча ----------

    private MapData points() {
        BlockState breaker = ModBlocks.BREAKER.get().defaultBlockState();
        set(18, 1, 41, breaker.setValue(PowerSwitchBlock.FACING, Direction.EAST));
        set(CX - 18, 1, CZ - 41, breaker.setValue(PowerSwitchBlock.FACING, Direction.WEST));
        set(40, 0, -4, ModBlocks.GENERATOR.get().defaultBlockState());

        MapData map = MapData.get(level);
        map.spawns(BhTeam.ALPHA).clear();
        map.spawns(BhTeam.BRAVO).clear();
        map.lootPoints().clear();
        map.tapePoints().clear();
        for (int z : new int[]{26, 30, 34}) {
            for (int x : new int[]{14, 15}) {
                map.spawns(BhTeam.ALPHA).add(p(x, 0, z));
                map.spawns(BhTeam.BRAVO).add(p(CX - x, 0, CZ - z));
            }
        }
        // Пары лута: точка A и её отражение у B. На каждой точке табличка «Лут, пара N» (убрать после декора).
        int[][] loot = {
                {15, 0, 15}, {23, 0, 27}, {24, 0, 50}, {5, 2, 30}, {34, 0, 50},
                {24, 5, 50}, {56, 5, 50}, {52, 0, 57}, {15, 5, 26}, {24, 0, 10}
        };
        for (int i = 0; i < loot.length; i++) {
            int[] l = loot[i];
            map.lootPoints().add(p(l[0], l[1], l[2]));
            map.lootPoints().add(p(CX - l[0], l[1], CZ - l[2]));
            set(l[0], l[1], l[2], AIR);
            set(CX - l[0], l[1], CZ - l[2], AIR);
            standingSign(l[0], l[1], l[2], 0, "bh.room.loot", i + 1);
            standingSign(CX - l[0], l[1], CZ - l[2], 8, "bh.room.loot", i + 1);
        }
        map.setPairedLoot(true);

        BlockPos alphaExit = p(2, 0, 30);
        BlockPos bravoExit = p(CX - 2, 0, 30);
        BlockState marker = ModBlocks.EXIT_MARKER.get().defaultBlockState();
        level.setBlock(alphaExit, marker, 3);
        level.setBlock(bravoExit, marker, 3);
        map.setExit(BhTeam.ALPHA, alphaExit);
        map.setExit(BhTeam.BRAVO, bravoExit);

        // Кассета: 12 мест на средней линии (x около 44) между выходами. Пока там бочки: пользователь ставит на их место
        // свою мебель, после этого точки ставятся заново командой /bh tape add.
        int[][] tape = {
                {51, -3, -4}, {51, -3, 2}, {38, 0, 2}, {44, 0, -3}, {44, 0, 6}, {41, 5, 10},
                {47, 5, 10}, {38, 0, 30}, {50, 0, 30}, {44, 0, 41}, {46, 5, 51}, {44, 0, 57}};
        for (int[] t : tape) {
            set(t[0], t[1] + 1, t[2], AIR);
            set(t[0], t[1], t[2], Blocks.BARREL.defaultBlockState());
            map.tapePoints().add(p(t[0], t[1], t[2]));
        }
        map.setTenantHome(p(53, -3, -3));
        map.setDirty();
        return map;
    }

    // ---------- помощники ----------

    /** Пересчёт соединений решёток, стёкол и заборов после постройки. */
    private void reconnect() {
        for (BlockPos pos : connect) {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) continue;
            BlockState u = Block.updateFromNeighbourShapes(s, level, pos);
            if (u != s) level.setBlock(pos, u, 2 | 16);
        }
    }

    private BlockPos p(int x, int y, int z) {
        return base.offset(x, y, z + 8);
    }

    private BlockState get(int x, int y, int z) {
        return level.getBlockState(p(x, y, z));
    }

    private void set(int x, int y, int z, BlockState s) {
        level.setBlock(p(x, y, z), s, 2);
    }

    /** То же, но блок запоминается для пересчёта соединений (решётки, стёкла, стены, цепи). */
    private void setC(int x, int y, int z, BlockState s) {
        set(x, y, z, s);
        connect.add(p(x, y, z));
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }
}
