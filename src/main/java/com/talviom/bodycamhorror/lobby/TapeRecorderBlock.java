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
 * Лобби: старый кассетный магнитофон. Пока PLAYING и матч не идёт, из него играет музыка лобби «с плёнки»
 * (звук у каждого игрока свой, client/music/RecorderMusic). Щелчок по магнитофону ставит на паузу или включает.
 * Модель и текстуры: tools/assets/make_recorder.py.
 */
public class TapeRecorderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");
    private static final VoxelShape[] SHAPES = {
            Block.box(1, 0, 5, 15, 8, 11), // north / south
            Block.box(5, 0, 1, 11, 8, 15)  // east / west
    };

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
        return SHAPES[state.getValue(FACING).getAxis() == Direction.Axis.Z ? 0 : 1];
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
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, playing ? 1.3f : 0.9f);
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
        return (lvl, pos, st, be) -> com.talviom.bodycamhorror.client.music.RecorderMusic.tick(lvl, pos, st);
    }
}
