package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.radio.RadioState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 0.9: клиент нажал (talking=true) или отпустил клавишу рации Z. Союзникам приходит щелчок рации (RadioCuePacket),
 * чтобы было слышно, что кто-то вышел в эфир, даже если он ещё молчит.
 */
public class RadioTalkPacket {
    private final boolean talking;

    public RadioTalkPacket(boolean talking) {
        this.talking = talking;
    }

    public static void encode(RadioTalkPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.talking);
    }

    public static RadioTalkPacket decode(FriendlyByteBuf buf) {
        return new RadioTalkPacket(buf.readBoolean());
    }

    public static void handle(RadioTalkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !RadioState.setTalking(sender.getUUID(), msg.talking)) return;
            MatchManager m = MatchManager.INSTANCE;
            BhTeam team = m.teamOf(sender);
            if (team == null) return;
            for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
                if (p == sender || m.teamOf(p) != team || p.isSpectator() || !p.isAlive()) continue;
                BhNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RadioCuePacket(msg.talking));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
