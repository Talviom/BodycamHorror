package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.client.Banners;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент: показать VHS-заставку (начало раунда, старт, итог раунда или матча). */
public class BannerPacket {
    public enum Kind { ROUND_START, GO, ROUND_WON, ROUND_LOST, DRAW, MATCH_WON, MATCH_LOST, STOPPED }

    public final Kind kind;
    public final int round;
    public final int scoreMine;
    public final int scoreTheirs;
    /** Название своей команды (ALPHA или BRAVO), пусто для зрителя. */
    public final String team;
    public final boolean swapped;
    /** Сколько тиков показывать (для ROUND_START это длина заморозки). */
    public final int ticks;

    public BannerPacket(Kind kind, int round, int scoreMine, int scoreTheirs, String team, boolean swapped, int ticks) {
        this.kind = kind;
        this.round = round;
        this.scoreMine = scoreMine;
        this.scoreTheirs = scoreTheirs;
        this.team = team;
        this.swapped = swapped;
        this.ticks = ticks;
    }

    public static void encode(BannerPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.kind);
        buf.writeVarInt(m.round);
        buf.writeVarInt(m.scoreMine);
        buf.writeVarInt(m.scoreTheirs);
        buf.writeUtf(m.team, 32);
        buf.writeBoolean(m.swapped);
        buf.writeVarInt(m.ticks);
    }

    public static BannerPacket decode(FriendlyByteBuf buf) {
        return new BannerPacket(buf.readEnum(Kind.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(32), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(BannerPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Banners.show(m)));
        ctx.get().setPacketHandled(true);
    }
}
