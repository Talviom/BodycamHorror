package com.talviom.bodycamhorror.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → все, кто видит Жильца: прыжок без плавного перемещения. Обычно клиент сглаживает движение
 * существа за 3 тика, и прыжок выглядел бы как скольжение. Здесь позиция ставится сразу, как скачок кадра.
 */
public class TenantSnapPacket {
    private final int id;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;

    public TenantSnapPacket(int id, double x, double y, double z, float yaw) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public static void encode(TenantSnapPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.yaw);
    }

    public static TenantSnapPacket decode(FriendlyByteBuf buf) {
        return new TenantSnapPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public static void handle(TenantSnapPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.snap(m)));
        ctx.get().setPacketHandled(true);
    }

    private static final class Client {
        static void snap(TenantSnapPacket m) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity e = mc.level.getEntity(m.id);
            if (e == null) return;
            e.moveTo(m.x, m.y, m.z, m.yaw, 0);
            e.setYHeadRot(m.yaw);
            e.setYBodyRot(m.yaw);
            if (e instanceof net.minecraft.world.entity.LivingEntity le) le.lerpTo(m.x, m.y, m.z, m.yaw, 0, 1, true);
        }
    }
}
