package com.talviom.bodycamhorror.arena;

import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Тестовая арена: закрытое здание 41x41 из 16 комнат с лампами, двумя щитками и генератором.
 * Нужна, чтобы проверить матч целиком, пока настоящая карта не построена.
 * Сразу расставляет спавны команд и точки лута.
 */
public final class ArenaBuilder {
    private static final int SIZE = 40;
    private static final int HEIGHT = 4;
    private static final int ROOM = 10;

    private ArenaBuilder() {}

    /** @param base угол арены на уровне пола (блок, на котором стоят игроки, находится под ним) */
    public static String build(ServerLevel level, BlockPos base) {
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState ceiling = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = 0; x <= SIZE; x++) {
            for (int z = 0; z <= SIZE; z++) {
                boolean outer = x == 0 || x == SIZE || z == 0 || z == SIZE;
                boolean inner = !outer && (x % ROOM == 0 || z % ROOM == 0) && !isDoor(x, z);
                level.setBlock(base.offset(x, -1, z), floor, 3);
                for (int dy = 0; dy < HEIGHT; dy++) {
                    level.setBlock(base.offset(x, dy, z), outer || inner ? wall : air, 3);
                }
                level.setBlock(base.offset(x, HEIGHT, z), ceiling, 3);
            }
        }

        // Лампа в потолке каждой комнаты.
        BlockState lamp = ModBlocks.LAMP.get().defaultBlockState();
        int lamps = 0;
        for (int i = 0; i < SIZE / ROOM; i++) {
            for (int j = 0; j < SIZE / ROOM; j++) {
                level.setBlock(base.offset(ROOM / 2 + ROOM * i, HEIGHT, ROOM / 2 + ROOM * j), lamp, 3);
                lamps++;
            }
        }

        // Два щитка в противоположных углах и генератор у центра.
        level.setBlock(base.offset(1, 0, 5), ModBlocks.BREAKER.get().defaultBlockState(), 3);
        level.setBlock(base.offset(SIZE - 1, 0, SIZE - 5), ModBlocks.BREAKER.get().defaultBlockState(), 3);
        level.setBlock(base.offset(22, 0, 22), ModBlocks.GENERATOR.get().defaultBlockState(), 3);

        // Точки матча: команды в противоположных углах, лут в центре каждой комнаты.
        MapData map = MapData.get(level);
        map.spawns(BhTeam.ALPHA).clear();
        map.spawns(BhTeam.BRAVO).clear();
        map.lootPoints().clear();
        for (int i = 0; i < 3; i++) {
            map.spawns(BhTeam.ALPHA).add(base.offset(3 + i * 2, 0, 3));
            map.spawns(BhTeam.BRAVO).add(base.offset(SIZE - 3 - i * 2, 0, SIZE - 3));
        }
        for (int i = 0; i < SIZE / ROOM; i++) {
            for (int j = 0; j < SIZE / ROOM; j++) {
                map.lootPoints().add(base.offset(ROOM / 2 + ROOM * i, 0, ROOM / 2 + ROOM * j));
            }
        }
        map.setDirty();

        return "Арена построена: " + lamps + " ламп, 2 щитка, генератор, по 3 спавна на команду, "
                + map.lootPoints().size() + " точек лута";
    }

    /** Проёмы шириной 3 блока посередине каждой внутренней стены. */
    private static boolean isDoor(int x, int z) {
        int mx = x % ROOM;
        int mz = z % ROOM;
        if (mx == 0 && mz == 0) return false;
        if (mx == 0) return mz >= 4 && mz <= 6;
        if (mz == 0) return mx >= 4 && mx <= 6;
        return false;
    }
}
