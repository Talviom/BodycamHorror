package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Клиент: значения, которые мод передаёт в шейдер (через Oculus, см. compat/oculus).
 * Здесь нет ни одного класса Oculus, поэтому без шейдеров класс просто считает числа впустую.
 *
 * bhFlashlight 0..1 — яркость своего нагрудного фонарика: включён, не мигнул, учтён заряд.
 *   Лампа накаливания разгорается и гаснет не мгновенно, на слабой батарейке луч проваливается.
 * bhFear 0..1 — сглаженный страх игрока, им шейдер темнит края и «дышит» экспозицией.
 */
public final class ShaderState {
    private static final Random RND = new Random();

    private static float flashlight;
    private static float flashlightPrev;
    private static float fear;
    private static float fearPrev;
    private static int dropTicks;
    private static float emergency;
    private static float emergencyPrev;
    private static float emergencyTarget;
    private static int scanTicks;
    private static final int EMERGENCY_RADIUS = 9;

    private ShaderState() {}

    /** 0.9: 0..1, насколько близко горящая аварийная лампа (в прямой видимости). Шейдер красит ею тени в красный. */
    public static float emergency() {
        return Mth.lerp(partial(), emergencyPrev, emergency);
    }

    private static float scanEmergency(Minecraft mc) {
        net.minecraft.world.level.Level level = mc.level;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.core.BlockPos c = mc.player.blockPosition();
        float best = 0f;
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -EMERGENCY_RADIUS; dx <= EMERGENCY_RADIUS; dx++) {
            for (int dy = -EMERGENCY_RADIUS; dy <= EMERGENCY_RADIUS; dy++) {
                for (int dz = -EMERGENCY_RADIUS; dz <= EMERGENCY_RADIUS; dz++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof com.talviom.bodycamhorror.sabotage.EmergencyLampBlock)
                            || !s.getValue(com.talviom.bodycamhorror.sabotage.EmergencyLampBlock.LIT)) continue;
                    net.minecraft.world.phys.Vec3 lamp = net.minecraft.world.phys.Vec3.atCenterOf(p);
                    float k = 1f - (float) (lamp.distanceTo(eye) / (EMERGENCY_RADIUS + 1));
                    if (k <= best) continue;
                    // За стеной красного не видно: луч от глаз до лампы не должен упираться в блок раньше неё.
                    net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                            eye, lamp, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && !hit.getBlockPos().equals(p)) continue;
                    best = k;
                }
            }
        }
        return Mth.clamp(best * 1.4f, 0f, 1f);
    }

    /** Значения для текущего кадра, сглаженные между тиками. */
    public static float flashlight() {
        return Mth.lerp(partial(), flashlightPrev, flashlight);
    }

    public static float fear() {
        return Mth.lerp(partial(), fearPrev, fear);
    }

    private static float partial() {
        return Minecraft.getInstance().getFrameTime();
    }

    private static void tick() {
        flashlightPrev = flashlight;
        fearPrev = fear;

        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && mc.getCameraEntity() == mc.player && !mc.player.isSpectator();

        float target = 0f;
        if (self && ClientState.flashlightOn && ClientState.flashlightLit) {
            float b = ClientState.battery;
            // Полная батарейка светит ровно, к концу заряда лампа заметно желтеет и тускнеет.
            target = 0.55f + 0.45f * Mth.clamp(b / 0.5f, 0f, 1f);
            if (b < 0.15f) {
                if (dropTicks > 0) {
                    dropTicks--;
                    target *= 0.08f;
                } else if (RND.nextFloat() < 0.04f + 0.25f * (1f - b / 0.15f)) {
                    dropTicks = 1 + RND.nextInt(4);
                }
            }
        } else {
            dropTicks = 0;
        }
        // Разгорается за 3-4 тика, гаснет почти сразу.
        flashlight += (target - flashlight) * (target > flashlight ? 0.45f : 0.7f);

        float fearTarget = self && ClientState.inMatch ? Mth.clamp(ClientState.fear / 100f, 0f, 1f) : 0f;
        fear += (fearTarget - fear) * 0.08f;

        emergencyPrev = emergency;
        if (mc.player == null || mc.level == null) {
            emergencyTarget = 0f;
        } else if (--scanTicks <= 0) {
            scanTicks = 10;
            emergencyTarget = scanEmergency(mc);
        }
        emergency += (emergencyTarget - emergency) * 0.12f;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ShaderStateEvents {
        @SubscribeEvent
        public static void onShaderStateClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) tick();
        }
    }
}
