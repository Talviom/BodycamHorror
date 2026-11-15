package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Клиент: прячем ванильный HUD и инвентарь, рисуем бодикам-оверлей. */
public final class ClientHud {
    private static final DateTimeFormatter TIMECODE = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");

    private static final Set<ResourceLocation> HIDDEN = Set.of(
            VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(),
            VanillaGuiOverlay.MOUNT_HEALTH.id(),
            VanillaGuiOverlay.JUMP_BAR.id(),
            VanillaGuiOverlay.ITEM_NAME.id()
    );

    private static int lastSlot = -1;
    private static int lastHotbarHash;
    private static long slotStripUntil;

    private ClientHud() {}

    /**
     * Полоска слотов вместо хотбара: показывает только занятые слоты и появляется на пару секунд,
     * когда игрок меняет слот или у него что-то меняется в руках.
     */
    private static void drawSlotStrip(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!BhConfig.SLOT_STRIP.get() || mc.player == null || mc.options.hideGui || creativeOrSpectator()) return;
        Inventory inv = mc.player.getInventory();

        int hash = 1;
        int filled = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) filled++;
            hash = 31 * hash + (s.isEmpty() ? 0 : Item.getId(s.getItem()) * 31 + s.getCount());
        }
        long now = System.currentTimeMillis();
        if (inv.selected != lastSlot || hash != lastHotbarHash) {
            lastSlot = inv.selected;
            lastHotbarHash = hash;
            slotStripUntil = now + 2500;
        }
        if (now > slotStripUntil || filled == 0) return;

        Font font = mc.font;
        int x = width - 8 - filled * 20;
        int y = height - 26;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            boolean selected = i == inv.selected;
            g.fill(x - 1, y - 1, x + 17, y + 17, selected ? 0x90FFFFFF : 0x50000000);
            g.renderItem(s, x, y);
            g.renderItemDecorations(font, s, x, y);
            g.drawString(font, String.valueOf(i + 1), x, y - 10, selected ? 0xFFFFFFFF : 0xFF9E9E9E, false);
            x += 20;
        }
        ItemStack held = inv.getItem(inv.selected);
        if (!held.isEmpty()) {
            String name = held.getHoverName().getString();
            g.drawString(font, name, width - 8 - font.width(name), y - 22, 0xFFE0E0E0, false);
        }
    }

    private static boolean creativeOrSpectator() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        GameType mode = mc.gameMode.getPlayerMode();
        return mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class HudForgeEvents {
        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
            if (!BhConfig.HIDE_VANILLA_HUD.get() || creativeOrSpectator()) return;
            if (HIDDEN.contains(e.getOverlay().id())) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void onScreen(ScreenEvent.Opening e) {
            if (!BhConfig.BLOCK_INVENTORY.get() || creativeOrSpectator()) return;
            if (e.getScreen() instanceof InventoryScreen) e.setCanceled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class HudModEvents {
        @SubscribeEvent
        public static void registerHudOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("bodycam_slots", (gui, g, partialTick, width, height) -> drawSlotStrip(g, width, height));
            e.registerAboveAll("bodycam", (gui, g, partialTick, width, height) -> {
                Minecraft mc = Minecraft.getInstance();
                if (!BhConfig.BODYCAM_OVERLAY.get() || mc.player == null || mc.options.hideGui) return;
                Font font = mc.font;
                boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                String cam = "CAM-" + mc.player.getGameProfile().getName().toUpperCase();
                String time = LocalDateTime.now().format(TIMECODE);
                int x = width - 8;
                int y = 8;
                if (blink) g.drawString(font, "●", x - font.width("● REC"), y, 0xFFE53935, false);
                g.drawString(font, "REC", x - font.width("REC"), y, 0xFFFFFFFF, false);
                g.drawString(font, time, x - font.width(time), y + 11, 0xFFE0E0E0, false);
                g.drawString(font, cam, x - font.width(cam), y + 22, 0xFFBDBDBD, false);
            });
        }
    }
}
