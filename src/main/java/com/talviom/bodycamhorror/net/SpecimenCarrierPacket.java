package com.talviom.bodycamhorror.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 0.9.8: id сущности игрока, который несёт «ОБРАЗЕЦ №7», или -1. Клиент рисует колбу у него за спиной. */
public class SpecimenCarrierPacket {
    final int entityId;

    public SpecimenCarrierPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(SpecimenCarrierPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId + 1);
    }

    public static SpecimenCarrierPacket decode(FriendlyByteBuf buf) {
        return new SpecimenCarrierPacket(buf.readVarInt() - 1);
    }

    public static void handle(SpecimenCarrierPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.talviom.bodycamhorror.client.SpecimenBackLayer.setCarrier(msg.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
