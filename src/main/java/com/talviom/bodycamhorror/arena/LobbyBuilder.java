package com.talviom.bodycamhorror.arena;

import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Простое лобби: тёмная бетонная комната 13x9 с тусклым светом.
 * У дальней стены две площадки, Alpha и Bravo: кто на них встанет, попадает в команду.
 * Игроки появляются у входа лицом к площадкам. Лобби становится точкой появления мира.
 */
public final class LobbyBuilder {
    private static final int W = 14;
    private static final int D = 10;
    private static final int H = 4;

    private LobbyBuilder() {}

    /** @param base угол лобби на уровне пола (блок, на котором стоят игроки, находится под ним) */
    public static String build(ServerLevel level, BlockPos base) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState wall = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState ceiling = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = 0; x <= W; x++) {
            for (int z = 0; z <= D; z++) {
                boolean outer = x == 0 || x == W || z == 0 || z == D;
                level.setBlock(base.offset(x, -1, z), floor, 3);
                for (int dy = 0; dy < H; dy++) level.setBlock(base.offset(x, dy, z), outer ? wall : air, 3);
                level.setBlock(base.offset(x, H, z), ceiling, 3);
            }
        }

        // Площадки команд 3x3 в полу: приглушённые синий и кирпично-красный.
        // Игрок появляется лицом на юг (+Z), значит слева от него большие X: Alpha слева, Bravo справа.
        BlockPos alphaPad = base.offset(10, 0, 7);
        BlockPos bravoPad = base.offset(4, 0, 7);
        pad(level, alphaPad, Blocks.BLUE_TERRACOTTA.defaultBlockState());
        pad(level, bravoPad, Blocks.RED_TERRACOTTA.defaultBlockState());

        // Приглушённый невидимый свет под потолком, отдельно над каждой площадкой, и пара свечей у стены.
        // В лобби полутьма, но площадки и таблички видно (на тесте 0.6.0 было слишком темно).
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9);
        level.setBlock(base.offset(3, H - 1, 3), light, 3);
        level.setBlock(base.offset(W - 3, H - 1, 3), light, 3);
        level.setBlock(base.offset(W / 2, H - 1, 7), light, 3);
        BlockState padLight = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 8);
        level.setBlock(alphaPad.above(H - 1), padLight, 3);
        level.setBlock(bravoPad.above(H - 1), padLight, 3);
        BlockState candles = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
        level.setBlock(base.offset(1, 0, D - 1), candles, 3);
        level.setBlock(base.offset(W - 1, 0, D - 1), candles, 3);

        // Таблички на дальней стене над площадками.
        sign(level, base.offset(10, 2, D - 1), "ALPHA", "stand here");
        sign(level, base.offset(4, 2, D - 1), "BRAVO", "stand here");
        sign(level, base.offset(W / 2, 2, D - 1), "THE HOST", "starts the tape", "/bh start");

        BlockPos spawn = base.offset(W / 2, 0, 2);
        MapData map = MapData.get(level);
        map.setLobby(spawn);
        map.setLobbyPad(BhTeam.ALPHA, alphaPad);
        map.setLobbyPad(BhTeam.BRAVO, bravoPad);
        level.setDefaultSpawnPos(spawn, 0f);

        return "Lobby built at " + spawn.toShortString() + " (team pads, world spawn moved here)";
    }

    private static void pad(ServerLevel level, BlockPos center, BlockState block) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.setBlock(center.offset(dx, -1, dz), block, 3);
        }
    }

    private static void sign(ServerLevel level, BlockPos pos, String... lines) {
        BlockState state = Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH);
        level.setBlock(pos, state, 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(DyeColor.LIGHT_GRAY).setHasGlowingText(true);
            for (int i = 0; i < lines.length && i < 4; i++) text = text.setMessage(i + (lines.length < 3 ? 1 : 0), Component.literal(lines[i]));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
}
