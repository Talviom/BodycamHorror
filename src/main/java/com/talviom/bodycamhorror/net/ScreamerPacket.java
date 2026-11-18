package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.client.TenantFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент: скример Жильца (вспышка, тряска кадра, сильные помехи). */
public class ScreamerPacket {
    private final int ticks;

    public ScreamerPacket(int ticks) {
        this.ticks = ticks;
    }

    public static void encode(ScreamerPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.ticks);
    }

    public static ScreamerPacket decode(FriendlyByteBuf buf) {
        return new ScreamerPacket(buf.readVarInt());
    }

    public static void handle(ScreamerPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TenantFx.start(m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
