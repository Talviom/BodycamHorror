package com.talviom.bodycamhorror.flashlight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Невидимый блок света от фонарика. Свой, а не minecraft:light, чтобы он убирал себя сам:
 * каждые полсекунды блок проверяет, светит ли сюда ещё чей-то фонарик, и если нет, исчезает.
 * Запланированная проверка сохраняется вместе с чанком, поэтому даже после вылета сервера
 * блок при следующей загрузке чанка уберёт себя, и вечных светящихся пятен на карте не останется.
 * Блоки света, которые поставил строитель карты (minecraft:light), фонарик не трогает.
 */
public class BeamLightBlock extends Block {
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
    private static final int CHECK_TICKS = 10;

    public BeamLightBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 8));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LEVEL);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Flashlight.ownsLight(level, pos)) level.scheduleTick(pos, this, CHECK_TICKS);
        else level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return true;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
