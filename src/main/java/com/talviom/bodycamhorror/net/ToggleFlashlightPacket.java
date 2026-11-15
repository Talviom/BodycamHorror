package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.flashlight.Flashlight;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент нажал клавишу фонарика. */
public class ToggleFlashlightPacket {
    public static void encode(ToggleFlashlightPacket msg, FriendlyByteBuf buf) {}

    public static ToggleFlashlightPacket decode(FriendlyByteBuf buf) {
        return new ToggleFlashlightPacket();
    }

    public static void handle(ToggleFlashlightPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) Flashlight.toggle(sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
