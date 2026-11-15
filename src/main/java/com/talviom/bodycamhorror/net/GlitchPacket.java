package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер просит клиент показать помехи бодикама. */
public class GlitchPacket {
    private final int ticks;
    private final float strength;

    public GlitchPacket(int ticks, float strength) {
        this.ticks = ticks;
        this.strength = strength;
    }

    public static void encode(GlitchPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ticks);
        buf.writeFloat(msg.strength);
    }

    public static GlitchPacket decode(FriendlyByteBuf buf) {
        return new GlitchPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(GlitchPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientEffects.startGlitch(msg.ticks, msg.strength)));
        ctx.get().setPacketHandled(true);
    }
}
