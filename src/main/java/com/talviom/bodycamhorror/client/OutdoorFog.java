package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0: туман на улице (карта «Больница»). Когда над головой открытое небо, туман за 1-2 секунды сгущается:
 * видно метров на 18-20, дальше почти чёрная серо-костяная муть. Под крышей он так же плавно уходит.
 *
 * Без шейдера туман ставит сама игра (RenderFog). С шейдером FOOTAGE_bh та же сила передаётся в uniform bhFog
 * (см. ShaderState и tools/shaders/patch_footage.py), шейдер рисует туман сам.
 * В творческом режиме и в наблюдателе тумана нет, чтобы было удобно строить и смотреть.
 */
public final class OutdoorFog {
    private static final float NEAR = 1.5f;
    private static final float FAR = 19f;
    private static final float R = 0.035f, G = 0.034f, B = 0.031f;

    private static float strength;
    private static float strengthPrev;

    private OutdoorFog() {}

    /** 0..1, сглаженная между тиками. */
    public static float strength() {
        return Mth.lerp(Minecraft.getInstance().getFrameTime(), strengthPrev, strength);
    }

    private static boolean outdoors(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        if (mc.level.dimension() != Level.OVERWORLD) return false;
        if (mc.player.isCreative() || mc.player.isSpectator()) return false;
        return mc.level.canSeeSky(BlockPos.containing(mc.player.getEyePosition()));
    }

    private static void tick() {
        strengthPrev = strength;
        float target = outdoors(Minecraft.getInstance()) ? 1f : 0f;
        strength += Mth.clamp(target - strength, -0.035f, 0.035f);
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class OutdoorFogEvents {
        @SubscribeEvent
        public static void onOutdoorFogTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) tick();
        }

        @SubscribeEvent
        public static void onOutdoorFogRender(ViewportEvent.RenderFog e) {
            float f = strength();
            if (f <= 0.001f) return;
            e.setNearPlaneDistance(Mth.lerp(f, e.getNearPlaneDistance(), NEAR));
            e.setFarPlaneDistance(Mth.lerp(f, e.getFarPlaneDistance(), FAR));
            e.setCanceled(true);
        }

        @SubscribeEvent
        public static void onOutdoorFogColor(ViewportEvent.ComputeFogColor e) {
            float f = strength();
            if (f <= 0.001f) return;
            e.setRed(Mth.lerp(f, e.getRed(), R));
            e.setGreen(Mth.lerp(f, e.getGreen(), G));
            e.setBlue(Mth.lerp(f, e.getBlue(), B));
        }
    }
}
