package com.talviom.bodycamhorror.arena;

import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import com.talviom.bodycamhorror.sabotage.PowerSwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Карта «Комплекс»: закрытое здание 49x33 в три уровня (подвал, первый этаж, галерея), только планировка, без мебели.
 *
 * Карта симметрична поворотом на 180° вокруг центра: всё, что есть у Alpha (запад), есть и у Bravo (восток),
 * поэтому стороны равны. Спавны в торцах первого этажа, выходы в углах за спавнами.
 * В центре двусветный зал: с галереи можно спрыгнуть вниз (6 блоков). Между этажами две лестницы наверх
 * и две вниз, в подвал, где стоит генератор. Щитки на первом этаже, у каждой стороны свой.
 * Точки лута идут зеркальными парами (в обе кладётся одно и то же), точки кассеты лежат на линии,
 * равноудалённой от обоих выходов.
 *
 * Высоты относительно base (пол первого этажа, на нём стоят): подвал y -5..-2, первый этаж y 0..4, галерея y 6..9.
 */
public final class FacilityBuilder {
    static final int W = 48;
    static final int D = 32;
    private static final int LOBBY_GAP = 6;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WALL = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_LOW = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState FLOOR_G = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState FLOOR_U = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState FLOOR_B = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();

    private final ServerLevel level;
    private final BlockPos base;

    private FacilityBuilder(ServerLevel level, BlockPos base) {
        this.level = level;
        this.base = base;
    }

    public static Component build(ServerLevel level, BlockPos base) {
        return new FacilityBuilder(level, base).run();
    }

    private Component run() {
        shell();
        ground();
        upper();
        basement();
        stairs();
        int lamps = lamps();
        MapData map = points();
        Component lobby = LobbyBuilder.build(level, base.offset(-(LOBBY_GAP + 15), 0, 0));
        return Component.translatable("bh.cmd.facility_built", lamps, map.lootPoints().size(), map.tapePoints().size(), lobby);
    }

    // ---------- коробка и этажи ----------

