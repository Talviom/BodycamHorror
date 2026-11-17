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

    private ShaderState() {}

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
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ShaderStateEvents {
        @SubscribeEvent
        public static void onShaderStateClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) tick();
        }
    }
}
