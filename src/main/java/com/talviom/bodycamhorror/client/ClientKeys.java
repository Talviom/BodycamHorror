package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.OpenInventoryPacket;
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
 * Клиент: клавиши мода. Фонарик на O (L занята прицелом TACZ), инвентарь на Tab
 * (список игроков стоит перенести на другую клавишу, например Caps Lock).
 * Взаимодействие со щитком и генератором идёт через клавишу F самого TACZ (тег interact_key/whitelist).
 * Имена вложенных классов и методов уникальны в пакете client (см. пояснение в ClientEffects).
 */
public final class ClientKeys {
    public static final KeyMapping FLASHLIGHT = new KeyMapping(
            "key.bodycamhorror.flashlight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.bodycamhorror");
    public static final KeyMapping INVENTORY = new KeyMapping(
            "key.bodycamhorror.inventory", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, "key.categories.bodycamhorror");

    private ClientKeys() {}

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class KeysModEvents {
        @SubscribeEvent
        public static void registerBhKeys(RegisterKeyMappingsEvent e) {
            e.register(FLASHLIGHT);
            e.register(INVENTORY);
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class KeysForgeEvents {
        @SubscribeEvent
        public static void onKeysClientTick(TickEvent.ClientTickEvent e) {
            Minecraft mc = Minecraft.getInstance();
            if (e.phase != TickEvent.Phase.END || mc.player == null) return;
            while (FLASHLIGHT.consumeClick()) {
                BhNetwork.CHANNEL.sendToServer(new ToggleFlashlightPacket());
            }
            while (INVENTORY.consumeClick()) {
                if (mc.screen == null && !mc.player.isCreative() && !mc.player.isSpectator()) {
                    BhNetwork.CHANNEL.sendToServer(new OpenInventoryPacket());
                }
            }
        }
    }
}