    private void shell() {
        fill(0, -6, 0, W, 10, D, WALL);
        fill(1, -5, 1, W - 1, -2, D - 1, AIR);   // подвал
        fill(1, 0, 1, W - 1, 4, D - 1, AIR);     // первый этаж
        fill(1, 6, 1, W - 1, 9, D - 1, AIR);     // галерея
        fill(1, -6, 1, W - 1, -6, D - 1, FLOOR_B);
        fill(1, -1, 1, W - 1, -1, D - 1, FLOOR_G);
        fill(1, 5, 1, W - 1, 5, D - 1, FLOOR_U);
        fill(1, 10, 1, W - 1, 10, D - 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
    }

    /** Первый этаж: торцевые залы со спавнами, северный и южный коридоры, две боковые комнаты и центральный зал. */
    private void ground() {
        // Стены торцевых залов (x = 9 и зеркально x = 39) с тремя проёмами.
        wallX(9, 1, D - 1, 0, 4, new int[][]{{3, 5}, {15, 17}, {27, 29}});
        // Стены центрального зала (x = 17 и x = 31) с проёмом посередине.
        wallX(17, 7, 25, 0, 4, new int[][]{{15, 17}});
        // Стены коридоров (z = 7 и z = 25) между торцами, с проёмами в комнаты и в зал.
        wallZ(7, 9, 39, 0, 4, new int[][]{{12, 14}, {23, 25}, {34, 36}});
        // Невысокие укрытия в торцевых залах (по пояс), чтобы спавн не простреливался насквозь.
        sym(5, 0, 9, 6, 0, 10, WALL_LOW);
        sym(5, 0, 22, 6, 0, 23, WALL_LOW);
        // Колонны в центральном зале держат галерею.
        for (int[] c : new int[][]{{20, 10}, {20, 22}, {28, 10}, {28, 22}}) sym(c[0], 0, c[1], c[0], 4, c[1], PILLAR);
    }

    /** Галерея: кольцо вокруг проёма над центральным залом, комнаты над коридорами и торцами. */
    private void upper() {
        wallX(9, 1, D - 1, 6, 9, new int[][]{{3, 5}, {15, 17}, {27, 29}});
        wallZ(7, 9, 39, 6, 9, new int[][]{{12, 14}, {23, 25}, {34, 36}});
        // Проём в полу над залом и перила по его краю.
        fill(19, 5, 10, 29, 5, 22, AIR);
        for (int x = 19; x <= 29; x++) {
            set(x, 6, 9, BARS);
            set(x, 6, 23, BARS);
        }
        for (int z = 9; z <= 23; z++) {
            set(18, 6, z, BARS);
            set(30, 6, z, BARS);
        }
        // Разрыв в перилах: отсюда можно спрыгнуть в зал (по одному с каждой стороны).
        sym(18, 6, 15, 18, 6, 17, AIR);
        // Колонны продолжаются до потолка галереи.
        for (int[] c : new int[][]{{20, 10}, {20, 22}, {28, 10}, {28, 22}}) sym(c[0], 5, c[1], c[0], 9, c[1], PILLAR);
    }

    /** Подвал: три тоннеля, разделённые стенами с проёмами, в центре генераторная. */
    private void basement() {
        wallX(16, 1, D - 1, -5, -2, new int[][]{{4, 6}, {15, 17}, {26, 28}});
        // Колонны в генераторной.
        for (int[] c : new int[][]{{20, 8}, {20, 24}, {28, 8}, {28, 24}}) sym(c[0], -5, c[1], c[0], -2, c[1], PILLAR);
        // Перегородки, превращающие боковые тоннели в лабиринт.
        sym(8, -5, 10, 8, -2, 22, WALL);
        sym(8, -5, 15, 8, -2, 17, AIR);
    }

    // ---------- лестницы ----------

    /**
     * Наверх: северный коридор у Alpha, x 11..16, ступени на восток. Вниз: северный коридор у Bravo, x 31..35,
     * спуск на восток. Обе зеркально повторены у другой стороны (южный коридор).
     */
    private void stairs() {
        // Первый этаж → галерея: 6 ступеней, шириной 3 (z 2..4).
        for (int k = 0; k < 6; k++) {
            int x = 11 + k;
            for (int z = 2; z <= 4; z++) {
                stair(x, k, z, Direction.EAST);
                for (int y = k + 1; y <= k + 3; y++) symSet(x, y, z, AIR);
            }
        }
        // Первый этаж → подвал: 5 ступеней вниз на восток (x 31..35), под ними пусто до пола подвала.
        for (int k = 0; k < 5; k++) {
            int x = 31 + k;
            for (int z = 2; z <= 4; z++) {
                for (int y = -5; y <= 3; y++) symSet(x, y, z, AIR);
                stair(x, -1 - k, z, Direction.WEST);
                for (int y = -5; y < -1 - k; y++) symSet(x, y, z, WALL);
            }
        }
        // Площадка внизу спуска свободна, выход в подвал.
        for (int z = 2; z <= 4; z++) for (int y = -5; y <= -2; y++) symSet(36, y, z, AIR);
    }

    // ---------- свет ----------

    private int lamps() {
        BlockState lamp = ModBlocks.LAMP.get().defaultBlockState();
        int n = 0;
        int[][] ground = {{4, 4}, {4, 16}, {4, 28}, {13, 16}, {20, 4}, {28, 4}};
        int[][] upper = {{4, 8}, {4, 24}, {13, 12}, {13, 20}, {24, 4}, {24, 16}};
        int[][] basement = {{8, 4}, {12, 16}, {24, 4}};
        for (int[] p : ground) n += symLamp(p[0], 4, p[1], lamp);
        for (int[] p : upper) n += symLamp(p[0], 9, p[1], lamp);
        for (int[] p : basement) n += symLamp(p[0], -2, p[1], lamp);
        return n;
    }

    private int symLamp(int x, int y, int z, BlockState lamp) {
        set(x, y, z, lamp);
        if (W - x == x && D - z == z) return 1;
        set(W - x, y, D - z, lamp);
        return 2;
    }

    // ---------- точки матча ----------

    private MapData points() {
        BlockState breaker = ModBlocks.BREAKER.get().defaultBlockState();
        set(10, 1, 12, breaker.setValue(PowerSwitchBlock.FACING, Direction.EAST));
        set(W - 10, 1, D - 12, breaker.setValue(PowerSwitchBlock.FACING, Direction.WEST));
        set(24, -5, 16, ModBlocks.GENERATOR.get().defaultBlockState());

        MapData map = MapData.get(level);
        map.spawns(BhTeam.ALPHA).clear();
        map.spawns(BhTeam.BRAVO).clear();
        map.lootPoints().clear();
        map.tapePoints().clear();
        for (int z : new int[]{13, 16, 19}) {
            map.spawns(BhTeam.ALPHA).add(p(4, 0, z));
            map.spawns(BhTeam.BRAVO).add(p(W - 4, 0, D - z));
        }
        // Зеркальные пары: сначала точка Alpha, потом её отражение у Bravo.
        int[][] loot = {
                {13, 0, 11}, {13, 0, 21}, {22, 0, 4}, {12, 0, 29},
                {5, 6, 8}, {5, 6, 24}, {13, 6, 16}, {22, 6, 4},
                {6, -5, 5}, {6, -5, 27}, {12, -5, 16}, {20, -5, 16}
        };
        for (int[] l : loot) {
            map.lootPoints().add(p(l[0], l[1], l[2]));
            map.lootPoints().add(p(W - l[0], l[1], D - l[2]));
        }
        map.setPairedLoot(true);

        BlockPos alphaExit = p(2, 0, 2);
        BlockPos bravoExit = p(W - 2, 0, D - 2);
        BlockState marker = ModBlocks.EXIT_MARKER.get().defaultBlockState();
        level.setBlock(alphaExit, marker, 3);
        level.setBlock(bravoExit, marker, 3);
        map.setExit(BhTeam.ALPHA, alphaExit);
        map.setExit(BhTeam.BRAVO, bravoExit);

        // Кассета: на перпендикуляре к середине отрезка между выходами (одинаково далеко от обоих), на всех уровнях.
        // 0.9: 12 точек (по 4 на этаж), в каждой мебель из Macaw's Furniture, кассета 3D-блоком лежит на ней.
        int[][] tape = {
                {29, 0, 9}, {19, 0, 23}, {31, 0, 6}, {17, 0, 26},
                {30, 6, 5}, {18, 6, 27}, {33, 6, 3}, {15, 6, 29},
                {26, -5, 13}, {22, -5, 19}, {29, -5, 9}, {19, -5, 23}};
        String[] furniture = {
                "mcwfurnitures:dark_oak_end_table", "mcwfurnitures:dark_oak_end_table", "mcwfurnitures:dark_oak_drawer", "mcwfurnitures:dark_oak_drawer",
                "mcwfurnitures:spruce_desk", "mcwfurnitures:spruce_desk", "mcwfurnitures:spruce_double_drawer", "mcwfurnitures:spruce_double_drawer",
                "mcwfurnitures:dark_oak_counter", "mcwfurnitures:dark_oak_counter", "mcwfurnitures:spruce_drawer", "mcwfurnitures:spruce_drawer"};
        for (int i = 0; i < tape.length; i++) {
            int[] t = tape[i];
            set(t[0], t[1], t[2], furniture(furniture[i]));
            map.tapePoints().add(p(t[0], t[1], t[2]));
        }
        map.setDirty();
        return map;
    }

    // ---------- помощники ----------

    /** Мебель из Macaw's Furniture; без этого мода (dev-клиент) вместо неё бочка, на ней кассета лежит так же. */
    private static BlockState furniture(String id) {
        net.minecraft.world.level.block.Block b = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(id));
        if (b == null || b == Blocks.AIR) return Blocks.BARREL.defaultBlockState();
        return b.defaultBlockState();
    }

