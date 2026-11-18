package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.gear.ModGear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Хранит только место плаката; картинку рисует клиент (PosterRenderer). */
public class PosterBlockEntity extends BlockEntity {
    public PosterBlockEntity(BlockPos pos, BlockState state) {
        super(ModGear.POSTER_BE.get(), pos, state);
    }

    /** Плакат больше блока: без этого его бы обрезало, когда сам блок вне кадра. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(PosterBlock.WIDTH + 1, PosterBlock.HEIGHT + 1, PosterBlock.WIDTH + 1);
    }
}
