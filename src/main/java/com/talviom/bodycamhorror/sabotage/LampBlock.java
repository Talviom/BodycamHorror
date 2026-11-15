package com.talviom.bodycamhorror.sabotage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Потолочный светильник, которым управляют щитки, генератор и хоррор-режиссёр. */
public class LampBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Плоский светильник под потолком: 14 в длину, 6 в ширину, 2 в высоту. */
    private static final VoxelShape SHAPE = Block.box(1, 14, 5, 15, 16, 11);

    public LampBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel sl && !state.is(oldState.getBlock())) Sabotage.registerLamp(sl, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel sl && !state.is(newState.getBlock())) Sabotage.unregisterLamp(sl, pos);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
