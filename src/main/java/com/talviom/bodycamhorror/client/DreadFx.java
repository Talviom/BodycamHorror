package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.net.DreadPacket;
import com.talviom.bodycamhorror.tenant.ModEntities;
import com.talviom.bodycamhorror.tenant.TenantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Клиент: хоррор-ивенты 0.9, которые видит только этот игрок (пункт 15 плана).
 * <ul>
 *   <li>Сбой времени: часы камеры на пару секунд показывают 03:33 или дату 1998 года.</li>
 *   <li>Перемотка: полосы трекинга бегут по кадру, картинка дёргается, как зажёванная плёнка.</li>
 *   <li>Силуэт: Жилец стоит в конце коридора. Подойдёшь ближе 7 блоков или выйдет время, свет моргнёт,
 *       и его нет. Это не настоящий Жилец: его нет на сервере, его видит только один игрок.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DreadFx {
    private static final Random RND = new Random();
    private static final int PHANTOM_ID = -0x7B0D1;
    private static final double PHANTOM_VANISH_DIST = 7.0;

    private static String timecode;
    private static long timecodeUntil;
    private static long rewindUntil;
    private static long rewindFrom;
    private static int phantomTicks;
    private static TenantEntity phantom;

    private DreadFx() {}

    public static void on(int kind, double x, double y, double z, float yaw, int ticks) {
        long now = System.currentTimeMillis();
        switch (kind) {
            case DreadPacket.TIMECODE -> {
                timecode = RND.nextBoolean() ? null : "1998-10-31  03:33:33";
                timecodeUntil = now + ticks * 50L;
            }
            case DreadPacket.REWIND -> {
                rewindFrom = now;
                rewindUntil = now + ticks * 50L;
            }
            case DreadPacket.SILHOUETTE -> spawnPhantom(x, y, z, yaw, ticks);
            default -> {}
        }
    }

    /**
     * Строка таймкода камеры: во время «сбоя времени» вместо настоящей. Часы камеры (ClientHud) берут её отсюда.
     * Без даты (null в timecode) показывается только время 03:33:33 при сегодняшней дате.
     */
    public static String timecode(String real) {
        if (System.currentTimeMillis() > timecodeUntil) return real;
        if (timecode != null) return timecode;
        int cut = real.lastIndexOf(' ');
        return (cut > 0 ? real.substring(0, cut + 1) : "") + "03:33:33";
    }

    private static void spawnPhantom(double x, double y, double z, float yaw, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        removePhantom(false);
        TenantEntity e = ModEntities.TENANT.get().create(mc.level);
        if (e == null) return;
        e.setId(PHANTOM_ID);
        e.moveTo(x, y, z, yaw, 0f);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.pose(TenantEntity.State.STARE);
        mc.level.putNonPlayerEntity(PHANTOM_ID, e);
        phantom = e;
        phantomTicks = ticks;
    }

    /** Убрать силуэт; blink: со вспышкой помех, будто моргнул свет. */
    private static void removePhantom(boolean blink) {
        Minecraft mc = Minecraft.getInstance();
        if (phantom == null) return;
        if (mc.level != null) mc.level.removeEntity(PHANTOM_ID, Entity.RemovalReason.DISCARDED);
        phantom = null;
        if (blink) ClientEffects.startGlitch(6, 0.9f);
    }

    @SubscribeEvent
    public static void onDreadClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || phantom == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || phantom.level() != mc.level) {
            phantom = null;
            return;
        }
        phantomTicks--;
        if (phantomTicks <= 0 || mc.player.distanceToSqr(phantom) < PHANTOM_VANISH_DIST * PHANTOM_VANISH_DIST) {
            removePhantom(true);
        }
    }

    /** Перемотка поверх кадра: тёмные полосы трекинга бегут вверх, кадр дрожит, светлая полоса шума. */
    @SubscribeEvent
    public static void onDreadRender(RenderGuiEvent.Post e) {
        long now = System.currentTimeMillis();
        if (now > rewindUntil) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth();
        int h = e.getWindow().getGuiScaledHeight();
        float k = (now - rewindFrom) / (float) Math.max(1, rewindUntil - rewindFrom);
        float strength = k < 0.15f ? k / 0.15f : k > 0.8f ? (1f - k) / 0.2f : 1f;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, w, h, ((int) (strength * 70) << 24));
        int bands = 3;
        for (int i = 0; i < bands; i++) {
            int bh = 6 + RND.nextInt(10);
            int y = (int) (((now / 3) + i * h / bands) % (h + 40)) - 20;
            y = h - y;
            g.fill(0, y, w, y + bh, ((int) (strength * 150) << 24) | 0x0C0B0A);
            g.fill(0, y + bh, w, y + bh + 1, ((int) (strength * 110) << 24) | Vhs.BONE);
        }
        Vhs.noise(g, 0, 0, w, h, 0.35f * strength);
        g.pose().popPose();
    }
}
