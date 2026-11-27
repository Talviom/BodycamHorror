package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.gear.ModGear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Нужен только ради тика на клиенте: он запускает музыку магнитофона (RecorderMusic). Данных не хранит. */
public class TapeRecorderBlockEntity extends BlockEntity {
    public TapeRecorderBlockEntity(BlockPos pos, BlockState state) {
        super(ModGear.TAPE_RECORDER_BE.get(), pos, state);
    }
}
