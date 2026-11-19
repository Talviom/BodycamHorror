package com.talviom.bodycamhorror.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * Свой инвентарь на 20 слотов: 2 ряда по 10.
 * Верхний ряд: слоты 0..8 это быстрые слоты (клавиши 1..9, полоска справа внизу), слот 9 обычный.
 * Нижний ряд: слоты инвентаря 10..19. Остальное (20..35) закрыто, см. InventoryRules.
 * Крафта и брони здесь нет. Предмет выбрасывается, если перетащить его за пределы окна или нажать Q.
 */
public class BhInventoryMenu extends AbstractContainerMenu {
    public static final int COLS = 10;
    public static final int ROWS = 2;
    public static final int SLOTS = COLS * ROWS;
    /** Левый верхний угол сетки слотов в inventory.png (клетка 18x18, предмет внутри с отступом 1). */
    public static final int GRID_X = 70;
    public static final int GRID_Y = 40;
    public static final int PITCH = 18;

    public BhInventoryMenu(int id, Inventory inv) {
        super(ModMenus.INVENTORY.get(), id);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new Slot(inv, row * COLS + col, GRID_X + 1 + col * PITCH, GRID_Y + 1 + row * PITCH));
            }
        }
    }

    public static void open(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        if (p.containerMenu != p.inventoryMenu) return;
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new BhInventoryMenu(id, inv),
                Component.translatable("container.bodycamhorror.inventory")));
    }

    /** Shift+клик переносит предмет между верхним и нижним рядом. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean moved = index < COLS
                ? moveItemStackTo(stack, COLS, SLOTS, false)
                : moveItemStackTo(stack, 0, COLS, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && !player.isSpectator();
    }
}
