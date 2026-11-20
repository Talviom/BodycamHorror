package com.talviom.bodycamhorror.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 0.9: союзник вышел в эфир (on=true) или отпустил рацию. Клиент играет щелчок и показывает значок. */
public class RadioCuePacket {
    final boolean on;

    public RadioCuePacket(boolean on) {
        this.on = on;
    }

    public static void encode(RadioCuePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.on);
    }

    public static RadioCuePacket decode(FriendlyByteBuf buf) {
        return new RadioCuePacket(buf.readBoolean());
    }

    public static void handle(RadioCuePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.talviom.bodycamhorror.client.RadioClient.onTeammateCue(msg.on)));
        ctx.get().setPacketHandled(true);
    }
}
