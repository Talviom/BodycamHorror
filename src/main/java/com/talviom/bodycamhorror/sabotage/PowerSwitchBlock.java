package com.talviom.bodycamhorror.sabotage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Щиток (свет в радиусе) или генератор (свет на всей карте).
 * Правый клик переключает питание. Заодно даёт сигнал редстоуна, пока включён.
 */
public class PowerSwitchBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** true: генератор, действует на всю карту. false: щиток, действует в радиусе. */
    public final boolean global;

    public PowerSwitchBlock(Properties props, boolean global) {
        super(props);
        this.global = global;
        registerDefaultState(stateDefinition.any().setValue(POWERED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        String message = Sabotage.tryToggle((ServerLevel) level, pos, state, this);
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
