package com.talviom.bodycamhorror.inventory;

import com.talviom.bodycamhorror.BhConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Игрок носит только 20 слотов (0..19). Слоты 20..35 закрыты:
 * предмет не подбирается, если ему нет места в открытых слотах,
 * а то, что всё же попало в закрытые слоты, переносится в открытые или выпадает на землю.
 * Броня, вторая рука и Curios не трогаются. В креативе ограничения нет.
 */
public class InventoryRules {
    private static final int OPEN = BhInventoryMenu.SLOTS;
    private static final int MAIN_END = 36;

    @SubscribeEvent
    public void onBhPickup(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !limited(p)) return;
        if (!fits(p.getInventory(), e.getItem().getItem())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onBhPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 10 != 0 || !limited(p)) return;
        Inventory inv = p.getInventory();
        for (int i = OPEN; i < MAIN_END; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            inv.setItem(i, ItemStack.EMPTY);
            ItemStack rest = moveIntoOpen(inv, stack);
            if (!rest.isEmpty()) p.drop(rest, false, false);
        }
    }

    private static boolean limited(ServerPlayer p) {
        return BhConfig.LIMIT_INVENTORY.get() && !p.isCreative() && !p.isSpectator();
    }

    /** Есть ли в открытых слотах место хотя бы для части стака. */
    static boolean fits(Inventory inv, ItemStack stack) {
        for (int i = 0; i < OPEN; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(s, stack) && s.getCount() < Math.min(s.getMaxStackSize(), inv.getMaxStackSize())) return true;
        }
        return false;
    }

    /** Разложить стак по открытым слотам. Возвращает то, что не влезло. */
    private static ItemStack moveIntoOpen(Inventory inv, ItemStack stack) {
        for (int i = 0; i < OPEN && !stack.isEmpty(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, stack)) continue;
            int room = Math.min(s.getMaxStackSize(), inv.getMaxStackSize()) - s.getCount();
            if (room <= 0) continue;
            int n = Math.min(room, stack.getCount());
            s.grow(n);
            stack.shrink(n);
        }
        for (int i = 0; i < OPEN && !stack.isEmpty(); i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }
}
