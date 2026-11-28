package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.gear.ModGear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Лобби: старый граммофон с трубой (до 2026-11-28 был магнитофон, id блока остался tape_recorder, чтобы не ломать миры).
 * Пока PLAYING и матч не идёт, из него играет музыка лобби (звук у каждого игрока свой, client/music/RecorderMusic),
 * пластинка крутится (client/GramophoneRenderer). Щелчок ставит на паузу или включает.
 * Модель и текстуры: tools/assets/make_gramophone.py.
 */
public class TapeRecorderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");
    /** Ящик и труба над ним (раструб чуть выходит за блок, но коробки хватает, чтобы по нему кликать). */
    private static final VoxelShape SHAPE = Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);

    public TapeRecorderBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PLAYING, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean playing = !state.getValue(PLAYING);
            level.setBlock(pos, state.setValue(PLAYING, playing), 3);
            // Иглу опускают или поднимают: тихий деревянный щелчок.
            level.playSound(null, pos, playing ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvents.WOODEN_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.45f, playing ? 1.2f : 0.9f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TapeRecorderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != ModGear.TAPE_RECORDER_BE.get()) return null;
        return (lvl, pos, st, be) -> {
            com.talviom.bodycamhorror.client.music.RecorderMusic.tick(lvl, pos, st);
            if (be instanceof TapeRecorderBlockEntity g) g.spinTick(st);
        };
    }
}
