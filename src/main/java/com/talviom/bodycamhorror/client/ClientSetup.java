package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.inventory.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Клиент: привязка экранов к меню. */
public final class ClientSetup {
    private ClientSetup() {}

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class SetupModEvents {
        @SubscribeEvent
        public static void onBhClientSetup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> MenuScreens.register(ModMenus.INVENTORY.get(), BhInventoryScreen::new));
        }
    }
}
