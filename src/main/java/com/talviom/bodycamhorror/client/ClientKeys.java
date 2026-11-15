package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.ToggleFlashlightPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент: клавиши мода. Имена вложенных классов и методов уникальны в пакете client
 * (см. пояснение в ClientEffects).
 */
public final class ClientKeys {
    public static final KeyMapping FLASHLIGHT = new KeyMapping(
            "key.bodycamhorror.flashlight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.bodycamhorror");

    private ClientKeys() {}

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class KeysModEvents {
        @SubscribeEvent
        public static void registerBhKeys(RegisterKeyMappingsEvent e) {
            e.register(FLASHLIGHT);
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class KeysForgeEvents {
        @SubscribeEvent
        public static void onKeysClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) return;
            while (FLASHLIGHT.consumeClick()) {
                BhNetwork.CHANNEL.sendToServer(new ToggleFlashlightPacket());
            }
        }
    }
}
