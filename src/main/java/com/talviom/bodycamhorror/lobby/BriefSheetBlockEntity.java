package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.gear.ModGear;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Что нарисовать на листе. topic: часть lang-ключа (bh.brief.&lt;topic&gt;...). Размеры и сдвиги в блоках
 * от левого нижнего угла клетки (если смотреть на лист), tilt: наклон в градусах (записки висят криво).
 */
public class BriefSheetBlockEntity extends BlockEntity {
    public static final int MEMO = 0;
    public static final int NOTE = 1;
    public static final int PHOTO = 2;
    public static final int PLAN = 3;
    public static final int MONITOR = 4;
    public static final int LABEL = 5;

    public String topic = "";
    public int style;
    public float x;
    public float y;
    public float w = 1f;
    public float h = 1f;
    public float tilt;

    public BriefSheetBlockEntity(BlockPos pos, BlockState state) {
        super(ModGear.BRIEF_SHEET_BE.get(), pos, state);
    }

    public void set(String topic, int style, float x, float y, float w, float h, float tilt) {
        this.topic = topic;
        this.style = style;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.tilt = tilt;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("topic", topic);
        tag.putInt("style", style);
        tag.putFloat("x", x);
        tag.putFloat("y", y);
        tag.putFloat("w", w);
        tag.putFloat("h", h);
        tag.putFloat("tilt", tilt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        topic = tag.getString("topic");
        style = tag.getInt("style");
        x = tag.getFloat("x");
        y = tag.getFloat("y");
        w = tag.contains("w") ? tag.getFloat("w") : 1f;
        h = tag.contains("h") ? tag.getFloat("h") : 1f;
        tilt = tag.getFloat("tilt");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Лист больше блока: без этого его бы обрезало, когда сам блок вне кадра. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(Math.max(w, h) + 1);
    }
}
