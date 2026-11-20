package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Клиент, один раз после обновления до 0.9: фонарик с O на T, чат с T на Enter.
 *
 * Новое значение по умолчанию у KeyMapping подхватывают только новые игроки: у остальных в options.txt
 * уже записано «фонарик O, чат T». Поэтому на первом тике клиента переставляем клавиши сами, но только
 * если они стоят по-старому (свои назначения игрока не трогаем), сохраняем options.txt и ставим метку
 * в config, чтобы больше этого не делать.
 */
public final class KeyDefaults09 {
    private static final String MARKER = "bodycamhorror-keys09.done";
    private static boolean checked;

    private KeyDefaults09() {}

    private static void migrate(Minecraft mc) {
        checked = true;
        Path marker = FMLPaths.CONFIGDIR.get().resolve(MARKER);
        if (Files.exists(marker)) return;
        boolean changed = false;
        KeyMapping chat = mc.options.keyChat;
        if (isKey(chat, GLFW.GLFW_KEY_T)) {
            chat.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_ENTER));
            changed = true;
        }
        if (isKey(ClientKeys.FLASHLIGHT, GLFW.GLFW_KEY_O)) {
            ClientKeys.FLASHLIGHT.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_T));
            changed = true;
        }
        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
            BodycamHorror.LOGGER.info("0.9 keys: flashlight -> T, chat -> Enter");
        }
        try {
            Files.writeString(marker, "flashlight T, chat Enter (0.9)\n");
        } catch (Exception e) {
            BodycamHorror.LOGGER.warn("Could not write {}: {}", MARKER, e.toString());
        }
    }

    private static boolean isKey(KeyMapping k, int glfw) {
        InputConstants.Key key = k.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() == glfw;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class KeyDefaults09Events {
        @SubscribeEvent
        public static void onKeyDefaults09ClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || checked) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.options == null) return;
            migrate(mc);
        }
    }
}
