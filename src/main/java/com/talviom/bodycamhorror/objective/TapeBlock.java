package com.talviom.bodycamhorror.objective;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 0.9 (пункт 16): кассета, лежащая в мире, это блок с 3D-моделью VHS-кассеты, а не плоский предмет на полу.
 * Лежит на мебели (точки кассеты) или на полу, куда её уронили (смерть несущего, выброс на G).
 * Подобрать: ПКМ пустой рукой или F с оружием в руках (клавиша взаимодействия TACZ, тег interact_key/whitelist).
 * Сама по себе блок ничего не роняет и не ломается: забрать её можно только подбором.
 */
public class TapeBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NS = Block.box(3, 0, 5, 13, 2, 11);
    private static final VoxelShape EW = Block.box(5, 0, 3, 11, 2, 13);

    public TapeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction f = state.getValue(FACING);
        return f.getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) TapeObjective.INSTANCE.pickUpBlock(sp, pos);
        return InteractionResult.CONSUME;
    }
}
