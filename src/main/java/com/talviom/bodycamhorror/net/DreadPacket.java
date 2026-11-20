package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.client.DreadFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент одного игрока: хоррор-ивенты 0.9, которые видит только он (камера, фигуры). */
public class DreadPacket {
    /** Часы камеры скачут на 03:33 или в 1998 год, потом возвращаются. */
    public static final int TIMECODE = 0;
    /** Перемотка: плёнку дёргает назад, полосы трекинга, звук перемотки. */
    public static final int REWIND = 1;
    /** Силуэт Жильца в конце коридора, исчезает вблизи или когда моргнёт свет. */
    public static final int SILHOUETTE = 2;

    private final int kind;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final int ticks;

    public DreadPacket(int kind, double x, double y, double z, float yaw, int ticks) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.ticks = ticks;
    }

    public static void encode(DreadPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.yaw);
        buf.writeVarInt(m.ticks);
    }

    public static DreadPacket decode(FriendlyByteBuf buf) {
        return new DreadPacket(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(DreadPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> DreadFx.on(m.kind, m.x, m.y, m.z, m.yaw, m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