    private BlockPos p(int x, int y, int z) {
        return base.offset(x, y, z);
    }

    private void set(int x, int y, int z, BlockState s) {
        level.setBlock(p(x, y, z), s, 2);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }

    /** Прямоугольник и его отражение поворотом на 180° вокруг центра. */
    private void sym(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        fill(x0, y0, z0, x1, y1, z1, s);
        fill(W - x0, y0, D - z0, W - x1, y1, D - z1, s);
    }

    private void symSet(int x, int y, int z, BlockState s) {
        set(x, y, z, s);
        set(W - x, y, D - z, s);
    }

    /** Ступень и её отражение (у отражения ступень смотрит в обратную сторону). */
    private void stair(int x, int y, int z, Direction up) {
        BlockState st = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        set(x, y, z, st.setValue(StairBlock.FACING, up));
        set(W - x, y, D - z, st.setValue(StairBlock.FACING, up.getOpposite()));
    }

    /** Стена вдоль Z на x (и зеркально), с проёмами {z0, z1}. */
    private void wallX(int x, int z0, int z1, int y0, int y1, int[][] doors) {
        for (int z = z0; z <= z1; z++) {
            boolean door = false;
            for (int[] d : doors) door |= z >= d[0] && z <= d[1];
            for (int y = y0; y <= y1; y++) symSet(x, y, z, door ? AIR : WALL);
        }
    }

    /** Стена вдоль X на z (и зеркально), с проёмами {x0, x1}. */
    private void wallZ(int z, int x0, int x1, int y0, int y1, int[][] doors) {
        for (int x = x0; x <= x1; x++) {
            boolean door = false;
            for (int[] d : doors) door |= x >= d[0] && x <= d[1];
            for (int y = y0; y <= y1; y++) symSet(x, y, z, door ? AIR : WALL);
        }
    }
}
