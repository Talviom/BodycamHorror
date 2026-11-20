package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент, 0.9 (пункт 13): зажатая клавиша выброса (G) больше не повторяет выброс и его звук.
 *
 * Windows, пока клавиша зажата, шлёт повторные нажатия, и игра считает каждое отдельным выбросом,
 * поэтому звук шёл без конца, даже с пустой рукой. В начале тика (до того, как игра разберёт клавиши)
 * съедаем все накопленные нажатия и возвращаем ровно одно, только если клавишу нажали заново
 * и в руке что-то есть. Ctrl+G (выбросить всю стопку) работает как раньше: это то же одно нажатие.
 */
public final class DropKeyGuard {
    private static boolean wasDown;

    private DropKeyGuard() {}

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class DropKeyGuardEvents {
        @SubscribeEvent
        public static void onDropKeyGuardTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getInstance();
            KeyMapping drop = mc.options.keyDrop;
            boolean down = drop.isDown();
            if (mc.player == null || mc.screen != null) {
                wasDown = down;
                return;
            }
            int clicks = 0;
            while (drop.consumeClick()) clicks++;
            boolean fresh = clicks > 0 && !wasDown;
            if (fresh && !mc.player.getMainHandItem().isEmpty() && !mc.player.isSpectator()) {
                KeyMapping.click(drop.getKey());
            }
            wasDown = down;
        }
    }
}
