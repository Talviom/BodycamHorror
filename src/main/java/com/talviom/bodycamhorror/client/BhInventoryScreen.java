package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.inventory.BhInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Экран инвентаря в стиле VHS: слева модель персонажа, справа 2 ряда по 10 слотов.
 * Крафта и брони нет. Предметы можно переставлять, Shift+клик переносит между рядами,
 * Q или перетаскивание за окно выбрасывает. Закрывается той же клавишей (Tab), Esc или E.
 */
public class BhInventoryScreen extends AbstractContainerScreen<BhInventoryMenu> {
    private static final ResourceLocation TEX = Vhs.rl("textures/gui/inventory.png");
    private static final DateTimeFormatter TIMECODE = DateTimeFormatter.ofPattern("HH:mm:ss");
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
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        // Плёночный налёт поверх окна, но под подсказкой.
        Vhs.scanlines(g, leftPos, topPos, imageWidth, imageHeight, 0.35f);
        Vhs.noise(g, leftPos, topPos, imageWidth, imageHeight, 0.05f);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        if (Vhs.has(TEX)) {
            g.blit(TEX, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        } else {
            g.fill(x, y, x + W, y + H, 0xE0101214);
            g.fill(x, y, x + W, y + 1, 0xFF8A8A8A);
            g.fill(x, y + H - 1, x + W, y + H, 0xFF8A8A8A);
            g.fill(x, y, x + 1, y + H, 0xFF8A8A8A);
            g.fill(x + W - 1, y, x + W, y + H, 0xFF8A8A8A);
            g.fill(x + MODEL_X0, y + MODEL_Y0, x + MODEL_X1, y + MODEL_Y1, 0x60000000);
            for (int r = 0; r < BhInventoryMenu.ROWS; r++) {
                for (int c = 0; c < BhInventoryMenu.COLS; c++) {
                    int sx = x + BhInventoryMenu.GRID_X + c * BhInventoryMenu.PITCH;
                    int sy = y + BhInventoryMenu.GRID_Y + r * BhInventoryMenu.PITCH;
                    g.fill(sx, sy, sx + 18, sy + 18, 0xFF3A3A3A);
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF161616);
                }
            }
        }

        // Модель персонажа, поворачивается за мышью.
        int cx = x + (MODEL_X0 + MODEL_X1) / 2;
        int feet = y + MODEL_Y1 - 6;
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, 44,
                    (float) cx - mouseX, (float) (feet - 70) - mouseY, minecraft.player);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!Vhs.has(TEX)) Vhs.chroma(g, "INVENTORY", 8, 4, 0xFFE8E8E8, 1f);

        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String rec = "REC";
        String time = LocalDateTime.now().format(TIMECODE);
        int right = imageWidth - 8;
        Vhs.plain(g, rec, right - Vhs.width(rec), 4, 0xFFE8E8E8);
        if (blink) Vhs.plain(g, "●", right - Vhs.width(rec) - Vhs.width("● "), 4, 0xFFE53935);
        Vhs.plain(g, time, right - Vhs.width(time), 15, 0xFF9E9E9E);

        // Номера быстрых слотов над верхним рядом.
        for (int c = 0; c < 9; c++) {
            String n = String.valueOf(c + 1);
            int sx = BhInventoryMenu.GRID_X + c * BhInventoryMenu.PITCH + 9 - Vhs.width(n) / 2;
            Vhs.plain(g, n, sx, BhInventoryMenu.GRID_Y - 10, 0xFF8A8A8A);
        }
        Vhs.plain(g, "SHIFT+CLICK: MOVE   Q: DROP", BhInventoryMenu.GRID_X, BhInventoryMenu.GRID_Y + 2 * BhInventoryMenu.PITCH + 8, 0xFF6E6E6E);
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
