package com.talviom.bodycamhorror.objective;

import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.SpecimenCarrierPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/** 0.9.8: рассылает всем, кто несёт колбу (чтобы она была видна у него за спиной). */
public final class SpecimenSync {
    private SpecimenSync() {}

    public static void carrier(MinecraftServer server, @Nullable ServerPlayer carrier) {
        BhNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new SpecimenCarrierPacket(carrier == null ? -1 : carrier.getId()));
    }
}
