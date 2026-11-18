package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.client.TenantFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент жертвы: эффекты атаки Жильца. */
public class ScreamerPacket {
    /** Скример у лица: вспышка, камера рывком на его лицо. */
    public static final int FLASH = 0;
    /** Прыжок: скачок кадра на плёнке. */
    public static final int BLINK = 1;
    /** Удушье: камера заперта на лице, экран краснеет, нельзя двигаться и стрелять. */
    public static final int STRANGLE = 2;
    /** Затихание после удушья: чернота, красное медленно уходит. */
    public static final int QUIET = 3;
    /** Союзник отбил: эффекты удушья снимаются сразу. */
    public static final int FREED = 4;

    private final int kind;
    private final int ticks;

    public ScreamerPacket(int kind, int ticks) {
        this.kind = kind;
        this.ticks = ticks;
    }

    public static void encode(ScreamerPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeVarInt(m.ticks);
    }

    public static ScreamerPacket decode(FriendlyByteBuf buf) {
        return new ScreamerPacket(buf.readByte(), buf.readVarInt());
    }

    public static void handle(ScreamerPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TenantFx.on(m.kind, m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
