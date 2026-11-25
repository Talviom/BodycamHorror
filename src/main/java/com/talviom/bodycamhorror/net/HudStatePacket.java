package com.talviom.bodycamhorror.net;

import com.talviom.bodycamhorror.client.ClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент, 4 раза в секунду: страх, заряд фонарика и флаги для HUD. */
public class HudStatePacket {
    public final float fear;
    public final float battery;
    public final boolean hasFlashlight;
    public final boolean flashlightOn;
    /** Луч светит прямо сейчас (не мигнул, не погас от помех). */
    public final boolean flashlightLit;
    public final boolean inMatch;
    public final boolean dark;
    public final boolean combat;
    /** Погиб в идущем матче и смотрит камеры союзников (интерфейс наблюдения, DeathFx). */
    public final boolean matchSpectator;

    public HudStatePacket(float fear, float battery, boolean hasFlashlight, boolean flashlightOn, boolean flashlightLit,
                          boolean inMatch, boolean dark, boolean combat) {
        this(fear, battery, hasFlashlight, flashlightOn, flashlightLit, inMatch, dark, combat, false);
    }

    public HudStatePacket(float fear, float battery, boolean hasFlashlight, boolean flashlightOn, boolean flashlightLit,
                          boolean inMatch, boolean dark, boolean combat, boolean matchSpectator) {
        this.matchSpectator = matchSpectator;
        this.fear = fear;
        this.battery = battery;
        this.hasFlashlight = hasFlashlight;
        this.flashlightOn = flashlightOn;
        this.flashlightLit = flashlightLit;
        this.inMatch = inMatch;
        this.dark = dark;
        this.combat = combat;
    }

    public static void encode(HudStatePacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.fear);
        buf.writeFloat(m.battery);
        int flags = (m.hasFlashlight ? 1 : 0) | (m.flashlightOn ? 2 : 0) | (m.inMatch ? 4 : 0)
                | (m.dark ? 8 : 0) | (m.combat ? 16 : 0) | (m.flashlightLit ? 32 : 0) | (m.matchSpectator ? 64 : 0);
        buf.writeByte(flags);
    }

    public static HudStatePacket decode(FriendlyByteBuf buf) {
        float fear = buf.readFloat();
        float battery = buf.readFloat();
        int f = buf.readByte();
        return new HudStatePacket(fear, battery, (f & 1) != 0, (f & 2) != 0, (f & 32) != 0, (f & 4) != 0, (f & 8) != 0, (f & 16) != 0,
                (f & 64) != 0);
    }

    public static void handle(HudStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientState.onHud(m)));
        ctx.get().setPacketHandled(true);
    }
}
