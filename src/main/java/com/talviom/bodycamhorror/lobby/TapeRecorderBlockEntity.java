package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.gear.ModGear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Граммофон лобби (id блока прежний, tape_recorder). Данных не хранит: тик на клиенте запускает музыку
 * (RecorderMusic) и крутит пластинку, которую рисует client/GramophoneRenderer.
 */
public class TapeRecorderBlockEntity extends BlockEntity {
    /** 78 оборотов в минуту, как у шеллаковой пластинки: 360 * 78 / 60 / 20 градусов за тик. */
    private static final float DEG_PER_TICK = 23.4f;
    /** Угол пластинки на клиенте, градусы; prevSpin для плавности между тиками. */
    public float spin, prevSpin;

    public TapeRecorderBlockEntity(BlockPos pos, BlockState state) {
        super(ModGear.TAPE_RECORDER_BE.get(), pos, state);
    }

    /** Клиент, каждый тик: пластинка крутится, пока граммофон играет. */
    public void spinTick(BlockState state) {
        prevSpin = spin;
        if (state.getValue(TapeRecorderBlock.PLAYING)) {
            spin += DEG_PER_TICK;
            if (spin >= 360f) {
                spin -= 360f;
                prevSpin -= 360f;
            }
        }
    }
}
