package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.OpenInventoryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Клиент: прячем ванильный HUD, подменяем инвентарь, рисуем бодикам-оверлей, полоску слотов, страх и заставки. */
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

    private static final ResourceLocation SLOT = Vhs.rl("textures/gui/slot.png");
    private static final ResourceLocation SLOT_SELECTED = Vhs.rl("textures/gui/slot_selected.png");
    /** Клетка полоски слотов: в 1,5 раза больше ванильной. */
    private static final int CELL = 30;
    private static final float ITEM_SCALE = 1.5f;

    private ClientHud() {}

    /**
     * Полоска слотов вместо хотбара, всегда видна в правом нижнем углу.
     * Показывает занятые быстрые слоты и выбранный слот, даже если он пустой.
     */
    private static void drawSlotStrip(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!BhConfig.SLOT_STRIP.get() || mc.player == null || mc.options.hideGui || creativeOrSpectator()) return;
        Inventory inv = mc.player.getInventory();

        int shown = 0;
        for (int i = 0; i < 9; i++) {
            if (!inv.getItem(i).isEmpty() || i == inv.selected) shown++;
        }
        Font font = mc.font;
        int x = width - 8 - shown * (CELL + 2) + 2;
        int y = height - 8 - CELL;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            boolean selected = i == inv.selected;
            if (s.isEmpty() && !selected) continue;
            ResourceLocation tex = selected ? SLOT_SELECTED : SLOT;
            if (Vhs.has(tex)) {
                g.blit(tex, x, y, CELL, CELL, 0f, 0f, 24, 24, 24, 24);
            } else {
                g.fill(x, y, x + CELL, y + CELL, selected ? 0x40000000 | Vhs.BONE : 0x50000000);
                if (selected) {
                    g.fill(x, y, x + CELL, y + 1, 0xC0000000 | Vhs.BONE);
                    g.fill(x, y + CELL - 1, x + CELL, y + CELL, 0xC0000000 | Vhs.BONE);
                }
            }
            if (!s.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x + 3, y + 3, 0);
                g.pose().scale(ITEM_SCALE, ITEM_SCALE, 1f);
                g.renderItem(s, 0, 0);
                g.renderItemDecorations(font, s, 0, 0);
                g.pose().popPose();
            }
            Vhs.plain(g, String.valueOf(i + 1), x + 1, y - 10, selected ? 0xE0000000 | Vhs.BONE : 0xE0000000 | Vhs.ASH);
            x += CELL + 2;
        }
        ItemStack held = inv.getItem(inv.selected);
        if (!held.isEmpty()) {
            String name = held.getHoverName().getString();
            Vhs.plain(g, name, width - 8 - Vhs.width(name), y - 24, 0xC8000000 | Vhs.BONE);
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

        /** «X joined the game» и «X left the game» не ванильным жёлтым, а тусклым пеплом. */
        @SubscribeEvent
        public static void onHudJoinLeaveMessage(ClientChatReceivedEvent.System e) {
            if (e.isOverlay() || !(e.getMessage().getContents() instanceof TranslatableContents tc)) return;
            if (!tc.getKey().startsWith("multiplayer.player.")) return;
            e.setMessage(Component.translatable(tc.getKey(), tc.getArgs())
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Vhs.ASH))));
        }

        /** Ванильный инвентарь (E) заменяется нашим экраном на 20 слотов. */
        @SubscribeEvent
        public static void onScreen(ScreenEvent.Opening e) {
            if (!BhConfig.BLOCK_INVENTORY.get() || creativeOrSpectator()) return;
            if (e.getScreen() instanceof InventoryScreen) {
                e.setCanceled(true);
                BhNetwork.CHANNEL.sendToServer(new OpenInventoryPacket());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class HudModEvents {
        @SubscribeEvent
        public static void registerHudOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("bodycam_slots", (gui, g, partialTick, width, height) ->
                    aboveChat(g, ABOVE_CHAT_Z, () -> drawSlotStrip(g, width, height)));
            e.registerAboveAll("bodycam_fear", (gui, g, partialTick, width, height) -> {
                if (!creativeOrSpectator()) aboveChat(g, ABOVE_CHAT_Z, () -> FearHud.draw(g, width, height));
            });
            e.registerAboveAll("bodycam", (gui, g, partialTick, width, height) -> {
                Minecraft mc = Minecraft.getInstance();
                if (!BhConfig.BODYCAM_OVERLAY.get() || mc.player == null || mc.options.hideGui) return;
                aboveChat(g, ABOVE_CHAT_Z, () -> drawCameraText(g, mc, width));
            });
            // Заставки раунда поверх всего остального: поверх открытого чата и предметов в полоске слотов.
            e.registerAboveAll("bodycam_banner", (gui, g, partialTick, width, height) ->
                    aboveChat(g, BANNER_Z, () -> Banners.draw(g, width, height)));
        }
    }

    /** Надписи камеры в правом верхнем углу: REC, время, имя. */
    private static void drawCameraText(GuiGraphics g, Minecraft mc, int width) {
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String cam = "CAM-" + mc.player.getGameProfile().getName().toUpperCase();
        String time = LocalDateTime.now().format(TIMECODE);
        int x = width - 8;
        int y = 8;
        // Как на старой камере: выцветшая кость и пепел, красной остаётся только точка записи.
        if (blink) Vhs.plain(g, "\u25CF", x - Vhs.width("\u25CF REC"), y, 0xFF000000 | Vhs.DRIED_BLOOD);
        Vhs.ghost(g, Vhs.text("REC"), x - Vhs.width("REC"), y, 0xD0000000 | Vhs.BONE, 1f, 0f);
        Vhs.plain(g, time, x - Vhs.width(time), y + 11, 0xA0000000 | Vhs.BONE);
        Vhs.plain(g, cam, x - Vhs.width(cam), y + 22, 0xE0000000 | Vhs.ASH);
    }

    /**
     * Ванильный чат рисует свои строки со сдвигом по глубине (z = 50..100), поэтому всё, что
     * нарисовано на z = 0, оказывалось под ним: открытый чат светлым пятном проступал сквозь
     * чёрную заставку раунда, сердце и полоску слотов. Наш HUD рисуем выше чата.
     * Строка ввода чата (сам экран чата) всё равно остаётся сверху: экраны рисуются после HUD.
     */
    private static final float ABOVE_CHAT_Z = 200f;
    /**
     * Предметы в полоске слотов рисуются ещё на +150..+250 выше своего слоя (иконка фонарика,
     * часы), поэтому заставка раунда идёт отдельным, самым верхним слоем.
     */
    private static final float BANNER_Z = 800f;

    private static void aboveChat(GuiGraphics g, float z, Runnable draw) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, z);
        try {
            draw.run();
        } finally {
            g.pose().popPose();
        }
    }
}
