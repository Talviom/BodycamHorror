package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.client.DeathFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: экран гаснет как старый ЭЛТ-монитор.
 * SELF: игрок погиб в матче (полный эффект, около 3 с). CAM: погиб союзник, за которым он смотрел (около 1 с).
 */
public class DeathFxPacket {
    public static final int SELF = 0;
    public static final int CAM = 1;

    private final int kind;

    public DeathFxPacket(int kind) {
        this.kind = kind;
    }

    public static void encode(DeathFxPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.kind);
    }

    public static DeathFxPacket decode(FriendlyByteBuf buf) {
        return new DeathFxPacket(buf.readByte());
    }

    public static void handle(DeathFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> DeathFx.start(msg.kind == CAM)));
        ctx.get().setPacketHandled(true);
    }
}
