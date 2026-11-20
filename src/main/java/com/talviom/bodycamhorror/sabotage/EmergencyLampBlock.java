package com.talviom.bodycamhorror.sabotage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 0.9: аварийная лампа. Тёмная, пока работает генератор, и тускло светит красным (свет 5), когда он выключен.
 * Щитки на неё не действуют, Жильца она не держит, разбить её нельзя (прочность как у бедрока).
 *
 * Своего учёта в SabotageData у неё нет: лампа сама раз в полсекунды смотрит, выключен ли генератор
 * (Sabotage.generatorOff), через запланированный тик, который сохраняется в чанке вместе с миром.
 * FLOOR: стоит на полу (в центре зала), иначе висит под потолком.
 */
public class EmergencyLampBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty FLOOR = BooleanProperty.create("floor");
    private static final int CHECK_TICKS = 10;
    private static final VoxelShape CEILING_SHAPE = Block.box(4.5, 10, 4.5, 11.5, 16, 11.5);
    private static final VoxelShape FLOOR_SHAPE = Block.box(4.5, 0, 4.5, 11.5, 6, 11.5);

    public EmergencyLampBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FLOOR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FLOOR);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FLOOR) ? FLOOR_SHAPE : CEILING_SHAPE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean lit = Sabotage.generatorOff(level);
        if (state.getValue(LIT) != lit) level.setBlock(pos, state.setValue(LIT, lit), 3);
        level.scheduleTick(pos, this, CHECK_TICKS);
    }
}
