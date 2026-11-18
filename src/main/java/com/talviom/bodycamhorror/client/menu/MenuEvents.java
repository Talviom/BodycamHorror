package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Клиент: ванильный титульный экран подменяется нашим главным меню (если оно не выключено в настройках). */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MenuEvents {
    private static boolean announced;

    private MenuEvents() {}

    @SubscribeEvent
    public static void onMenuScreenOpening(ScreenEvent.Opening e) {
        if (!(e.getNewScreen() instanceof TitleScreen) || !enabled()) return;
        e.setNewScreen(new BhTitleScreen());
        if (!announced) {
            announced = true;
            BodycamHorror.LOGGER.info("[BH menu] title screen replaced with the bodycam menu");
        }
    }

    private static boolean enabled() {
        try {
            return BhConfig.CUSTOM_MENU.get();
        } catch (IllegalStateException e) {
            return true; // настройки клиента ещё не загружены: меню всё равно наше
        }
    }
}
