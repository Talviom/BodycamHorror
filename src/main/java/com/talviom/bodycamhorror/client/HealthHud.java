package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.gear.BandageItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: здоровье и перевязка вместо ванильных сердечек.
 * <ul>
 *   <li>Полоска здоровья: кусок старой плёнки с перфорацией рядом с сердцем страха, без цифр. Засвеченные
 *       кадры показывают, сколько здоровья осталось; при низком здоровье они темнеют до запёкшейся крови
 *       и неровно мерцают. Потерянный кусок ещё мгновение тлеет тёмно-красным, потом гаснет.</li>
 *   <li>Под прицелом, пока держишь ПКМ с бинтом: «ПЕРЕВЯЗКА» и тонкая полоска хода.</li>
 * </ul>
 * Текстура плёнки: tools/assets/make_bandage.py (без неё рисуется простой запасной вариант).
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealthHud {
    private static final ResourceLocation FILM = Vhs.rl("textures/gui/health_film.png");
    /** Размер полоски на экране; текстура вдвое крупнее (128x24 на ряд). */
    public static final int WIDTH = 64;
    public static final int HEIGHT = 12;
    /** Засвеченная полоса внутри плёнки, по высоте (в пикселях экрана). */
    private static final int BAND_TOP = 3;
    private static final int BAND_H = 6;

    private static final float LOW = 0.35f;

    /** Что сейчас показано (плавно догоняет настоящее здоровье) и тлеющий след потери. */
    private static float shown = -1f;
    private static float lost = 0f;
    private static long lostAt;

    private HealthHud() {}

    /** Полоска здоровья, левый верхний угол в (x, y). Вызывается из FearHud рядом с сердцем. */
    static void drawHealth(GuiGraphics g, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !BhConfig.HIDE_VANILLA_HUD.get() || mc.player.isSpectator()) return;
        float hp = Mth.clamp(mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth()), 0f, 1f);
        long now = System.currentTimeMillis();
        if (shown < 0f) shown = hp;
        if (hp < shown - 0.001f) {
            // Удар: показанное здоровье падает сразу, а потерянный кусок тлеет.
            lost = Math.max(lost, shown);
            lostAt = now;
            shown = hp;
        } else {
            shown = Math.min(hp, shown + 0.02f); // лечение заполняет плёнку постепенно
        }
        float lostAlpha = lost > shown ? 1f - Mth.clamp((now - lostAt - 350) / 650f, 0f, 1f) : 0f;
        if (lostAlpha <= 0f) lost = 0f;

        // Цвет засветки: кость при полном здоровье, к запёкшейся крови при низком.
        float k = Mth.clamp((shown - 0.15f) / 0.6f, 0f, 1f);
        int rgb = lerpRgb(Vhs.DRIED_BLOOD, Vhs.BONE, k);
        float alpha = 0.9f;
        if (shown < LOW) {
            // Неровное мерцание, как у умирающей лампы, тем чаще, чем меньше здоровья.
            double t = now / (140.0 + shown * 400.0);
            alpha = 0.55f + 0.35f * (float) Math.abs(Math.sin(t) * Math.sin(t * 0.37 + 1.3));
        }

        if (Vhs.has(FILM)) {
            Vhs.blitAlpha(g, FILM, x, y, WIDTH, HEIGHT, 0, 0, 128, 24, 128, 48, 1f);
            int lostW = Math.round(WIDTH * lost);
            int fillW = Math.round(WIDTH * shown);
            if (lostAlpha > 0f && lostW > fillW) {
                tinted(g, x + fillW, y, lostW - fillW, fillW, Vhs.DRIED_BLOOD, 0.85f * lostAlpha);
            }
            if (fillW > 0) tinted(g, x, y, fillW, 0, rgb, alpha);
        } else {
            g.fill(x, y, x + WIDTH, y + HEIGHT, 0xB01C1A17);
            int fillW = Math.round(WIDTH * shown);
            g.fill(x, y + BAND_TOP, x + fillW, y + BAND_TOP + BAND_H, ((int) (alpha * 230) << 24) | rgb);
        }
    }

    /** Кусок засвеченной полосы шириной w с места u (в пикселях экрана), окрашенный в rgb. */
    private static void tinted(GuiGraphics g, int x, int y, int w, int u, int rgb, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        g.blit(FILM, x, y, w, HEIGHT, u * 2f, 24f, w * 2, 24, 128, 48);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int gr = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (gr << 8) | bl;
    }

    /** «ПЕРЕВЯЗКА» под прицелом и полоска хода, пока держишь ПКМ с бинтом. Отпустил: перевязка отменена. */
    @SubscribeEvent
    public static void onBandagingRender(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        if (!mc.player.isUsingItem() || !(mc.player.getUseItem().getItem() instanceof BandageItem)) return;
        float p = BandageClient.progress(mc.player, e.getPartialTick());
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth();
        int h = e.getWindow().getGuiScaledHeight();
        int cx = w / 2;
        int y = h / 2 + 16;
        Vhs.ghostCentered(g, Vhs.title(Vhs.tr("bh.hud.bandaging")), cx, y, 0xD0000000 | Vhs.BONE, 1f, 0.15f);
        int bw = 70;
        int bx = cx - bw / 2;
        int by = y + 12;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 3, 0x90000000 | 0x1C1A17);
        g.fill(bx, by, bx + Math.round(bw * p), by + 2, 0xD0000000 | Vhs.BONE);
        // Тусклый двойник полоски, как у надписей: плёнка чуть «плывёт».
        float gx = (float) Math.sin(System.currentTimeMillis() / 900.0) * 1.2f + 1f;
        g.fill(bx + Math.round(gx), by + 1, bx + Math.round(gx) + Math.round(bw * p), by + 3, 0x30000000 | Vhs.BONE);
    }
}
