package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.inventory.BhInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент нажал клавишу инвентаря: сервер открывает наш экран на 20 слотов. */
public class OpenInventoryPacket {
    public static void encode(OpenInventoryPacket msg, FriendlyByteBuf buf) {}

    public static OpenInventoryPacket decode(FriendlyByteBuf buf) {
        return new OpenInventoryPacket();
    }

    public static void handle(OpenInventoryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) BhInventoryMenu.open(sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
