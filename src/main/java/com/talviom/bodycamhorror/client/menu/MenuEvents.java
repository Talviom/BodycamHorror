package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: ванильные экраны заменяются нашими (если это не выключено в настройках, hud.customMenu):
 * титульный экран нашим главным меню, меню паузы нашим, а при загрузке мира и входе на сервер
 * вместо разноцветной карты чанков и земляного фона тёмный кадр «LOADING THE TAPE».
 * Земляной фон остальных ванильных экранов заменён текстурой (assets/minecraft/textures/gui/options_background.png).
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MenuEvents {
    private static boolean announced;

    private MenuEvents() {}

    /**
     * Пока открыта наша пауза, интерфейс игры (прицел, строка лобби над панелью, слоты, REC и сердце)
     * не рисуется: иначе он просвечивает сквозь затемнение и наезжает на пункты меню.
     */
    @SubscribeEvent
    public static void onMenuPauseHideHud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof BhPauseScreen) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMenuScreenOpening(ScreenEvent.Opening e) {
        Screen next = e.getNewScreen();
        if (next == null || !enabled()) return;
        if (next instanceof TitleScreen) {
            e.setNewScreen(new BhTitleScreen());
            if (!announced) {
                announced = true;
                BodycamHorror.LOGGER.info("[BH menu] title screen replaced with the bodycam menu");
            }
        } else if (next instanceof PauseScreen) {
            e.setNewScreen(new BhPauseScreen());
        }
    }

    /** Загрузка мира и вход на сервер: свой тёмный кадр вместо ванильного. */
    @SubscribeEvent
    public static void onMenuLoadingRender(ScreenEvent.Render.Pre e) {
        Screen s = e.getScreen();
        if (!(s instanceof LevelLoadingScreen) && !(s instanceof ReceivingLevelScreen)) return;
        if (!enabled()) return;
        e.setCanceled(true);
        drawLoading(e.getGuiGraphics(), s.width, s.height);
    }

    private static void drawLoading(GuiGraphics g, int w, int h) {
        g.fill(0, 0, w, h, 0xFF060606);
        Vhs.noise(g, 0, 0, w, h, 0.2f);
        Vhs.vignette(g, w, h, 1f);
        Vhs.flicker(g, w, h);
        int dots = (int) ((System.currentTimeMillis() / 450) % 4);
        String text = "LOADING THE TAPE" + ".".repeat(dots);
        int x = (w - Vhs.width(Vhs.title("LOADING THE TAPE..."))) / 2;
        Vhs.ghost(g, Vhs.title(text), x, h / 2 - 8, 0xD0000000 | Vhs.BONE, 1f, 0.3f);
        Vhs.ghostCentered(g, Vhs.title("DON'T LOOK AWAY."), w / 2, h / 2 + 8, 0xC0000000 | Vhs.DRIED_BLOOD, 1f, 0.2f);
        MenuOsd.draw(g, w, h, 1f, null);
    }

    private static boolean enabled() {
        try {
            return BhConfig.CUSTOM_MENU.get();
        } catch (IllegalStateException e) {
            return true; // настройки клиента ещё не загружены: меню всё равно наше
        }
    }
}
