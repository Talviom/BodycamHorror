package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class BhNetwork {
    /** Меняется при изменении пакетов: клиент и сервер разных версий мода не соединятся. */
    private static final String PROTOCOL = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BodycamHorror.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private BhNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(0, GlitchPacket.class, GlitchPacket::encode, GlitchPacket::decode, GlitchPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, ToggleFlashlightPacket.class, ToggleFlashlightPacket::encode, ToggleFlashlightPacket::decode,
                ToggleFlashlightPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, HudStatePacket.class, HudStatePacket::encode, HudStatePacket::decode,
                HudStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, BannerPacket.class, BannerPacket::encode, BannerPacket::decode,
                BannerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, OpenInventoryPacket.class, OpenInventoryPacket::encode, OpenInventoryPacket::decode,
                OpenInventoryPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendGlitch(ServerPlayer player, int ticks, float strength) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GlitchPacket(ticks, strength));
    }

    public static void sendHud(ServerPlayer player, HudStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendBanner(ServerPlayer player, BannerPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
