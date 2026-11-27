package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.match.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент → сервер: погибший переключает камеру союзника (ЛКМ вперёд, ПКМ назад). */
public class SpectateCyclePacket {
    private final int dir;

    public SpectateCyclePacket(int dir) {
        this.dir = dir;
    }

    public static void encode(SpectateCyclePacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.dir);
    }

    public static SpectateCyclePacket decode(FriendlyByteBuf buf) {
        return new SpectateCyclePacket(buf.readByte());
    }

    public static void handle(SpectateCyclePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) MatchManager.INSTANCE.cycleSpectate(sender, msg.dir >= 0 ? 1 : -1);
        });
        ctx.get().setPacketHandled(true);
    }
}
