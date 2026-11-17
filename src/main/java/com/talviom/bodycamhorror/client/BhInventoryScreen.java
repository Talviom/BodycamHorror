package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.inventory.BhInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;


/**
 * Экран инвентаря в мрачном стиле «найденной плёнки»: слева модель персонажа в тени, справа 2 ряда по 10 слотов.
 * Крафта и брони нет. Предметы можно переставлять, Shift+клик переносит между рядами,
 * Q или перетаскивание за окно выбрасывает. Закрывается той же клавишей (Tab), Esc или E.
 */
public class BhInventoryScreen extends AbstractContainerScreen<BhInventoryMenu> {
    private static final ResourceLocation TEX = Vhs.rl("textures/gui/inventory.png");
    private static final int W = 252;
    private static final int H = 150;
    /** Область модели персонажа в текстуре. */
    private static final int MODEL_X0 = 8, MODEL_X1 = 60, MODEL_Y0 = 16, MODEL_Y1 = 120;

    public BhInventoryScreen(BhInventoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Вместо ванильного серого фона: почти чёрный кадр с тёмными краями.
        g.fill(0, 0, width, height, 0xD0000000);
        Vhs.vignette(g, width, height, 1f);
        super.render(g, mouseX, mouseY, partialTick);
        // Плёночное зерно поверх окна, но под подсказкой.
        Vhs.noise(g, leftPos, topPos, imageWidth, imageHeight, 0.08f);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        if (Vhs.has(TEX)) {
            g.blit(TEX, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        } else {
            // Запасной вид: грязная тёмная панель с утопленными клетками.
            g.fill(x, y, x + W, y + H, 0xF00B0A09);
            g.fill(x, y, x + W, y + 1, 0xFF2A2622);
            g.fill(x, y + H - 1, x + W, y + H, 0xFF2A2622);
            g.fill(x, y, x + 1, y + H, 0xFF2A2622);
            g.fill(x + W - 1, y, x + W, y + H, 0xFF2A2622);
            g.fill(x + MODEL_X0, y + MODEL_Y0, x + MODEL_X1, y + MODEL_Y1, 0x80000000);
            for (int r = 0; r < BhInventoryMenu.ROWS; r++) {
                for (int c = 0; c < BhInventoryMenu.COLS; c++) {
                    int sx = x + BhInventoryMenu.GRID_X + c * BhInventoryMenu.PITCH;
                    int sy = y + BhInventoryMenu.GRID_Y + r * BhInventoryMenu.PITCH;
                    g.fill(sx, sy, sx + 18, sy + 18, 0xFF1E1B18);
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF070606);
                }
            }
        }

        // Модель персонажа, поворачивается за мышью. Сверху тень, чтобы она не светилась в темноте.
        int cx = x + (MODEL_X0 + MODEL_X1) / 2;
        int feet = y + MODEL_Y1 - 6;
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, 44,
                    (float) cx - mouseX, (float) (feet - 70) - mouseY, minecraft.player);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fillGradient(x + MODEL_X0, y + MODEL_Y0, x + MODEL_X1, y + MODEL_Y1, 0x30000000, 0x90000000);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // В текстуре надписей нет, все подписи рисует код.
        Vhs.ghost(g, Vhs.title("PERSONAL EFFECTS"), 8, 5, 0xFF000000 | Vhs.BONE, 1f, 0f);
        Vhs.plain(g, "ITEMS ON THE BODY", BhInventoryMenu.GRID_X, 18, 0xFF000000 | Vhs.SOOT);
        // Подпись под «фотографией» персонажа.
        String subject = "SUBJECT";
        Vhs.plain(g, subject, (MODEL_X0 + MODEL_X1) / 2 - Vhs.width(subject) / 2, MODEL_Y1 + 4, 0xFF000000 | Vhs.ASH);
        // Строка запёкшейся кровью внизу.
        String omen = "THEY WILL FIND WHAT YOU LEAVE.";
        Vhs.ghost(g, Vhs.title(omen), imageWidth - 8 - Vhs.width(Vhs.title(omen)), imageHeight - 14,
                0xFF000000 | Vhs.DRIED_BLOOD, 1f, 0f);

        boolean blink = (System.currentTimeMillis() / 700) % 2 == 0;
        String rec = "REC";
        int right = imageWidth - 8;
        Vhs.plain(g, rec, right - Vhs.width(rec), 5, 0xFF000000 | Vhs.ASH);
        if (blink) Vhs.plain(g, "\u25CF", right - Vhs.width(rec) - Vhs.width("\u25CF "), 5, 0xFF000000 | Vhs.DRIED_BLOOD);

        // Номера быстрых слотов над верхним рядом.
        for (int c = 0; c < 9; c++) {
            String n = String.valueOf(c + 1);
            int sx = BhInventoryMenu.GRID_X + c * BhInventoryMenu.PITCH + 9 - Vhs.width(n) / 2;
            Vhs.plain(g, n, sx, BhInventoryMenu.GRID_Y - 10, 0xFF000000 | Vhs.ASH);
        }
        Vhs.plain(g, "SHIFT+CLICK: MOVE   Q: DROP", BhInventoryMenu.GRID_X,
                BhInventoryMenu.GRID_Y + 2 * BhInventoryMenu.PITCH + 8, 0xFF000000 | Vhs.SOOT);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (ClientKeys.INVENTORY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
