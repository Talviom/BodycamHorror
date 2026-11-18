package com.talviom.bodycamhorror.sabotage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Щиток (свет в радиусе) или генератор (свет на всей карте).
 * Правый клик переключает питание. Заодно даёт сигнал редстоуна, пока включён.
 * Щиток висит на стене: FACING показывает, куда смотрит его дверца.
 */
public class PowerSwitchBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape GENERATOR_SHAPE = Block.box(1, 0, 1, 15, 14, 15);
    private static final VoxelShape BREAKER_NORTH = Block.box(3, 2, 13, 13, 14, 16);
    private static final VoxelShape BREAKER_SOUTH = Block.box(3, 2, 0, 13, 14, 3);
    private static final VoxelShape BREAKER_WEST = Block.box(13, 2, 3, 16, 14, 13);
    private static final VoxelShape BREAKER_EAST = Block.box(0, 2, 3, 3, 14, 13);

    /** true: генератор, действует на всю карту. false: щиток, действует в радиусе. */
    public final boolean global;

    public PowerSwitchBlock(Properties props, boolean global) {
        super(props);
        this.global = global;
        registerDefaultState(stateDefinition.any().setValue(POWERED, true).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (global) return GENERATOR_SHAPE;
        return switch (state.getValue(FACING)) {
            case SOUTH -> BREAKER_SOUTH;
            case WEST -> BREAKER_WEST;
            case EAST -> BREAKER_EAST;
            default -> BREAKER_NORTH;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        // Щиток вешается на ту стену, по которой кликнули. Генератор поворачивается лицом к игроку.
        Direction facing = !global && face.getAxis().isHorizontal()
                ? face
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        String message = Sabotage.tryToggle((ServerLevel) level, pos, state, this);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.talviom.bodycamhorror.match.MatchReport.switchUsed(sp.server, sp, message);
        }
        player.displayClientMessage(Component.literal(message), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel sl && !state.is(oldState.getBlock())) Sabotage.registerSwitch(sl, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel sl && !state.is(newState.getBlock())) Sabotage.unregisterSwitch(sl, pos);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
