package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: ванильные экраны заменяются нашими (если это не выключено в настройках, hud.customMenu):
 * титульный экран нашим главным меню, меню паузы нашим, экран смерти вне матча кадром SIGNAL LOST,
 * а при загрузке мира и входе на сервер вместо разноцветной карты чанков и земляного фона тёмный кадр «LOADING THE TAPE».
 * Земляной фон остальных ванильных экранов заменён текстурой (overrides/assets/minecraft/textures/gui/options_background.png, встроенный пак поверх всех).
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MenuEvents {
    private static boolean announced;

    private MenuEvents() {}

    /**
     * Пока открыта наша пауза или вызванные из неё экраны (настройки, открытие для сети и т. п.),
     * интерфейс игры (прицел, строка лобби над панелью, слоты, REC и сердце) не рисуется:
     * иначе он просвечивает сквозь затемнение и наезжает на пункты меню.
     * Чат и инвентарь не трогаем: там интерфейс игры нужен. Под ванильным экраном смерти тоже (если наше меню выключено).
     */
    @SubscribeEvent
    public static void onMenuPauseHideHud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
        Screen s = net.minecraft.client.Minecraft.getInstance().screen;
        if (s == null || s instanceof net.minecraft.client.gui.screens.ChatScreen
                || s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                || (s instanceof DeathScreen && !(s instanceof BhDeathScreen))) return;
        e.setCanceled(true);
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
        } else if (next instanceof DeathScreen vanilla && !(next instanceof BhDeathScreen)) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            boolean hardcore = mc.level != null && mc.level.getLevelData().isHardcore();
            net.minecraft.network.chat.Component cause = BhDeathScreen.causeOf(vanilla);
            e.setNewScreen(new BhDeathScreen(cause, hardcore));
            BodycamHorror.LOGGER.info("[BH menu] death screen replaced (cause: {})", cause == null ? "none" : cause.getString());
        }
    }

    /**
     * Загрузка мира, вход на сервер и короткие служебные экраны без кнопок («Joining world», «Reading world data»,
     * «Saving world»): свой тёмный кадр вместо ванильного. Надписи свои и всегда на английском: шрифт печатной
     * машинки без кириллицы, а у пользователя игра на русском.
     */
    @SubscribeEvent
    public static void onMenuLoadingRender(ScreenEvent.Render.Pre e) {
        Screen s = e.getScreen();
        boolean loading = s instanceof LevelLoadingScreen || s instanceof ReceivingLevelScreen || s instanceof ProgressScreen;
        boolean message = s instanceof GenericDirtMessageScreen;
        if ((!loading && !message) || !enabled()) return;
        e.setCanceled(true);
        boolean saving = message && s.getTitle().getContents() instanceof TranslatableContents tc
                && tc.getKey().toLowerCase(java.util.Locale.ROOT).contains("sav");
        if (saving) drawLoading(e.getGuiGraphics(), s.width, s.height, Vhs.tr("bh.loading.saving"), Vhs.tr("bh.loading.saving_omen"));
        else drawLoading(e.getGuiGraphics(), s.width, s.height, Vhs.tr("bh.loading.loading"), Vhs.tr("bh.loading.loading_omen"));
    }

    private static void drawLoading(GuiGraphics g, int w, int h, String what, String omen) {
        g.fill(0, 0, w, h, 0xFF060606);
        Vhs.noise(g, 0, 0, w, h, 0.2f);
        Vhs.vignette(g, w, h, 1f);
        Vhs.flicker(g, w, h);
        int dots = (int) ((System.currentTimeMillis() / 450) % 4);
        String text = what + ".".repeat(dots);
        int x = (w - Vhs.width(Vhs.title(what + "..."))) / 2;
        Vhs.ghost(g, Vhs.title(text), x, h / 2 - 8, 0xD0000000 | Vhs.BONE, 1f, 0.3f);
        Vhs.ghostCentered(g, Vhs.title(omen), w / 2, h / 2 + 8, 0xC0000000 | Vhs.DRIED_BLOOD, 1f, 0.2f);
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
